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
    private final ConcurrentHashMap<Long, Boolean> nearWaterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> columnWaterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> heightCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> oceanFloorCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Holder<Biome>> biomeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> accurateChunkHints = new ConcurrentHashMap<>();

    private volatile FastHeightSampler fastSampler;
    private volatile AccurateHeightSampler accurateSampler;
    private volatile boolean highPrecisionMode = false;

    private static long hashXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
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
        if (highPrecisionMode && shouldUseAccurate(x, z)) {
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
        if (highPrecisionMode && shouldUseAccurate(x, z)) {
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
        long key = hashXZ(x, z);
        Boolean cached = nearWaterCache.get(key);
        if (cached != null) {
            TerrainSamplingStats.recordCacheHit();
            return cached;
        }
        TerrainSamplingStats.recordCacheMiss();
        int d = neighborDistance;
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
        int h = height(level, x, z);
        int sea = level.getSeaLevel();
        boolean isWaterBiome = isWaterLike(level, x, z);
        boolean biomeWater = isWaterBiome && of < sea;
        boolean heightWater = (h <= sea + 1) && (of < h - 1);
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
        heightCache.clear();
        oceanFloorCache.clear();
        ensureAccurateSampler(level);
    }

    public void disableHighPrecision() {
        this.highPrecisionMode = false;
        heightCache.clear();
        oceanFloorCache.clear();
        accurateChunkHints.clear();
    }

    public boolean isHighPrecisionMode() {
        return highPrecisionMode;
    }

    public AccurateHeightSampler getAccurateSampler(ServerLevel level) {
        ensureAccurateSampler(level);
        return accurateSampler;
    }

    public void clearAccurateCorridor() {
        accurateChunkHints.clear();
    }

    public void markAccurateChunk(int chunkX, int chunkZ, int radiusChunks) {
        int radius = Math.max(0, radiusChunks);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                accurateChunkHints.put(chunkKey(chunkX + dx, chunkZ + dz), Boolean.TRUE);
            }
        }
    }

    public void markAccurateBlock(int x, int z, int radiusChunks) {
        markAccurateChunk(x >> 4, z >> 4, radiusChunks);
    }

    public void markAccurateLine(int startX, int startZ, int endX, int endZ, int radiusChunks, int stepBlocks) {
        int distance = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        int step = Math.max(1, stepBlocks);
        int samples = Math.max(1, (distance + step - 1) / step);
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            int x = (int) Math.round(startX + (endX - startX) * t);
            int z = (int) Math.round(startZ + (endZ - startZ) * t);
            markAccurateBlock(x, z, radiusChunks);
        }
    }

    public void clear() {
        waterCache.clear();
        nearWaterCache.clear();
        columnWaterCache.clear();
        heightCache.clear();
        oceanFloorCache.clear();
        biomeCache.clear();
        accurateChunkHints.clear();
        if (fastSampler != null) fastSampler.clearCache();
        if (accurateSampler != null) accurateSampler.clear();
    }

    private boolean shouldUseAccurate(int x, int z) {
        if (accurateChunkHints.isEmpty()) {
            return true;
        }
        return accurateChunkHints.containsKey(chunkKey(x >> 4, z >> 4));
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
