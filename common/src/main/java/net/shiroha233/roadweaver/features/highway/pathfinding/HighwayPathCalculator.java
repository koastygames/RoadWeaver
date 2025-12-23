package net.shiroha233.roadweaver.features.highway.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.features.highway.config.HighwayGenerationConfig;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.TerrainCachePrewarmer;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.TerrainSamplingCache;
import net.shiroha233.roadweaver.helpers.Records;

import java.util.List;

/**
 * Highway 寻路计算器。
 *
 * 职责：
 * - 将端点对齐到 Highway 网格（步长来自 cfg.pathfinding.aStarStep）
 * - 可选预热 TerrainSamplingCache
 * - 调用 Highway 专用寻路器返回 RoadSegmentPlacement 列表
 */
public final class HighwayPathCalculator {
    private HighwayPathCalculator() {}

    public static List<Records.RoadSegmentPlacement> calculateHighwayPath(BlockPos startIn,
                                                                          BlockPos endIn,
                                                                          int width,
                                                                          ServerLevel level,
                                                                          int maxSteps,
                                                                          TerrainSamplingCache cache,
                                                                          HighwayGenerationConfig cfg) {
        if (startIn == null || endIn == null || level == null || cache == null || cfg == null) {
            return null;
        }

        int dGrid = cfg.pathfinding().effectiveAStarStep();
        int sx = snapToGrid(startIn.getX(), dGrid);
        int sz = snapToGrid(startIn.getZ(), dGrid);
        int ex = snapToGrid(endIn.getX(), dGrid);
        int ez = snapToGrid(endIn.getZ(), dGrid);

        BlockPos start = new BlockPos(sx, startIn.getY(), sz);
        BlockPos end = new BlockPos(ex, endIn.getY(), ez);

        BlockPos startGround = new BlockPos(start.getX(), cache.height(level, start.getX(), start.getZ()), start.getZ());
        BlockPos endGround = new BlockPos(end.getX(), cache.height(level, end.getX(), end.getZ()), end.getZ());

        if (cfg.hierarchicalPathfindingEnabled()) {
            TerrainCachePrewarmer.prewarmAlongRoute(
                    startGround,
                    endGround,
                    level,
                    Math.max(500, maxSteps / 4),
                    cache);
        }

        return HighwayBidirectionalAStarPathfinder.calculateLandPath(
                startGround,
                endGround,
                width,
                level,
                maxSteps,
                cache,
                cfg);
    }

    private static int snapToGrid(int v, int gridSize) {
        return Math.floorDiv(v, gridSize) * gridSize;
    }
}
