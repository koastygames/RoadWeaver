package net.shiroha233.roadweaver.features.highway.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
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

    /**
     * 两阶段寻路：粗路径 → 释放粗采样 → 精采样 → 精路径。
     * 粗采样数据在粗路径搜索完成后立即释放，确保精采样前内存已归还GC。
     */
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

        PathfindingCostConfig pathCfg = cfg.pathfindingCost();
        int dGrid = pathCfg.effectiveAStarStep();
        int sx = snapToGrid(startIn.getX(), dGrid);
        int sz = snapToGrid(startIn.getZ(), dGrid);
        int ex = snapToGrid(endIn.getX(), dGrid);
        int ez = snapToGrid(endIn.getZ(), dGrid);

        BlockPos start = new BlockPos(sx, startIn.getY(), sz);
        BlockPos end = new BlockPos(ex, endIn.getY(), ez);

        BlockPos startGround = new BlockPos(start.getX(), cache.height(level, start.getX(), start.getZ()), start.getZ());
        BlockPos endGround = new BlockPos(end.getX(), cache.height(level, end.getX(), end.getZ()), end.getZ());

        // 阶段一：粗路径搜索
        PathTerrainField coarseTerrain = PathTerrainFieldFactory.cached(level, cache, dGrid);
        HighwayBidirectionalAStarPathfinder.PathCalculationResult coarseResult =
                HighwayBidirectionalAStarPathfinder.calculateLandPath(
                        startGround, endGround, width, level, maxSteps, cache, cfg, coarseTerrain);
        if (coarseResult == null || coarseResult.segments() == null || coarseResult.segments().isEmpty()) {
            return PathCalculationResult.failure();
        }

        if (!pathCfg.isAccurateSampling()) {
            return new PathCalculationResult(coarseResult.segments(), coarseTerrain);
        }

        // 粗路径已得到，释放粗采样缓存（CachedTerrainField是适配器无大数组，但仍释放）
        // 阶段二：精采样
        List<BlockPos> coarsePath = coarseResult.segments().stream()
                .map(RoadSegmentPlacement::middlePos)
                .toList();
        PathTerrainField quantizedTerrain = PathTerrainFieldFactory.quantized(
                level, cache, coarsePath, dGrid, pathCfg.quantizedSamplingChunkRadius());
        if (quantizedTerrain == null) {
            return new PathCalculationResult(coarseResult.segments(), coarseTerrain);
        }

        // 阶段三：精路径搜索
        HighwayBidirectionalAStarPathfinder.PathCalculationResult refinedResult =
                HighwayBidirectionalAStarPathfinder.calculateLandPath(
                        startGround, endGround, width, level, maxSteps, cache, cfg, quantizedTerrain);
        if (refinedResult == null || refinedResult.segments() == null || refinedResult.segments().isEmpty()) {
            quantizedTerrain.dispose();
            return new PathCalculationResult(coarseResult.segments(), coarseTerrain);
        }
        return new PathCalculationResult(refinedResult.segments(), quantizedTerrain);
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
