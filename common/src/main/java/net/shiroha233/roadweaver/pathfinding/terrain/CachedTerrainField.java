/* 文件职责：将旧地形缓存适配为普通道路搜索地形场。 */
package net.shiroha233.roadweaver.pathfinding.terrain;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;

/**
 * 基于旧缓存层的地形场适配器。
 */
public final class CachedTerrainField implements PathTerrainField {
    private final ServerLevel level;
    private final TerrainSamplingCache cache;
    private final int step;

    public CachedTerrainField(ServerLevel level, TerrainSamplingCache cache, int step) {
        this.level = level;
        this.cache = cache;
        this.step = step;
    }

    @Override
    public int seaLevel() {
        return level.getSeaLevel();
    }

    @Override
    public int height(int x, int z) {
        return cache.height(level, x, z);
    }

    @Override
    public int oceanFloor(int x, int z) {
        return cache.oceanFloor(level, x, z);
    }

    @Override
    public boolean isColumnWater(int x, int z) {
        return cache.isColumnWater(level, x, z);
    }

    @Override
    public boolean isNearWater(int x, int z, int neighborDistance) {
        return cache.isNearWaterLike(level, x, z, neighborDistance);
    }

    @Override
    public Holder<Biome> biome(int x, int z) {
        return cache.getBiome(level, x, z);
    }

    @Override
    public boolean contains(int x, int z) {
        return true;
    }

    @Override
    public int step() {
        return step;
    }
}
