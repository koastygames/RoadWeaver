package net.shiroha233.roadweaver.features.highway.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.features.highway.config.HighwayGenerationConfig;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainCachePrewarmer;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;

import java.util.List;

/**
 * Highway 路径计算器
 */
public final class HighwayPathCalculator {
    private HighwayPathCalculator() {}

    public static List<RoadSegmentPlacement> calculateHighwayPath(BlockPos startIn,
                                                                  BlockPos endIn,
                                                                  int width,
                                                                  ServerLevel level,
                                                                  int maxSteps,
                                                                  TerrainSamplingCache cache,
                                                                  HighwayGenerationConfig cfg) {
        if (startIn == null || endIn == null || level == null || cache == null || cfg == null) {
            return null;
        }

        int dGrid = cfg.pathfindingCost().effectiveAStarStep();
        int sx = snapToGrid(startIn.getX(), dGrid);
        int sz = snapToGrid(startIn.getZ(), dGrid);
        int ex = snapToGrid(endIn.getX(), dGrid);
        int ez = snapToGrid(endIn.getZ(), dGrid);

        BlockPos start = new BlockPos(sx, startIn.getY(), sz);
        BlockPos end = new BlockPos(ex, endIn.getY(), ez);

        boolean accurateSampling = cfg.pathfindingCost() != null && cfg.pathfindingCost().isAccurateSampling();
        if (accurateSampling) {
            cache.enableHighPrecision(level);
        }

        try {
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
        } finally {
            if (accurateSampling) {
                cache.disableHighPrecision();
            }
        }
    }

    private static int snapToGrid(int v, int gridSize) {
        return Math.floorDiv(v, gridSize) * gridSize;
    }
}
