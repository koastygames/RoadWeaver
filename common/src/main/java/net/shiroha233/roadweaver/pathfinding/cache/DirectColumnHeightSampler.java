package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.commons.lang3.mutable.MutableDouble;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 基于噪声列的轻量精确采样，避免首次新区块整块烘焙。
 */
final class DirectColumnHeightSampler {
    private static final double PRELIMINARY_THRESHOLD = 0.390625D;
    private static final int COLUMN_CACHE_LIMIT = 1 << 18;
    private static final int PRELIMINARY_CACHE_LIMIT = 1 << 18;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final int minY;
    private final int maxYExclusive;
    private final int maxBlockY;
    private final int cellHeight;
    private final BlockState defaultBlock;

    private final DensityFunction initialDensity;
    private final DensityFunction finalDensity;
    private final Predicate<BlockState> worldSurfacePredicate = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
    private final Predicate<BlockState> oceanFloorPredicate = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
    private final Predicate<BlockState> motionPredicate = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.isOpaque();

    private final ConcurrentHashMap<Long, ColumnHeightData> columnCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> preliminaryCache = new ConcurrentHashMap<>();
    private final ThreadLocal<MutableContext> localContext = ThreadLocal.withInitial(MutableContext::new);
    private final MiniAquifer aquifer;

    private DirectColumnHeightSampler(NoiseSettings noiseSettings,
                                      NoiseGeneratorSettings generatorSettings,
                                      NoiseRouter router,
                                      PositionalRandomFactory aquiferRandom) {
        this.minY = noiseSettings.minY();
        this.maxYExclusive = minY + noiseSettings.height();
        this.maxBlockY = maxYExclusive - 1;
        this.cellHeight = noiseSettings.getCellHeight();
        this.defaultBlock = generatorSettings.defaultBlock();
        this.initialDensity = router.initialDensityWithoutJaggedness();
        this.finalDensity = router.finalDensity();
        this.aquifer = new MiniAquifer(
                aquiferRandom,
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.fluidLevelSpreadNoise(),
                router.lavaNoise(),
                router.erosion(),
                router.depth(),
                generatorSettings.seaLevel(),
                generatorSettings.defaultFluid());
    }

    static DirectColumnHeightSampler create(ServerLevel level, NoiseBasedChunkGenerator generator, RandomState randomState) {
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        NoiseSettings noiseSettings = settings.noiseSettings().clampToHeightAccessor(level);
        NoiseRouter router = randomState.router();
        return new DirectColumnHeightSampler(
                noiseSettings,
                settings,
                router,
                randomState.aquiferRandom());
    }

    int worldSurfaceWg(int x, int z) {
        return columnData(x, z).worldSurfaceWg();
    }

    int oceanFloorWg(int x, int z) {
        return columnData(x, z).oceanFloorWg();
    }

    int motionBlockingNoLeaves(int x, int z) {
        return columnData(x, z).motionBlockingNoLeaves();
    }

    void clear() {
        columnCache.clear();
        preliminaryCache.clear();
        aquifer.clear();
    }

    private ColumnHeightData columnData(int x, int z) {
        long key = hashXZ(x, z);
        ColumnHeightData cached = columnCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (columnCache.size() > COLUMN_CACHE_LIMIT) {
            columnCache.clear();
        }
        ColumnHeightData computed = sampleColumn(x, z);
        ColumnHeightData raced = columnCache.putIfAbsent(key, computed);
        return raced != null ? raced : computed;
    }

    private ColumnHeightData sampleColumn(int x, int z) {
        int worldSurface = minY;
        int oceanFloor = minY;
        int motion = minY;
        boolean needWorld = true;
        boolean needOcean = true;
        boolean needMotion = true;

        int startY = scanStartY(x, z);
        for (int y = startY; y >= minY && (needWorld || needOcean || needMotion); y--) {
            BlockState state = sampleState(x, y, z);
            if (state.isAir()) {
                continue;
            }
            if (needWorld && worldSurfacePredicate.test(state)) {
                worldSurface = y + 1;
                needWorld = false;
            }
            if (needOcean && oceanFloorPredicate.test(state)) {
                oceanFloor = y + 1;
                needOcean = false;
            }
            if (needMotion && motionPredicate.test(state)) {
                motion = y + 1;
                needMotion = false;
            }
        }

        return new ColumnHeightData(worldSurface, oceanFloor, motion);
    }

