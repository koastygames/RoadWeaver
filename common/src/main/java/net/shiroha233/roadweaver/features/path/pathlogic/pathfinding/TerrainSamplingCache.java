package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.HashMap;
import java.util.Map;

public final class TerrainSamplingCache {
    private final Map<Long, Boolean> waterCache = new HashMap<>();
    private final Map<Long, Boolean> nearWaterCache = new HashMap<>();
    private final Map<Long, Boolean> columnWaterCache = new HashMap<>();
    private final Map<Long, Integer> heightCache = new HashMap<>();
    private final Map<Long, Integer> oceanFloorCache = new HashMap<>();
    private final Map<Long, Holder<Biome>> biomeCache = new HashMap<>();

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
        var generator = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().getGeneratorState().randomState();
        int sea = level.getSeaLevel();
        int motion = generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level, rs);
        if (motion > sea + 2) {
            heightCache.put(key, motion);
            return motion;
        }

        int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, rs);
        boolean waterBiome = isWaterLike(level, x, z);
        boolean shouldUseSurface = waterBiome || (surface <= sea + 2 && oceanFloor(level, x, z) < sea);
        int h = shouldUseSurface ? surface : motion;

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

        // 修正：使用 BiomeSource 进行噪声采样，不加载区块
        // 注意：getNoiseBiome 需要夸脱坐标 (x >> 2, y >> 2, z >> 2)
        var chunkSource = level.getChunkSource();
        var randomState = chunkSource.getGeneratorState().randomState();
        var biomeSource = chunkSource.getGenerator().getBiomeSource();

        // 采样 Y=64 处的生物群系（海平面附近）
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
        var generator = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().getGeneratorState().randomState();
        // 修正：使用对应的 Heightmap 类型进行噪声采样
        int h = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
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
        int[][] neighborOffsets = new int[][] {
                { d, 0 }, { -d, 0 }, { 0, d }, { 0, -d },
                { d, d }, { d, -d }, { -d, d }, { -d, -d }
        };
        for (int[] off : neighborOffsets) {
            int nx = x + off[0];
            int nz = z + off[1];
            if (isWaterLike(level, nx, nz)) {
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

        // 使用多种方式检测水体，解决以下问题：
        // 1. 浅滩(beach)不在 IS_RIVER/IS_OCEAN 群系标签中，但实际有水
        // 2. 群系边界处噪声采样可能判断失误，导致跨海不建桥

        int of = oceanFloor(level, x, z);  // OCEAN_FLOOR_WG：海底/河床高度
        int h = height(level, x, z);        // MOTION_BLOCKING_NO_LEAVES：表面高度
        int sea = level.getSeaLevel();

        // 方法1：群系判断（原有逻辑）
        // 适用于标准的河流/海洋群系
        boolean isWaterBiome = isWaterLike(level, x, z);
        boolean biomeWater = isWaterBiome && of < sea;

        // 方法2：高度差判断（新增逻辑）
        // 核心原理：如果表面高度接近海平面，但海底明显更低，说明中间是水
        // - h <= sea + 1：表面在海平面或略高（水面通常在 seaLevel）
        // - of < h - 1：海底比表面低至少2格，说明有水深
        // 这样可以检测到：浅滩、沼泽边缘、甚至非标准群系中的水体
        boolean heightWater = (h <= sea + 1) && (of < h - 1);

        // 任一方法检测到水体即可
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

    public void clear() {
        waterCache.clear();
        nearWaterCache.clear();
        columnWaterCache.clear();
        heightCache.clear();
        oceanFloorCache.clear();
        biomeCache.clear();
    }
}
