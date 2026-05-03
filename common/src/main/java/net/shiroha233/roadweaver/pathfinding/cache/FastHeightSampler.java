package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于预估地表高度的快速采样器
 */
public final class FastHeightSampler {

    private static final double LEGACY_DENSITY_THRESHOLD = 0.390625D;
    private static final double PRELIMINARY_CHEESE_OFFSET = -0.703125D;
    private static final double PRELIMINARY_SEARCH_OFFSET = -0.390625D;
    private static final int SEARCH_LOWER_BOUND = -64;
    private static final int SEARCH_UPPER_CLAMP_MIN = -40;

    private static final ResourceKey<DensityFunction> OVERWORLD_OFFSET =
            densityKey("overworld/offset");
    private static final ResourceKey<DensityFunction> OVERWORLD_FACTOR =
            densityKey("overworld/factor");
    private static final ResourceKey<DensityFunction> OVERWORLD_OFFSET_LARGE =
            densityKey("overworld_large_biomes/offset");
    private static final ResourceKey<DensityFunction> OVERWORLD_FACTOR_LARGE =
            densityKey("overworld_large_biomes/factor");
    private static final ResourceKey<DensityFunction> OVERWORLD_OFFSET_AMPLIFIED =
            densityKey("overworld_amplified/offset");
    private static final ResourceKey<DensityFunction> OVERWORLD_FACTOR_AMPLIFIED =
            densityKey("overworld_amplified/factor");

    private final DensityFunction fallbackDensity;
    private final DensityFunction preliminaryDensity;
    private final DensityFunction upperBoundFactor;
    private final DensityFunction upperBoundOffset;
    private final int minY;
    private final int maxY;
    private final int cellHeight;
    private final boolean hasPreliminaryEstimator;
    private final ConcurrentHashMap<Long, Integer> heightCache = new ConcurrentHashMap<>();

    private FastHeightSampler(DensityFunction fallbackDensity,
                              DensityFunction preliminaryDensity,
                              DensityFunction upperBoundFactor,
                              DensityFunction upperBoundOffset,
                              NoiseSettings settings,
                              boolean hasPreliminaryEstimator) {
        this.fallbackDensity = fallbackDensity;
        this.preliminaryDensity = preliminaryDensity;
        this.upperBoundFactor = upperBoundFactor;
        this.upperBoundOffset = upperBoundOffset;
        this.minY = settings.minY();
        this.maxY = minY + settings.height();
        this.cellHeight = settings.getCellHeight();
        this.hasPreliminaryEstimator = hasPreliminaryEstimator;
    }

    public static FastHeightSampler create(ServerLevel level) {
        var chunkSource = level.getChunkSource();
        RandomState randomState = chunkSource.getGeneratorState().randomState();
        NoiseRouter router = randomState.router();
        NoiseSettings settings = getNoiseSettings(level);
        PreliminaryEstimator estimator = createPreliminaryEstimator(level, randomState);
        return new FastHeightSampler(
                router.initialDensityWithoutJaggedness(),
                estimator.preliminaryDensity,
                estimator.upperBoundFactor,
                estimator.upperBoundOffset,
                settings,
                estimator.available
        );
    }

    public int sampleHeight(int x, int z) {
        int alignedX = (x >> 2) << 2;
        int alignedZ = (z >> 2) << 2;
        long key = packXZ(alignedX, alignedZ);
        Integer cached = heightCache.get(key);
        if (cached != null) return cached;

        int height = computeHeight(alignedX, alignedZ);
        heightCache.put(key, height);
        return height;
    }

    public void prewarmRegion(int minX, int minZ, int maxX, int maxZ, int step) {
        for (int x = minX; x <= maxX; x += step) {
            for (int z = minZ; z <= maxZ; z += step) {
                sampleHeight((x >> 2) << 2, (z >> 2) << 2);
            }
        }
    }

    private int computeHeight(int x, int z) {
        if (!hasPreliminaryEstimator) {
            return computeLegacyHeight(x, z);
        }

        int topY = floorToCell(computeUpperBound(x, z));
        if (topY <= SEARCH_LOWER_BOUND) {
            return clampToWorld(SEARCH_LOWER_BOUND);
        }

        int searchTop = Math.min(maxY, topY);
        int searchBottom = Math.max(minY, SEARCH_LOWER_BOUND);
        for (int y = searchTop; y >= searchBottom; y -= cellHeight) {
            double density = preliminaryDensity.compute(new DensityFunction.SinglePointContext(x, y, z));
            if (density > 0.0D) {
                return clampToWorld(y);
            }
        }
        return clampToWorld(searchBottom);
    }

    private int computeLegacyHeight(int x, int z) {
        for (int y = maxY; y >= minY; y -= cellHeight) {
            double density = fallbackDensity.compute(new DensityFunction.SinglePointContext(x, y, z));
            if (density > LEGACY_DENSITY_THRESHOLD) {
                return y;
            }
        }
        return minY;
    }

    private int computeUpperBound(int x, int z) {
        DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext(x, 0, z);
        double factor = upperBoundFactor.compute(context);
        double offset = upperBoundOffset.compute(context);
        double inverseFactor = safeInvert(factor);
        double raw = remap(0.2734375D * inverseFactor - offset, 1.5D, -1.5D, -64.0D, 320.0D);
        double clamped = Math.max(SEARCH_UPPER_CLAMP_MIN, Math.min(maxY, raw));
        return (int) Math.floor(clamped);
    }

    private int clampToWorld(int y) {
        if (y < minY) return minY;
        if (y > maxY) return maxY;
        return y;
    }

