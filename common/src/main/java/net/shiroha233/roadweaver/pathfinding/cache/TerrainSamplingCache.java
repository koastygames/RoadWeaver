package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 地形采样缓存
 */
public final class TerrainSamplingCache {
    private final ConcurrentHashMap<Long, Boolean> waterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<Long, Boolean>> nearWaterCacheByDistance = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> columnWaterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> heightCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> oceanFloorCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Holder<Biome>> biomeCache = new ConcurrentHashMap<>();

    private volatile FastHeightSampler fastSampler;
    private volatile AccurateHeightSampler accurateSampler;
    private volatile boolean highPrecisionMode = false;

    private static long hashXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public int height(ServerLevel level, int x, int z) {
        long key = hashXZ(x, z);
        Integer cached = heightCache.get(key);
        if (cached != null) {
            TerrainSamplingStats.recordCacheHit();
            return cached;
        }
        TerrainSamplingStats.recordCacheMiss();
        int h;
        if (highPrecisionMode) {
            ensureAccurateSampler(level);
            h = accurateSampler.surfaceHeight(x, z);
        } else {
            ensureFastSampler(level);
            h = fastSampler.sampleHeight(x, z);
        }
        heightCache.put(key, h);
        return h;
    }

    public boolean isWaterLike(ServerLevel level, int x, int z) {
        long key = hashXZ(x, z);
        Boolean cached = waterCache.get(key);
        if (cached != null) {
            TerrainSamplingStats.recordCacheHit();
            return cached;
        }
        TerrainSamplingStats.recordCacheMiss();
        var chunkSource = level.getChunkSource();
        var randomState = chunkSource.getGeneratorState().randomState();
        var biomeSource = chunkSource.getGenerator().getBiomeSource();
        Holder<Biome> biome = biomeSource.getNoiseBiome(x >> 2, 16, z >> 2, randomState.sampler());
        boolean res = biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN);
        waterCache.put(key, res);
        return res;
    }

    public int oceanFloor(ServerLevel level, int x, int z) {
        long key = hashXZ(x, z);
        Integer cached = oceanFloorCache.get(key);
        if (cached != null) {
            TerrainSamplingStats.recordCacheHit();
            return cached;
        }
        TerrainSamplingStats.recordCacheMiss();
        int h;
        if (highPrecisionMode) {
            ensureAccurateSampler(level);
            h = accurateSampler.oceanFloorWg(x, z);
        } else {
            ensureFastSampler(level);
            h = fastSampler.sampleHeight(x, z);
        }
        oceanFloorCache.put(key, h);
        return h;
    }

    public boolean isNearWaterLike(ServerLevel level, int x, int z, int neighborDistance) {
        int distance = Math.max(0, neighborDistance);
        ConcurrentHashMap<Long, Boolean> nearWaterCache = nearWaterCacheByDistance.computeIfAbsent(
                distance,
                ignored -> new ConcurrentHashMap<>());
        long key = hashXZ(x, z);
        Boolean cached = nearWaterCache.get(key);
        if (cached != null) {
            TerrainSamplingStats.recordCacheHit();
            return cached;
        }
        TerrainSamplingStats.recordCacheMiss();
        int d = distance;
        int[][] offsets = {
                {d, 0}, {-d, 0}, {0, d}, {0, -d},
                {d, d}, {d, -d}, {-d, d}, {-d, -d}
        };
        for (int[] off : offsets) {
            if (isWaterLike(level, x + off[0], z + off[1])) {
                nearWaterCache.put(key, true);
                return true;
            }
        }
        nearWaterCache.put(key, false);
        return false;
    }

    public boolean isColumnWater(ServerLevel level, int x, int z) {
        long key = hashXZ(x, z);
        Boolean cached = columnWaterCache.get(key);
        if (cached != null) {
            TerrainSamplingStats.recordCacheHit();
            return cached;
        }
        TerrainSamplingStats.recordCacheMiss();
        int of = oceanFloor(level, x, z);
        int sea = level.getSeaLevel();
        int surface = height(level, x, z);
        if (highPrecisionMode) {
            ensureAccurateSampler(level);
            surface = accurateSampler.worldSurfaceWg(x, z);
        }
        boolean isWaterBiome = isWaterLike(level, x, z);
        boolean biomeWater = isWaterBiome && of < sea;
        boolean heightWater = (surface <= sea + 1) && (of < surface - 1);
        boolean res = biomeWater || heightWater;
        columnWaterCache.put(key, res);
        return res;
    }

    public Holder<Biome> getBiome(ServerLevel level, int x, int z) {
        long key = hashXZ(x, z);
        Holder<Biome> cached = biomeCache.get(key);
        if (cached != null) {
            TerrainSamplingStats.recordCacheHit();
            return cached;
        }
        TerrainSamplingStats.recordCacheMiss();
        var chunkSource = level.getChunkSource();
        var randomState = chunkSource.getGeneratorState().randomState();
        var biomeSource = chunkSource.getGenerator().getBiomeSource();
        Holder<Biome> biome = biomeSource.getNoiseBiome(x >> 2, 16, z >> 2, randomState.sampler());
        biomeCache.put(key, biome);
        return biome;
    }

    public void prewarmRegion(ServerLevel level, int minX, int minZ, int maxX, int maxZ, int step) {
        ensureFastSampler(level);
        fastSampler.prewarmRegion(minX, minZ, maxX, maxZ, step);
    }

    public void enableHighPrecision(ServerLevel level) {
        this.highPrecisionMode = true;
        clearPrecisionDerivedCaches();
        ensureAccurateSampler(level);
    }

    public void disableHighPrecision() {
        this.highPrecisionMode = false;
        clearPrecisionDerivedCaches();
    }

    private void clearPrecisionDerivedCaches() {
        heightCache.clear();
        oceanFloorCache.clear();
        columnWaterCache.clear();
    }

    public boolean isHighPrecisionMode() {
        return highPrecisionMode;
    }

    public AccurateHeightSampler getAccurateSampler(ServerLevel level) {
        ensureAccurateSampler(level);
        return accurateSampler;
    }

    public void clear() {
        waterCache.clear();
        nearWaterCacheByDistance.clear();
        columnWaterCache.clear();
        heightCache.clear();
        oceanFloorCache.clear();
        biomeCache.clear();
        if (fastSampler != null) fastSampler.clearCache();
        if (accurateSampler != null) accurateSampler.clear();
    }

    private void ensureFastSampler(ServerLevel level) {
        if (fastSampler == null) {
            synchronized (this) {
                if (fastSampler == null) {
                    fastSampler = FastHeightSampler.create(level);
                }
            }
        }
    }

    private void ensureAccurateSampler(ServerLevel level) {
        if (accurateSampler == null) {
            synchronized (this) {
                if (accurateSampler == null) {
                    accurateSampler = AccurateHeightSampler.create(level);
                }
            }
        }
    }
}
