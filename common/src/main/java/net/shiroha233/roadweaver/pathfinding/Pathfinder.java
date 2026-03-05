package net.shiroha233.roadweaver.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;

/**
 * 普通道路寻路器接口，支持策略模式切换算法
 */
public interface Pathfinder {
    PathResult findPath(BlockPos start, BlockPos end, int width,
                        ServerLevel level, int maxSteps,
                        TerrainSamplingCache cache, PathfindingCostConfig config);
}
