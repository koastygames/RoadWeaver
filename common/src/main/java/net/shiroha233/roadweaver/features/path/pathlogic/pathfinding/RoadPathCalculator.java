/* 文件职责：组织普通道路的精确量化寻路与最终路径段生成。 */
package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.pathfinding.PathResult;
import net.shiroha233.roadweaver.pathfinding.Pathfinder;
import net.shiroha233.roadweaver.pathfinding.PathfinderFactory;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.AccuratePathHeightResolver;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainRegion;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainRegionSampler;
import net.shiroha233.roadweaver.pathfinding.impl.PathPostProcessor;

import java.util.List;

import static net.shiroha233.roadweaver.pathfinding.impl.PathfindingHelper.snapToGrid;

/**
 * 道路路径计算器
 */
public final class RoadPathCalculator {
    private RoadPathCalculator() {}

    public static List<RoadSegmentPlacement> calculateAStarRoadPath(BlockPos startIn,
                                                                    BlockPos endIn,
                                                                    int width,
                                                                    ServerLevel level,
                                                                    int maxSteps,
                                                                    TerrainSamplingCache cache,
                                                                    RoadGenerationConfig cfg) {
        PathCalculationResult result = calculateAStarRoadPathDetailed(startIn, endIn, width, level, maxSteps, cache, cfg);
        try {
            return result.segments();
        } finally {
            if (result.terrain() != null) {
                result.terrain().dispose();
            }
        }
    }

    public static PathCalculationResult calculateAStarRoadPathDetailed(BlockPos startIn,
                                                                       BlockPos endIn,
                                                                       int width,
                                                                       ServerLevel level,
                                                                       int maxSteps,
                                                                       TerrainSamplingCache cache,
                                                                       RoadGenerationConfig cfg) {
        if (startIn == null || endIn == null || level == null || cache == null || cfg == null) {
            return PathCalculationResult.failure();
        }
        PathfindingCostConfig pathCfg = cfg.pathfinding();
        int dGrid = pathCfg.effectiveAStarStep();
        int sx = snapToGrid(startIn.getX(), dGrid);
        int sz = snapToGrid(startIn.getZ(), dGrid);
        int ex = snapToGrid(endIn.getX(), dGrid);
        int ez = snapToGrid(endIn.getZ(), dGrid);

        BlockPos start = new BlockPos(sx, startIn.getY(), sz);
        BlockPos end = new BlockPos(ex, endIn.getY(), ez);

        AccurateTerrainRegion terrain;
        int margin = searchMargin(start, end);
        try {
            terrain = AccurateTerrainRegionSampler.sample(level, cache,
                    Math.min(start.getX(), end.getX()) - margin,
                    Math.min(start.getZ(), end.getZ()) - margin,
                    Math.max(start.getX(), end.getX()) + margin,
                    Math.max(start.getZ(), end.getZ()) + margin,
                    dGrid);
        } catch (RuntimeException failure) {
            return PathCalculationResult.failure();
        }

        boolean handedOff = false;
        try {
            BlockPos startGround = new BlockPos(start.getX(), terrain.height(start.getX(), start.getZ()), start.getZ());
            BlockPos endGround = new BlockPos(end.getX(), terrain.height(end.getX(), end.getZ()), end.getZ());
            List<BlockPos> rawPath = calculateRawPath(startGround, endGround, level, maxSteps, cache, terrain, pathCfg);
            if (rawPath == null || rawPath.isEmpty()) {
                return PathCalculationResult.failure();
            }
            PathCalculationResult result = finalizePath(rawPath, width, level, cache, terrain);
            handedOff = result.terrain() != null;
            return result;
        } finally {
            if (!handedOff) {
                terrain.dispose();
            }
        }
    }

    /**
     * 直接后处理规划阶段已经完成的精确路径，不再采样或重复寻路。
     */
    public static PathCalculationResult calculateFromPlannedPath(List<BlockPos> plannedPath,
                                                                  int width,
                                                                  ServerLevel level,
                                                                  TerrainSamplingCache cache,
                                                                  AccurateTerrainRegion terrain) {
        if (plannedPath == null || plannedPath.isEmpty() || terrain == null || terrain.isDisposed()) {
            return PathCalculationResult.failure();
        }
        return finalizePath(plannedPath, width, level, cache, terrain);
    }

    private static List<BlockPos> calculateRawPath(BlockPos startGround,
                                                   BlockPos endGround,
                                                   ServerLevel level,
                                                   int maxSteps,
                                                   TerrainSamplingCache cache,
                                                   PathTerrainField terrain,
                                                   PathfindingCostConfig pathCfg) {
        var algo = pathCfg.pathfindingAlgorithm();
        Pathfinder pathfinder = PathfinderFactory.create(algo);
        PathResult result = pathfinder.findRawPath(startGround, endGround, level, maxSteps, cache, terrain, pathCfg);
        if (!result.success() || !result.hasRawPath()) {
            return null;
        }
        return result.rawPath();
    }

    private static PathCalculationResult finalizePath(List<BlockPos> rawPath,
                                                      int width,
                                                      ServerLevel level,
                                                      TerrainSamplingCache cache,
                                                      PathTerrainField terrain) {
        if (rawPath == null || rawPath.size() < 2) {
            return PathCalculationResult.failure();
        }
        AccurateHeightSampler accurate = cache.getAccurateSampler(level);
        List<BlockPos> finalPath = AccuratePathHeightResolver.resolve(rawPath, terrain, accurate);
        List<RoadSegmentPlacement> segments = PathPostProcessor.process(
                finalPath,
                width,
                level,
                cache,
                terrain,
                RoadConstants.DEFAULT_BRIDGE_MIN_WATER_DEPTH,
                net.shiroha233.roadweaver.pathfinding.impl.SplineHelper.CurveMode.CATMULL_ROM,
                accurate);
        if (segments == null) {
            return PathCalculationResult.failure();
        }
        return new PathCalculationResult(segments, terrain);
    }

    static int searchMargin(BlockPos start, BlockPos end) {
        int distance = Math.abs(start.getX() - end.getX()) + Math.abs(start.getZ() - end.getZ());
        return Math.min(RoadConstants.DEFAULT_PLAN_MAX_EDGE_LEN_BLOCKS, Math.max(512, distance));
    }

    public record PathCalculationResult(List<RoadSegmentPlacement> segments, PathTerrainField terrain) {
        public static PathCalculationResult failure() {
            return new PathCalculationResult(null, null);
        }
    }
}
