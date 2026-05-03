/* 文件职责：组织普通道路两阶段寻路与最终路径段生成。 */
package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;
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

        PathTerrainField coarseTerrain = PathTerrainFieldFactory.cached(level, cache, dGrid);
        List<BlockPos> coarsePath = calculateRawPath(startGround, endGround, level, maxSteps, cache, coarseTerrain, pathCfg);
        if (coarsePath == null || coarsePath.isEmpty()) {
            return null;
        }

        if (!pathCfg.isAccurateSampling()) {
            return finalizePath(coarsePath, width, level, cache, pathCfg);
        }

        PathTerrainField quantizedTerrain = PathTerrainFieldFactory.quantized(
                level,
                cache,
                coarsePath,
                dGrid,
                pathCfg.quantizedSamplingChunkRadius());
        if (quantizedTerrain == null) {
            return finalizePath(coarsePath, width, level, cache, pathCfg);
        }

        List<BlockPos> finalRawPath = calculateRawPath(startGround, endGround, level, maxSteps, cache, quantizedTerrain, pathCfg);
        if (finalRawPath == null || finalRawPath.isEmpty()) {
            finalRawPath = coarsePath;
        }
        return finalizePath(finalRawPath, width, level, cache, pathCfg);
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

    private static List<RoadSegmentPlacement> finalizePath(List<BlockPos> rawPath,
                                                           int width,
                                                           ServerLevel level,
                                                           TerrainSamplingCache cache,
                                                           PathfindingCostConfig pathCfg) {
        if (rawPath == null || rawPath.size() < 2) {
            return null;
        }
        AccurateHeightSampler accurate = cache.getAccurateSampler(level);
        List<BlockPos> finalPath = rawPath;
        if (pathCfg.needsRefinement()) {
            finalPath = accurate.samplePathHeights(rawPath, 0);
        }
        return PathPostProcessor.process(finalPath, width, level, cache,
                net.shiroha233.roadweaver.core.constants.RoadConstants.DEFAULT_BRIDGE_MIN_WATER_DEPTH, accurate);
    }

    static int heightSampler(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.height(level, x, z);
    }
}
