package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.pathfinding.PathResult;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.impl.BidirectionalAStarPathfinder;

import java.util.ArrayList;
import java.util.List;

/**
 * 道路路径计算器
 * 职责：提供 A* 寻路和桥梁跨度提取功能
 */
public final class RoadPathCalculator {
    private RoadPathCalculator() {}

    /**
     * 使用双向 A* 算法计算道路路径
     */
    public static List<RoadSegmentPlacement> calculateAStarRoadPath(
            BlockPos start, BlockPos end, int width,
            ServerLevel level, int maxSteps,
            TerrainSamplingCache cache, RoadGenerationConfig genConfig) {
        if (start == null || end == null || level == null || cache == null || genConfig == null) {
            return null;
        }

        PathfindingCostConfig costConfig = genConfig.pathfinding();
        if (costConfig == null) {
            return null;
        }

        BidirectionalAStarPathfinder pathfinder = new BidirectionalAStarPathfinder();
        PathResult result = pathfinder.findPath(start, end, width, level, maxSteps, cache, costConfig);

        if (!result.success() || result.segments() == null) {
            return null;
        }

        return result.segments();
    }

    /**
     * 从道路段中提取桥梁跨度
     */
    public static List<RoadSpan> extractSpans(List<RoadSegmentPlacement> segments,
                                               ServerLevel level,
                                               TerrainSamplingCache cache,
                                               PathfindingCostConfig costConfig) {
        if (segments == null || segments.isEmpty() || level == null || cache == null) {
            return new ArrayList<>();
        }

        List<RoadSpan> spans = new ArrayList<>();
        int minWaterDepth = costConfig != null ? costConfig.bridgeMinWaterDepth() : 3;

        int i = 0;
        while (i < segments.size()) {
            BlockPos pos = segments.get(i).middlePos();
            int oceanFloor = cache.oceanFloor(level, pos.getX(), pos.getZ());
            int waterDepth = Math.max(0, level.getSeaLevel() - oceanFloor);

            if (waterDepth >= minWaterDepth) {
                int start = i;
                while (i < segments.size()) {
                    BlockPos p = segments.get(i).middlePos();
                    int floor = cache.oceanFloor(level, p.getX(), p.getZ());
                    int depth = Math.max(0, level.getSeaLevel() - floor);
                    if (depth < minWaterDepth) break;
                    i++;
                }
                if (i > start) {
                    spans.add(new RoadSpan(
                            segments.get(start).middlePos(),
                            segments.get(i - 1).middlePos(),
                            SpanType.BRIDGE));
                }
            } else {
                i++;
            }
        }
        return spans;
    }
}