    private int scanStartY(int x, int z) {
        int prelim = preliminarySurfaceLevel(x, z);
        int startY = prelim == Integer.MAX_VALUE
                ? maxBlockY
                : Mth.clamp(prelim + (cellHeight << 1), minY, maxBlockY);
        if (startY >= maxBlockY || startY > maxBlockY - (cellHeight << 1)) {
            return startY;
        }
        int topSolidProbe = highestSolidProbeY(x, z, startY + 1);
        if (topSolidProbe == Integer.MIN_VALUE) {
            return startY;
        }
        return Mth.clamp(topSolidProbe + cellHeight - 1, startY, maxBlockY);
    }

    private int highestSolidProbeY(int x, int z, int minProbeY) {
        for (int y = maxBlockY; y >= minProbeY; y -= cellHeight) {
            if (sampleDensity(finalDensity, x, y, z) > 0.0D) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private BlockState sampleState(int x, int y, int z) {
        double density = sampleDensity(finalDensity, x, y, z);
        if (density > 0.0D) {
            return defaultBlock;
        }
        BlockState fluid = aquifer.computeSubstance(x, y, z, density);
        return fluid == null ? AIR : fluid;
    }

    int preliminarySurfaceLevel(int x, int z) {
        int alignedX = QuartPos.toBlock(QuartPos.fromBlock(x));
        int alignedZ = QuartPos.toBlock(QuartPos.fromBlock(z));
        long key = hashXZ(alignedX, alignedZ);
        Integer cached = preliminaryCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (preliminaryCache.size() > PRELIMINARY_CACHE_LIMIT) {
            preliminaryCache.clear();
        }
        int computed = computePreliminarySurfaceLevel(alignedX, alignedZ);
        Integer raced = preliminaryCache.putIfAbsent(key, computed);
        return raced != null ? raced : computed;
    }

    private int computePreliminarySurfaceLevel(int x, int z) {
        for (int y = maxYExclusive; y >= minY; y -= cellHeight) {
            if (sampleDensity(initialDensity, x, y, z) > PRELIMINARY_THRESHOLD) {
                return y;
            }
        }
        return Integer.MAX_VALUE;
    }

    private double sampleDensity(DensityFunction function, int x, int y, int z) {
        MutableContext context = localContext.get();
        context.set(x, y, z);
        return function.compute(context);
    }

    private static long hashXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static long hashXYZ(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    private record ColumnHeightData(int worldSurfaceWg, int oceanFloorWg, int motionBlockingNoLeaves) {}

    private static final class MutableContext implements DensityFunction.FunctionContext {
        private int x;
        private int y;
        private int z;

        void set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int blockX() {
            return x;
        }

        @Override
        public int blockY() {
            return y;
        }

        @Override
        public int blockZ() {
            return z;
        }
    }

    private final class MiniAquifer {
        private static final int AQUIFER_CACHE_LIMIT = 1 << 19;
        private static final int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS = {
                {0, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1},
                {-3, 0}, {-2, 0}, {-1, 0}, {1, 0},
                {-2, 1}, {-1, 1}, {0, 1}, {1, 1}
        };
        private final PositionalRandomFactory positionalRandomFactory;
        private final DensityFunction barrierNoise;
        private final DensityFunction fluidLevelFloodednessNoise;
        private final DensityFunction fluidLevelSpreadNoise;
        private final DensityFunction lavaNoise;
        private final DensityFunction erosion;
        private final DensityFunction depth;
        private final int minGlobalFluidY;
        private final FluidStatus lavaFluidStatus;
        private final FluidStatus defaultFluidStatus;

        private final ConcurrentHashMap<Long, FluidStatus> aquiferStatusCache = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Long, Long> aquiferLocationCache = new ConcurrentHashMap<>();
        private final ThreadLocal<MutableDouble> pressureScratch = ThreadLocal.withInitial(() -> new MutableDouble(Double.NaN));

        private MiniAquifer(PositionalRandomFactory positionalRandomFactory,
                            DensityFunction barrierNoise,
                            DensityFunction fluidLevelFloodednessNoise,
                            DensityFunction fluidLevelSpreadNoise,
                            DensityFunction lavaNoise,
                            DensityFunction erosion,
                            DensityFunction depth,
                            int seaLevel,
                            BlockState defaultFluid) {
            this.positionalRandomFactory = positionalRandomFactory;
            this.barrierNoise = barrierNoise;
            this.fluidLevelFloodednessNoise = fluidLevelFloodednessNoise;
            this.fluidLevelSpreadNoise = fluidLevelSpreadNoise;
            this.lavaNoise = lavaNoise;
            this.erosion = erosion;
            this.depth = depth;
            this.minGlobalFluidY = Math.min(-54, seaLevel);
            this.lavaFluidStatus = new FluidStatus(-54, Blocks.LAVA.defaultBlockState());
            this.defaultFluidStatus = new FluidStatus(seaLevel, defaultFluid);
        }

        void clear() {
            aquiferStatusCache.clear();
            aquiferLocationCache.clear();
        }

        private BlockState computeSubstance(int x, int y, int z, double density) {
            if (density > 0.0D) {
                return null;
            }

            FluidStatus globalStatus = globalFluidStatus(y);
            if (globalStatus.at(y).is(Blocks.LAVA)) {
                return Blocks.LAVA.defaultBlockState();
            }

            int gridX = Math.floorDiv(x - 5, 16);
            int gridY = Math.floorDiv(y + 1, 12);
            int gridZ = Math.floorDiv(z - 5, 16);
            int nearest = Integer.MAX_VALUE;
            int second = Integer.MAX_VALUE;
            int third = Integer.MAX_VALUE;
            long nearestPos = 0L;
            long secondPos = 0L;
            long thirdPos = 0L;

            for (int dx = 0; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        int cx = gridX + dx;
                        int cy = gridY + dy;
                        int cz = gridZ + dz;
                        long aquiferCenter = getOrCreateAquiferCenter(cx, cy, cz);

                        int dx2 = BlockPos.getX(aquiferCenter) - x;
                        int dy2 = BlockPos.getY(aquiferCenter) - y;
                        int dz2 = BlockPos.getZ(aquiferCenter) - z;
                        int distanceSq = dx2 * dx2 + dy2 * dy2 + dz2 * dz2;
                        if (nearest >= distanceSq) {
                            thirdPos = secondPos;
                            secondPos = nearestPos;
                            nearestPos = aquiferCenter;
                            third = second;
                            second = nearest;
                            nearest = distanceSq;
                        } else if (second >= distanceSq) {
                            thirdPos = secondPos;
                            secondPos = aquiferCenter;
                            third = second;
                            second = distanceSq;
                        } else if (third >= distanceSq) {
                            thirdPos = aquiferCenter;
                            third = distanceSq;
                        }
                    }
                }
            }

            FluidStatus s1 = getAquiferStatus(nearestPos);
            double d1 = similarity(nearest, second);
            BlockState state = s1.at(y);
            if (d1 <= 0.0D) {
                return state;
            }

            if (state.is(Blocks.WATER) && globalFluidStatus(y - 1).at(y - 1).is(Blocks.LAVA)) {
                return state;
            }

            MutableDouble barrier = pressureScratch.get();
            barrier.setValue(Double.NaN);

            FluidStatus s2 = getAquiferStatus(secondPos);
            double pressure12 = d1 * calculatePressure(x, y, z, barrier, s1, s2);
            if (density + pressure12 > 0.0D) {
                return null;
            }

            FluidStatus s3 = getAquiferStatus(thirdPos);
            double d0 = similarity(nearest, third);
            if (d0 > 0.0D) {
                double pressure13 = d1 * d0 * calculatePressure(x, y, z, barrier, s1, s3);
                if (density + pressure13 > 0.0D) {
                    return null;
                }
            }

            double d4 = similarity(second, third);
            if (d4 > 0.0D) {
                double pressure23 = d1 * d4 * calculatePressure(x, y, z, barrier, s2, s3);
                if (density + pressure23 > 0.0D) {
                    return null;
                }
            }

            return state;
        }

        private long getOrCreateAquiferCenter(int gridX, int gridY, int gridZ) {
            long key = hashXYZ(gridX, gridY, gridZ);
            Long cached = aquiferLocationCache.get(key);
            if (cached != null) {
                return cached;
            }
            if (aquiferLocationCache.size() > AQUIFER_CACHE_LIMIT) {
                aquiferLocationCache.clear();
            }
            RandomSource random = positionalRandomFactory.at(gridX, gridY, gridZ);
            long created = BlockPos.asLong(
                    gridX * 16 + random.nextInt(10),
                    gridY * 12 + random.nextInt(9),
                    gridZ * 16 + random.nextInt(10));
            Long raced = aquiferLocationCache.putIfAbsent(key, created);
            return raced != null ? raced : created;
        }

        private FluidStatus getAquiferStatus(long packedPosition) {
            int x = BlockPos.getX(packedPosition);
            int y = BlockPos.getY(packedPosition);
            int z = BlockPos.getZ(packedPosition);
            int gridX = Math.floorDiv(x, 16);
            int gridY = Math.floorDiv(y, 12);
            int gridZ = Math.floorDiv(z, 16);
            long key = hashXYZ(gridX, gridY, gridZ);
            FluidStatus cached = aquiferStatusCache.get(key);
            if (cached != null) {
                return cached;
            }
            if (aquiferStatusCache.size() > AQUIFER_CACHE_LIMIT) {
                aquiferStatusCache.clear();
            }
            FluidStatus computed = computeFluid(x, y, z);
            FluidStatus raced = aquiferStatusCache.putIfAbsent(key, computed);
            return raced != null ? raced : computed;
        }

        private FluidStatus computeFluid(int x, int y, int z) {
            FluidStatus globalStatus = globalFluidStatus(y);
            int minSurface = Integer.MAX_VALUE;
            int top = y + 12;
            int bottom = y - 12;
            boolean centerTouchesFluid = false;

            for (int[] offset : SURFACE_SAMPLING_OFFSETS_IN_CHUNKS) {
                int sx = x + SectionPos.sectionToBlockCoord(offset[0]);
                int sz = z + SectionPos.sectionToBlockCoord(offset[1]);
                int surface = preliminarySurfaceLevel(sx, sz);
                int probeY = surface + 8;
                boolean center = offset[0] == 0 && offset[1] == 0;
                if (center && bottom > probeY) {
                    return globalStatus;
                }

                boolean aboveProbe = top > probeY;
                if (aboveProbe || center) {
                    FluidStatus neighbor = globalFluidStatus(probeY);
                    if (!neighbor.at(probeY).isAir()) {
                        if (center) {
                            centerTouchesFluid = true;
                        }
                        if (aboveProbe) {
                            return neighbor;
                        }
                    }
                }

                minSurface = Math.min(minSurface, surface);
            }

            int level = computeSurfaceLevel(x, y, z, globalStatus, minSurface, centerTouchesFluid);
            return new FluidStatus(level, computeFluidType(x, y, z, globalStatus, level));
        }

        private int computeSurfaceLevel(int x, int y, int z,
                                        FluidStatus globalStatus,
                                        int minSurface,
                                        boolean centerTouchesFluid) {
            MutableContext context = localContext.get();
            context.set(x, y, z);
            if (OverworldBiomeBuilder.isDeepDarkRegion(erosion, depth, context)) {
                return DimensionType.WAY_BELOW_MIN_Y;
            }

            int distance = minSurface + 8 - y;
            double floodednessBase = centerTouchesFluid
                    ? Mth.clampedMap((double) distance, 0.0D, 64.0D, 1.0D, 0.0D)
                    : 0.0D;
            double floodednessNoise = Mth.clamp(sampleDensity(fluidLevelFloodednessNoise, x, y, z), -1.0D, 1.0D);
            double high = Mth.map(floodednessBase, 1.0D, 0.0D, -0.3D, 0.8D);
            double low = Mth.map(floodednessBase, 1.0D, 0.0D, -0.8D, 0.4D);
            double d0 = floodednessNoise - low;
            double d1 = floodednessNoise - high;

            if (d1 > 0.0D) {
                return globalStatus.fluidLevel();
            }
            if (d0 > 0.0D) {
                return computeRandomizedFluidSurfaceLevel(x, y, z, minSurface);
            }
            return DimensionType.WAY_BELOW_MIN_Y;
        }

        private int computeRandomizedFluidSurfaceLevel(int x, int y, int z, int minSurface) {
            int gx = Math.floorDiv(x, 16);
            int gy = Math.floorDiv(y, 40);
            int gz = Math.floorDiv(z, 16);
            int base = gy * 40 + 20;
            double spread = sampleDensity(fluidLevelSpreadNoise, gx, gy, gz) * 10.0D;
            int quantized = Mth.quantize(spread, 3);
            return Math.min(minSurface, base + quantized);
        }

        private BlockState computeFluidType(int x, int y, int z,
                                            FluidStatus globalStatus,
                                            int fluidLevel) {
            BlockState fluid = globalStatus.fluidType();
            if (fluidLevel <= -10
                    && fluidLevel != DimensionType.WAY_BELOW_MIN_Y
                    && !globalStatus.fluidType().is(Blocks.LAVA)) {
                int gx = Math.floorDiv(x, 64);
                int gy = Math.floorDiv(y, 40);
                int gz = Math.floorDiv(z, 64);
                double lava = sampleDensity(lavaNoise, gx, gy, gz);
                if (Math.abs(lava) > 0.3D) {
                    fluid = Blocks.LAVA.defaultBlockState();
                }
            }
            return fluid;
        }

        private double calculatePressure(int x, int y, int z,
                                         MutableDouble barrierCache,
                                         FluidStatus a,
                                         FluidStatus b) {
            BlockState aState = a.at(y);
            BlockState bState = b.at(y);
            boolean mixedLavaWater = (aState.is(Blocks.LAVA) && bState.is(Blocks.WATER))
                    || (aState.is(Blocks.WATER) && bState.is(Blocks.LAVA));
            if (mixedLavaWater) {
                return 2.0D;
            }

            int delta = Math.abs(a.fluidLevel() - b.fluidLevel());
            if (delta == 0) {
                return 0.0D;
            }

            double center = 0.5D * (a.fluidLevel() + b.fluidLevel());
            double yOffset = (double) y + 0.5D - center;
            double halfDelta = delta / 2.0D;
            double band = halfDelta - Math.abs(yOffset);
            double pressure;
            if (yOffset > 0.0D) {
                double v = band;
                pressure = v > 0.0D ? v / 1.5D : v / 2.5D;
            } else {
                double v = 3.0D + band;
                pressure = v > 0.0D ? v / 3.0D : v / 10.0D;
            }

            double barrier;
            if (pressure < -2.0D || pressure > 2.0D) {
                barrier = 0.0D;
            } else {
                double cached = barrierCache.getValue();
                if (Double.isNaN(cached)) {
                    cached = sampleDensity(barrierNoise, x, y, z);
                    barrierCache.setValue(cached);
                }
                barrier = cached;
            }
            return 2.0D * (barrier + pressure);
        }

        private static double similarity(int a, int b) {
            return 1.0D - (double) Math.abs(b - a) / 25.0D;
        }

        private FluidStatus globalFluidStatus(int y) {
            return y < minGlobalFluidY ? lavaFluidStatus : defaultFluidStatus;
        }

        private record FluidStatus(int fluidLevel, BlockState fluidType) {
            private BlockState at(int y) {
                return y < fluidLevel ? fluidType : AIR;
            }
        }
    }
}
