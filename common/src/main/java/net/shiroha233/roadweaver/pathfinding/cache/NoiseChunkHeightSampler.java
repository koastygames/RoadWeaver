package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 基于原版 NoiseChunk 批量烘焙整块高度图的精确采样器
 */
final class NoiseChunkHeightSampler {
    private static final ZeroBeardifier ZERO_BEARDIFIER = ZeroBeardifier.INSTANCE;

    private static final int CHUNK_CACHE_CAPACITY = 256;

    private final RandomState randomState;
    private final NoiseGeneratorSettings generatorSettings;
    private final NoiseSettings noiseSettings;
    private final Aquifer.FluidPicker fluidPicker;
    private final BlockState defaultBlock;
    private final Map<Long, ChunkHeightData> chunkCache = Collections.synchronizedMap(
            new LinkedHashMap<Long, ChunkHeightData>(CHUNK_CACHE_CAPACITY, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, ChunkHeightData> eldest) {
                    return size() > CHUNK_CACHE_CAPACITY;
                }
            });

    private NoiseChunkHeightSampler(RandomState randomState,
                                    NoiseGeneratorSettings generatorSettings,
                                    NoiseSettings noiseSettings,
                                    Aquifer.FluidPicker fluidPicker) {
        this.randomState = randomState;
        this.generatorSettings = generatorSettings;
        this.noiseSettings = noiseSettings;
        this.fluidPicker = fluidPicker;
        this.defaultBlock = generatorSettings.defaultBlock();
    }

    static NoiseChunkHeightSampler create(ServerLevel level, NoiseBasedChunkGenerator generator, RandomState randomState) {
        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        NoiseSettings noiseSettings = settings.noiseSettings().clampToHeightAccessor(level);
        return new NoiseChunkHeightSampler(randomState, settings, noiseSettings, createFluidPicker(settings));
    }

    int motionBlockingNoLeaves(int x, int z) {
        return chunkData(x, z).motionBlockingNoLeaves(localIndex(x, z));
    }

    int worldSurfaceWg(int x, int z) {
        return chunkData(x, z).worldSurfaceWg(localIndex(x, z));
    }

    int oceanFloorWg(int x, int z) {
        return chunkData(x, z).oceanFloorWg(localIndex(x, z));
    }

    /**
     * 释放缓存。用新实例替换而非 clear()，确保内部 table 数组被 GC 回收。
     */
    void clear() {
        chunkCache.clear();
        // LinkedHashMap LRU 容量固定256，不会过度膨胀，clear后table适中无需替换
    }

    private ChunkHeightData chunkData(int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long key = chunkKey(chunkX, chunkZ);
        return chunkCache.computeIfAbsent(key, ignored -> bakeChunk(chunkX, chunkZ));
    }

    private ChunkHeightData bakeChunk(int chunkX, int chunkZ) {
        int minY = noiseSettings.minY();
        int[] worldSurface = new int[256];
        int[] oceanFloor = new int[256];
        int[] motionBlocking = new int[256];
        Arrays.fill(worldSurface, minY);
        Arrays.fill(oceanFloor, minY);
        Arrays.fill(motionBlocking, minY);

        byte[] unresolvedMasks = new byte[256];
        Arrays.fill(unresolvedMasks, (byte) 0x07);
        int unresolvedColumns = unresolvedMasks.length;

        Predicate<BlockState> worldSurfacePredicate = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
        Predicate<BlockState> oceanFloorPredicate = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
        Predicate<BlockState> motionPredicate = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES.isOpaque();

        int cellWidth = noiseSettings.getCellWidth();
        int cellHeight = noiseSettings.getCellHeight();
        int cellCountXZ = 16 / cellWidth;
        int cellCountY = Mth.floorDiv(noiseSettings.height(), cellHeight);
        int cellNoiseMinY = Mth.floorDiv(minY, cellHeight);

        int minBlockX = chunkX << 4;
        int minBlockZ = chunkZ << 4;

        AccessibleNoiseChunk noiseChunk = new AccessibleNoiseChunk(
                cellCountXZ,
                randomState,
                minBlockX,
                minBlockZ,
                noiseSettings,
                ZERO_BEARDIFIER,
                generatorSettings,
                fluidPicker);

        noiseChunk.initializeForFirstCellX();
        try {
            outer:
            for (int cellX = 0; cellX < cellCountXZ; cellX++) {
                noiseChunk.advanceCellX(cellX);

                for (int cellZ = 0; cellZ < cellCountXZ; cellZ++) {
                    for (int cellY = cellCountY - 1; cellY >= 0; cellY--) {
                        noiseChunk.selectCellYZ(cellY, cellZ);

                        for (int inCellY = cellHeight - 1; inCellY >= 0; inCellY--) {
                            int blockY = (cellNoiseMinY + cellY) * cellHeight + inCellY;
                            noiseChunk.updateForY(blockY, (double) inCellY / cellHeight);

                            for (int inCellX = 0; inCellX < cellWidth; inCellX++) {
                                int blockX = minBlockX + cellX * cellWidth + inCellX;
                                int localX = blockX & 15;
                                noiseChunk.updateForX(blockX, (double) inCellX / cellWidth);

                                for (int inCellZ = 0; inCellZ < cellWidth; inCellZ++) {
                                    int blockZ = minBlockZ + cellZ * cellWidth + inCellZ;
                                    noiseChunk.updateForZ(blockZ, (double) inCellZ / cellWidth);

                                    int index = localX + ((blockZ & 15) << 4);
                                    byte mask = unresolvedMasks[index];
                                    if (mask == 0) {
                                        continue;
                                    }

                                    BlockState state = noiseChunk.sampleInterpolatedState();
                                    if (state == null) {
                                        state = defaultBlock;
                                    }
                                    if (state.isAir()) {
                                        continue;
                                    }

                                    byte nextMask = mask;
                                    if ((nextMask & 0x01) != 0 && worldSurfacePredicate.test(state)) {
                                        worldSurface[index] = blockY + 1;
                                        nextMask &= ~0x01;
                                    }
                                    if ((nextMask & 0x02) != 0 && oceanFloorPredicate.test(state)) {
                                        oceanFloor[index] = blockY + 1;
                                        nextMask &= ~0x02;
                                    }
                                    if ((nextMask & 0x04) != 0 && motionPredicate.test(state)) {
                                        motionBlocking[index] = blockY + 1;
                                        nextMask &= ~0x04;
                                    }

                                    if (nextMask != mask) {
                                        unresolvedMasks[index] = nextMask;
                                        if (nextMask == 0) {
                                            unresolvedColumns--;
                                            if (unresolvedColumns == 0) {
                                                break outer;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                noiseChunk.swapSlices();
            }
        } finally {
            noiseChunk.stopInterpolation();
        }

        return new ChunkHeightData(worldSurface, oceanFloor, motionBlocking);
    }

    private static int localIndex(int x, int z) {
        return (x & 15) + ((z & 15) << 4);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus defaultFluid = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        return (x, y, z) -> y < Math.min(-54, seaLevel) ? lava : defaultFluid;
    }

    static record ChunkHeightData(int[] worldSurfaceWg, int[] oceanFloorWg, int[] motionBlockingNoLeaves) {
        int worldSurfaceWg(int index) {
            return worldSurfaceWg[index];
        }

        int oceanFloorWg(int index) {
            return oceanFloorWg[index];
        }

        int motionBlockingNoLeaves(int index) {
            return motionBlockingNoLeaves[index];
        }
    }

    private static final class AccessibleNoiseChunk extends NoiseChunk {
        private AccessibleNoiseChunk(int cellCountXZ,
                                     RandomState randomState,
                                     int minBlockX,
                                     int minBlockZ,
                                     NoiseSettings noiseSettings,
                                     DensityFunctions.BeardifierOrMarker beardifier,
                                     NoiseGeneratorSettings generatorSettings,
                                     Aquifer.FluidPicker fluidPicker) {
            super(cellCountXZ, randomState, minBlockX, minBlockZ, noiseSettings, beardifier, generatorSettings, fluidPicker, Blender.empty());
        }

        private BlockState sampleInterpolatedState() {
            return this.getInterpolatedState();
        }
    }

    private enum ZeroBeardifier implements DensityFunctions.BeardifierOrMarker {
        INSTANCE;

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            return 0.0D;
        }

        @Override
        public void fillArray(double[] values, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(values, 0.0D);
        }

        @Override
        public double minValue() {
            return 0.0D;
        }

        @Override
        public double maxValue() {
            return 0.0D;
        }
    }
}
