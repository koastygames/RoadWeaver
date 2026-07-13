/* 文件职责：组织普通道路两阶段寻路与最终路径段生成。 */
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
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainFieldFactory;
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
        return calculateAStarRoadPathDetailed(startIn, endIn, width, level, maxSteps, cache, cfg).segments();
    }

    public static PathCalculationResult calculateAStarRoadPathDetailed(BlockPos startIn,
                                                                       BlockPos endIn,
                                                                       int width,
                                                                       ServerLevel level,
                                                                       int maxSteps,
                                                                       TerrainSamplingCache cache,
                                                                       RoadGenerationConfig cfg) {
        return calculateAStarRoadPathDetailed(startIn, endIn, width, level, maxSteps, cache, cfg, null);
    }

    /**
     * 两阶段寻路：粗路径 → 精采样 → 精路径。
     * 注意：coarseOverride 可能被多条连接共享（来自Registry），调用者负责在所有使用完成后 dispose。
     */
    public static PathCalculationResult calculateAStarRoadPathDetailed(BlockPos startIn,
                                                                       BlockPos endIn,
                                                                       int width,
                                                                       ServerLevel level,
                                                                       int maxSteps,
                                                                       TerrainSamplingCache cache,
                                                                       RoadGenerationConfig cfg,
                                                                       PathTerrainField coarseOverride) {
        PathfindingCostConfig pathCfg = cfg.pathfinding();
        int dGrid = pathCfg.effectiveAStarStep();
        int sx = snapToGrid(startIn.getX(), dGrid);
        int sz = snapToGrid(startIn.getZ(), dGrid);
        int ex = snapToGrid(endIn.getX(), dGrid);
        int ez = snapToGrid(endIn.getZ(), dGrid);

        BlockPos start = new BlockPos(sx, startIn.getY(), sz);
        BlockPos end = new BlockPos(ex, endIn.getY(), ez);

        BlockPos startGround = new BlockPos(start.getX(), heightSampler(cache, start.getX(), start.getZ(), level), start.getZ());
        BlockPos endGround = new BlockPos(end.getX(), heightSampler(cache, end.getX(), end.getZ(), level), end.getZ());

        PathTerrainField coarseTerrain = coarseOverride != null
                && coarseOverride.step() == dGrid
                && coarseOverride.contains(startGround.getX(), startGround.getZ())
                && coarseOverride.contains(endGround.getX(), endGround.getZ())
                ? coarseOverride
                : PathTerrainFieldFactory.cached(level, cache, dGrid);
        List<BlockPos> coarsePath = calculateRawPath(startGround, endGround, level, maxSteps, cache, coarseTerrain, pathCfg);
        if (coarsePath == null || coarsePath.isEmpty()) {
            return PathCalculationResult.failure();
        }

        if (!pathCfg.isAccurateSampling()) {
            return finalizePath(coarsePath, width, level, cache, coarseTerrain, pathCfg);
        }

        PathTerrainField quantizedTerrain = PathTerrainFieldFactory.quantized(
                level,
                cache,
                coarsePath,
                dGrid,
                pathCfg.quantizedSamplingChunkRadius());
        if (quantizedTerrain == null) {
            return finalizePath(coarsePath, width, level, cache, coarseTerrain, pathCfg);
        }

        List<BlockPos> finalRawPath = calculateRawPath(startGround, endGround, level, maxSteps, cache, quantizedTerrain, pathCfg);
        if (finalRawPath == null || finalRawPath.isEmpty()) {
            finalRawPath = coarsePath;
        }
        return finalizePath(finalRawPath, width, level, cache, quantizedTerrain, pathCfg);
    }

    /**
     * 直接使用已计算好的粗路径，跳过粗路径搜索，直接进入精采样阶段。
     * 用于规划阶段已完成粗路径搜索的场景，此时粗采样数据已释放，粗路径从缓存获取。
     */
    public static PathCalculationResult calculateFromCoarsePath(List<BlockPos> coarsePath,
                                                                BlockPos startIn,
                                                                BlockPos endIn,
                                                                int width,
                                                                ServerLevel level,
                                                                int maxSteps,
                                                                TerrainSamplingCache cache,
                                                                RoadGenerationConfig cfg) {
        if (coarsePath == null || coarsePath.isEmpty()) {
            return PathCalculationResult.failure();
        }

        PathfindingCostConfig pathCfg = cfg.pathfinding();
        int dGrid = pathCfg.effectiveAStarStep();

        // 如果不需要精采样，直接用粗路径生成最终路径
        if (!pathCfg.isAccurateSampling()) {
            // 需要一个 fallback terrain field 用于 finalizePath
            PathTerrainField fallback = PathTerrainFieldFactory.cached(level, cache, dGrid);
            return finalizePath(coarsePath, width, level, cache, fallback, pathCfg);
        }

        PathTerrainField quantizedTerrain = PathTerrainFieldFactory.quantized(
                level,
                cache,
                coarsePath,
                dGrid,
                pathCfg.quantizedSamplingChunkRadius());
        if (quantizedTerrain == null) {
            PathTerrainField fallback = PathTerrainFieldFactory.cached(level, cache, dGrid);
            return finalizePath(coarsePath, width, level, cache, fallback, pathCfg);
        }

        // 精路径搜索：重新计算起点终点
        int sx = snapToGrid(startIn.getX(), dGrid);
        int sz = snapToGrid(startIn.getZ(), dGrid);
        int ex = snapToGrid(endIn.getX(), dGrid);
        int ez = snapToGrid(endIn.getZ(), dGrid);
        BlockPos startGround = new BlockPos(sx, heightSampler(cache, sx, sz, level), sz);
        BlockPos endGround = new BlockPos(ex, heightSampler(cache, ex, ez, level), ez);

        List<BlockPos> finalRawPath = calculateRawPath(startGround, endGround, level, maxSteps, cache, quantizedTerrain, pathCfg);
        if (finalRawPath == null || finalRawPath.isEmpty()) {
            finalRawPath = coarsePath;
        }
        return finalizePath(finalRawPath, width, level, cache, quantizedTerrain, pathCfg);
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
                                                      PathTerrainField terrain,
                                                      PathfindingCostConfig pathCfg) {
        if (rawPath == null || rawPath.size() < 2) {
            return PathCalculationResult.failure();
        }
        AccurateHeightSampler accurate = cache.getAccurateSampler(level);
        List<BlockPos> finalPath = rawPath;
        if (pathCfg.needsRefinement()) {
            finalPath = accurate.samplePathHeights(rawPath, 0);
        }
        List<RoadSegmentPlacement> segments = PathPostProcessor.process(
                finalPath,
                width,
                level,
                cache,
                terrain,
                RoadConstants.DEFAULT_BRIDGE_MIN_WATER_DEPTH,
                net.shiroha233.roadweaver.pathfinding.impl.SplineHelper.CurveMode.CATMULL_ROM,
                accurate);
        return segments == null ? PathCalculationResult.failure() : new PathCalculationResult(segments, terrain);
    }

    static int heightSampler(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.height(level, x, z);
    }

    public record PathCalculationResult(List<RoadSegmentPlacement> segments, PathTerrainField terrain) {
        public static PathCalculationResult failure() {
            return new PathCalculationResult(null, null);
        }
    }
}
