package net.shiroha233.roadweaver.features.highway.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.features.highway.config.HighwayGenerationConfig;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainFieldFactory;

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
        return calculateHighwayPathDetailed(startIn, endIn, width, level, maxSteps, cache, cfg).segments();
    }

    public static PathCalculationResult calculateHighwayPathDetailed(BlockPos startIn,
                                                                     BlockPos endIn,
                                                                     int width,
                                                                     ServerLevel level,
                                                                     int maxSteps,
                                                                     TerrainSamplingCache cache,
                                                                     HighwayGenerationConfig cfg) {
        if (startIn == null || endIn == null || level == null || cache == null || cfg == null) {
            return PathCalculationResult.failure();
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
            HighwayBidirectionalAStarPathfinder.PathCalculationResult coarseResult = HighwayBidirectionalAStarPathfinder.calculateLandPath(
                    startGround,
                    endGround,
                    width,
                    level,
                    maxSteps,
                    cache,
                    cfg,
                    null);
            if (!accurateSampling || coarseResult.segments() == null || coarseResult.segments().isEmpty()) {
                return new PathCalculationResult(coarseResult.segments(), coarseResult.terrain());
            }

            List<BlockPos> coarsePath = coarseResult.segments().stream()
                    .map(RoadSegmentPlacement::middlePos)
                    .toList();
            PathTerrainField terrain = PathTerrainFieldFactory.quantized(
                    level,
                    cache,
                    coarsePath,
                    dGrid,
                    cfg.pathfindingCost().quantizedSamplingChunkRadius());
            if (terrain == null) {
                return new PathCalculationResult(coarseResult.segments(), null);
            }

            HighwayBidirectionalAStarPathfinder.PathCalculationResult refinedResult = HighwayBidirectionalAStarPathfinder.calculateLandPath(
                    startGround,
                    endGround,
                    width,
                    level,
                    maxSteps,
                    cache,
                    cfg,
                    terrain);
            if (refinedResult.segments() == null || refinedResult.segments().isEmpty()) {
                return new PathCalculationResult(coarseResult.segments(), null);
            }
            return new PathCalculationResult(refinedResult.segments(), refinedResult.terrain());
        } finally {
            if (accurateSampling) {
                cache.disableHighPrecision();
            }
        }
    }

    private static int snapToGrid(int v, int gridSize) {
        return Math.floorDiv(v, gridSize) * gridSize;
    }

    public record PathCalculationResult(List<RoadSegmentPlacement> segments, PathTerrainField terrain) {
        public static PathCalculationResult failure() {
            return new PathCalculationResult(null, null);
        }
    }
}
