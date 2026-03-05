package net.shiroha233.roadweaver.pathfinding.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;

/**
 * 寻路公共工具方法
 */
public final class PathfindingHelper {
    private PathfindingHelper() {}

    public static int heightSampler(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.height(level, x, z);
    }

    public static boolean isWaterLike(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.isWaterLike(level, x, z);
    }

    public static int oceanFloorSampler(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.oceanFloor(level, x, z);
    }

    public static boolean isNearWaterLike(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.isNearWaterLike(level, x, z, 16);
    }

    public static boolean isColumnWater(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.isColumnWater(level, x, z);
    }

    public static int snapToGrid(int v, int gridSize) {
        return Math.floorDiv(v, gridSize) * gridSize;
    }

    public static int calculateTerrainStability(TerrainSamplingCache cache, BlockPos pos, int y,
                                                ServerLevel level, int step) {
        int cost = 0;
        if (Math.abs(heightSampler(cache, pos.getX() + step, pos.getZ(), level) - y) > 0) cost++;
        if (Math.abs(heightSampler(cache, pos.getX() - step, pos.getZ(), level) - y) > 0) cost++;
        if (Math.abs(heightSampler(cache, pos.getX(), pos.getZ() + step, level) - y) > 0) cost++;
        if (Math.abs(heightSampler(cache, pos.getX(), pos.getZ() - step, level) - y) > 0) cost++;
        return cost;
    }
}
