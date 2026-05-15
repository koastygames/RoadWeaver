package net.shiroha233.roadweaver.features.highway.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.highway.HighwayRoadTypes;
import net.shiroha233.roadweaver.features.highway.config.HighwayGenerationConfig;
import net.shiroha233.roadweaver.features.highway.pathfinding.HighwayPathCalculator;
import net.shiroha233.roadweaver.features.highway.pathfinding.HighwayPathCalculator.PathCalculationResult;
import net.shiroha233.roadweaver.features.path.pathlogic.core.StructureRoadOffsetService;
import net.shiroha233.roadweaver.pathfinding.PathSpanExtractor;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责生成高速公路道路数据并写入持久化存储。
 */
public final class HighwayRoad {
    private final ServerLevel level;
    private final StructureConnection connection;
    private final HighwayGenerationConfig generationConfig;

    public HighwayRoad(ServerLevel level, StructureConnection connection, HighwayGenerationConfig generationConfig) {
        this.level = level;
        this.connection = connection;
        this.generationConfig = generationConfig;
    }

    public boolean generateRoad(int maxSteps) {
        if (level == null || connection == null || generationConfig == null) {
            return false;
        }
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return false;
        }

        int width = Math.max(1, generationConfig.roadWidth());
        List<BlockState> materials = List.of();
        List<BlockState> slabMaterials = List.of();
        BlockPos rawStart = connection.from();
        BlockPos rawEnd = connection.to();

        TerrainSamplingCache cache = new TerrainSamplingCache();
        try {
            RoadGenerationConfig adaptedConfig = generationConfig.toRoadGenerationConfig();
            PathCalculationResult pathResult = HighwayPathCalculator.calculateHighwayPathDetailed(
                    rawStart,
                    rawEnd,
                    width,
                    level,
                    Math.max(1, maxSteps),
                    cache,
                    generationConfig);
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            List<RoadSegmentPlacement> rawSegments = pathResult.segments();
            if (rawSegments == null || rawSegments.size() < 3) {
                return false;
            }

            List<RoadSegmentPlacement> segments = StructureRoadOffsetService.trimPathNearStructure(
                    level,
                    rawSegments,
                    rawStart,
                    rawEnd);
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            if (segments == null || segments.size() < 3) {
                return false;
            }

            PathTerrainField terrain = pathResult.terrain();
            List<RoadSpan> spans = PathSpanExtractor.extractSpans(
                    segments,
                    level,
                    cache,
                    terrain,
                    adaptedConfig.bridgeMinWaterDepth());
            List<Integer> targetY = computeTargetY(level, segments, spans, generationConfig);
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }

            RoadData roadData = new RoadData(
                    width,
                    HighwayRoadTypes.HIGHWAY,
                    materials,
                    slabMaterials,
                    segments,
                    spans,
                    targetY,
                    RoadData.NO_OWNER_2D,
                    RoadData.NO_OWNER_2D);
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            RoadShardStorage.addRoad(level, roadData);
            return true;
        } finally {
            cache.clear();
        }
    }

    private static List<Integer> computeTargetY(ServerLevel level,
            List<RoadSegmentPlacement> segments,
            List<RoadSpan> spans,
            HighwayGenerationConfig generationConfig) {
        int segmentCount = segments.size();
        List<BlockPos> centers = new ArrayList<>(segmentCount);
        for (RoadSegmentPlacement segment : segments) {
            centers.add(segment.middlePos());
        }

        boolean[] bridgeMask = new boolean[segmentCount];
        if (spans != null && !spans.isEmpty()) {
            Map<Long, Integer> indexMap = new HashMap<>();
            for (int i = 0; i < centers.size(); i++) {
                indexMap.put(centers.get(i).asLong(), i);
            }
            for (RoadSpan span : spans) {
                if (span.type() != SpanType.BRIDGE) {
                    continue;
                }
                Integer startIndex = indexMap.get(span.start().asLong());
                Integer endIndex = indexMap.get(span.end().asLong());
                if (startIndex == null || endIndex == null) {
                    continue;
                }
                int min = Math.max(0, Math.min(startIndex, endIndex));
                int max = Math.min(segmentCount - 1, Math.max(startIndex, endIndex));
                for (int index = min; index <= max; index++) {
                    bridgeMask[index] = true;
                }
            }
        }

        int averagingRadius = Math.max(0, generationConfig.averagingRadius());
        int[] baseHeights = new int[segmentCount];
        for (int i = 0; i < segmentCount; i++) {
            int sum = 0;
            int count = 0;
            int min = Math.max(0, i - averagingRadius);
            int max = Math.min(segmentCount - 1, i + averagingRadius);
            for (int index = min; index <= max; index++) {
                sum += centers.get(index).getY();
                count++;
            }
            baseHeights[i] = count > 0 ? (int) Math.round(sum / (double) count) : centers.get(i).getY();
        }

        if (!generationConfig.slopeLimitEnabled()) {
            List<Integer> result = new ArrayList<>(segmentCount);
            for (int value : baseHeights) {
                result.add(value);
            }
            return result;
        }

        int slopeRunBlocks = Math.max(1, generationConfig.slopeRunBlocks());
        int slopeRiseBlocks = Math.max(0, generationConfig.slopeRiseBlocks());
        int[] smoothed = HighwayHeightSmoother.smooth(
                baseHeights,
                centers,
                bridgeMask,
                slopeRunBlocks,
                slopeRiseBlocks);

        List<Integer> result = new ArrayList<>(segmentCount);
        for (int value : smoothed) {
            result.add(value);
        }
        return result;
    }
}
