package net.shiroha233.roadweaver.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.HighwayConfig;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;

/**
 * 高速公路寻路器接口
 */
public interface HighwayPathfinder {
    PathResult findPath(BlockPos start, BlockPos end, int width,
                        ServerLevel level, int maxSteps,
                        TerrainSamplingCache cache,
                        PathfindingCostConfig pathConfig, HighwayConfig highwayConfig);
}
