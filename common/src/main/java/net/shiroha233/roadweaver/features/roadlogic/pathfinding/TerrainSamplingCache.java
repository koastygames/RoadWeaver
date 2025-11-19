package net.shiroha233.roadweaver.features.roadlogic.pathfinding;

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

    private static long hashXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public int height(ServerLevel level, int x, int z) {
        long key = hashXZ(x, z);
        Integer cached = heightCache.get(key);
        if (cached != null) {
            return cached;
        }
        var generator = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().getGeneratorState().randomState();
        int h = generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level, rs);
        heightCache.put(key, h);
        return h;
    }

    boolean isWaterLike(ServerLevel level, int x, int z) {
        long key = hashXZ(x, z);
        Boolean cached = waterCache.get(key);
        if (cached != null) return cached;
        
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

    int oceanFloor(ServerLevel level, int x, int z) {
        long key = hashXZ(x, z);
        Integer cached = oceanFloorCache.get(key);
        if (cached != null) {
            return cached;
        }
        var generator = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().getGeneratorState().randomState();
        // 修正：使用对应的 Heightmap 类型进行噪声采样
        int h = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs);
        oceanFloorCache.put(key, h);
        return h;
    }

    boolean isNearWaterLike(ServerLevel level, int x, int z, int neighborDistance) {
        long key = hashXZ(x, z);
        Boolean cached = nearWaterCache.get(key);
        if (cached != null) return cached;
        int d = neighborDistance;
        int[][] neighborOffsets = new int[][]{
                {d, 0}, {-d, 0}, {0, d}, {0, -d},
                {d, d}, {d, -d}, {-d, d}, {-d, -d}
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

    boolean isColumnWater(ServerLevel level, int x, int z) {
        long key = hashXZ(x, z);
        Boolean cached = columnWaterCache.get(key);
        if (cached != null) return cached;
        
        // 修正：不再查询 FluidState，而是通过比较地形高度和海平面来推断
        // 这种方法线程安全，且不需要加载区块
        int of = oceanFloor(level, x, z); // OCEAN_FLOOR
        int sea = level.getSeaLevel();
        
        // 如果“海底高度”远低于“海平面”，且“表面高度”接近海平面，通常意味着这是水体
        // 或者简单地：如果 oceanFloor < seaLevel，且该位置属于水生群系，则基本是水
        
        boolean isWaterBiome = isWaterLike(level, x, z);
        boolean isBelowSea = of < sea;
        
        // 逻辑：如果是海洋/河流群系，且海底在海平面下，那就是水
        boolean res = isWaterBiome && isBelowSea;
        
        columnWaterCache.put(key, res);
        return res;
    }
}
