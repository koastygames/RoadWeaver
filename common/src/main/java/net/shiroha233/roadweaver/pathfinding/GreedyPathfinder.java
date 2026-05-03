package net.shiroha233.roadweaver.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

/**
 * 长途驾驶贪婪寻路器接口
 */
public interface GreedyPathfinder {
    PathResult findPath(BlockPos start, double dirX, double dirZ,
                        int maxSteps, int width,
                        ServerLevel level, TerrainSamplingCache cache,
                        PathfindingCostConfig config, double dirBias);

    default PathResult findPath(BlockPos start, double dirX, double dirZ,
                                int maxSteps, int width,
                                ServerLevel level, TerrainSamplingCache cache,
                                PathTerrainField terrain,
                                PathfindingCostConfig config, double dirBias) {
        return findPath(start, dirX, dirZ, maxSteps, width, level, cache, config, dirBias);
    }
}