    private int floorToCell(int y) {
        return Math.floorDiv(y, cellHeight) * cellHeight;
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static PreliminaryEstimator createPreliminaryEstimator(ServerLevel level, RandomState randomState) {
        try {
            var densityRegistry = level.registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION);
            DensityFunction offset = DensityFunctions.cache2d(
                    wireDensityFunction(randomState, densityRegistry.getOrThrow(selectOffsetKey(level)))
            );
            DensityFunction factor = DensityFunctions.cache2d(
                    wireDensityFunction(randomState, densityRegistry.getOrThrow(selectFactorKey(level)))
            );
            DensityFunction preliminaryDensity = DensityFunctions.add(
                    slideOverworld(
                            isAmplified(level),
                            DensityFunctions.add(
                                    noiseGradientDensity(factor, offsetToDepth(offset)),
                                    DensityFunctions.constant(PRELIMINARY_CHEESE_OFFSET)
                            ).clamp(-64.0D, 64.0D)
                    ),
                    DensityFunctions.constant(PRELIMINARY_SEARCH_OFFSET)
            );
            return new PreliminaryEstimator(preliminaryDensity, factor, offset, true);
        } catch (Exception ignored) {
            return new PreliminaryEstimator(null, null, null, false);
        }
    }

    private static DensityFunction wireDensityFunction(RandomState randomState, DensityFunction function) {
        DensityFunction.Visitor visitor = new DensityFunction.Visitor() {
            private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noiseHolder) {
                if (noiseHolder.noise() != null) {
                    return noiseHolder;
                }
                ResourceKey<NormalNoise.NoiseParameters> key = noiseHolder.noiseData().unwrapKey().orElseThrow();
                return new DensityFunction.NoiseHolder(noiseHolder.noiseData(), randomState.getOrCreateNoise(key));
            }

            @Override
            public DensityFunction apply(DensityFunction densityFunction) {
                return wrapped.computeIfAbsent(densityFunction, ignored -> densityFunction);
            }
        };
        return function.mapAll(visitor);
    }

    private static DensityFunction offsetToDepth(DensityFunction offset) {
        return DensityFunctions.add(DensityFunctions.yClampedGradient(-64, 320, 1.5D, -1.5D), offset);
    }

    private static DensityFunction noiseGradientDensity(DensityFunction factor, DensityFunction depthWithJaggedness) {
        DensityFunction gradientUnscaled = DensityFunctions.mul(depthWithJaggedness, factor);
        return DensityFunctions.mul(DensityFunctions.constant(4.0D), gradientUnscaled.quarterNegative());
    }

    private static DensityFunction slideOverworld(boolean amplified, DensityFunction caves) {
        return slide(caves, -64, 384, amplified ? 16 : 80, amplified ? 0 : 64, -0.078125D, 0, 24, amplified ? 0.4D : 0.1171875D);
    }

    private static DensityFunction slide(DensityFunction caves,
                                         int minY,
                                         int height,
                                         int topStartY,
                                         int topEndY,
                                         double topTarget,
                                         int bottomStartY,
                                         int bottomEndY,
                                         double bottomTarget) {
        DensityFunction topFactor = DensityFunctions.yClampedGradient(minY + height - topStartY, minY + height - topEndY, 1.0D, 0.0D);
        DensityFunction topLerped = DensityFunctions.lerp(topFactor, topTarget, caves);
        DensityFunction bottomFactor = DensityFunctions.yClampedGradient(minY + bottomStartY, minY + bottomEndY, 0.0D, 1.0D);
        return DensityFunctions.lerp(bottomFactor, bottomTarget, topLerped);
    }

    private static double remap(double input, double fromMin, double fromMax, double toMin, double toMax) {
        double factor = (toMax - toMin) / (fromMax - fromMin);
        double offset = toMin - fromMin * factor;
        return input * factor + offset;
    }

    private static double safeInvert(double value) {
        if (Math.abs(value) < 1.0E-9D) {
            return value >= 0.0D ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }
        return 1.0D / value;
    }

    private static ResourceKey<DensityFunction> selectOffsetKey(ServerLevel level) {
        if (isAmplified(level)) return OVERWORLD_OFFSET_AMPLIFIED;
        if (isLargeBiomes(level)) return OVERWORLD_OFFSET_LARGE;
        return OVERWORLD_OFFSET;
    }

    private static ResourceKey<DensityFunction> selectFactorKey(ServerLevel level) {
        if (isAmplified(level)) return OVERWORLD_FACTOR_AMPLIFIED;
        if (isLargeBiomes(level)) return OVERWORLD_FACTOR_LARGE;
        return OVERWORLD_FACTOR;
    }

    private static boolean isAmplified(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noiseGen
                && noiseGen.generatorSettings().is(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.AMPLIFIED);
    }

    private static boolean isLargeBiomes(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noiseGen
                && noiseGen.generatorSettings().is(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.LARGE_BIOMES);
    }

    private static ResourceKey<DensityFunction> densityKey(String path) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, new ResourceLocation(path));
    }

    private static NoiseSettings getNoiseSettings(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        if (generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noiseGen) {
            return noiseGen.generatorSettings().value().noiseSettings();
        }
        return NoiseSettings.create(-64, 384, 1, 2);
    }

    public void clearCache() {
        heightCache.clear();
    }

    public int getCacheSize() {
        return heightCache.size();
    }

    private record PreliminaryEstimator(DensityFunction preliminaryDensity,
                                        DensityFunction upperBoundFactor,
                                        DensityFunction upperBoundOffset,
                                        boolean available) {
    }
}
