package net.shiroha233.roadweaver.features.longdrive.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.features.longdrive.config.LongDriveGenerationConfig;
import net.shiroha233.roadweaver.features.longdrive.pathfinding.GreedyForwardPathfinder;
import net.shiroha233.roadweaver.pathfinding.PathResult;
import net.shiroha233.roadweaver.pathfinding.PathSpanExtractor;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.impl.PathPostProcessor;
import net.shiroha233.roadweaver.pathfinding.impl.SplineHelper;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainFieldFactory;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责生成长途主干道道路数据并写入持久化存储。
 */
public final class LongDriveRoad {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private final ServerLevel level;
    private final BlockPos start;
    private final double directionX;
    private final double directionZ;
    private final LongDriveGenerationConfig generationConfig;

    public LongDriveRoad(ServerLevel level,
            BlockPos start,
            double directionX,
            double directionZ,
            LongDriveGenerationConfig generationConfig) {
        this.level = level;
        this.start = start;
        this.directionX = directionX;
        this.directionZ = directionZ;
        this.generationConfig = generationConfig;
    }

    public BlockPos generate(int maxSteps) {
        if (level == null || generationConfig == null) {
            return null;
        }

        int width = Math.max(1, generationConfig.roadWidth());
        List<BlockState> materials = List.of(Blocks.GRAY_CONCRETE.defaultBlockState());
        List<BlockState> slabMaterials = List.of(Blocks.GRAY_CONCRETE.defaultBlockState());

        TerrainSamplingCache cache = new TerrainSamplingCache();
        try {
            cache.enableHighPrecision(level);
            GreedyForwardPathfinder pathfinder = new GreedyForwardPathfinder();
            PathResult coarseResult = pathfinder.findPath(
                    start,
                    directionX,
                    directionZ,
                    maxSteps,
                    width,
                    level,
                    cache,
                    generationConfig.pathfindingCost(),
                    generationConfig.directionBias());
            if (!coarseResult.success() || coarseResult.segments().size() < 3) {
                return null;
            }

            List<BlockPos> coarsePath = coarseResult.segments().stream()
                    .map(RoadSegmentPlacement::middlePos)
                    .toList();
            PathTerrainField terrain = PathTerrainFieldFactory.quantized(
                    level,
                    cache,
                    coarsePath,
                    Math.max(1, generationConfig.pathfindingCost().effectiveAStarStep()),
                    generationConfig.pathfindingCost().quantizedSamplingChunkRadius());
            PathResult result = terrain == null
                    ? coarseResult
                    : pathfinder.findPath(
                            start,
                            directionX,
                            directionZ,
                            maxSteps,
                            width,
                            level,
                            cache,
                            terrain,
                            generationConfig.pathfindingCost(),
                            generationConfig.directionBias());
            if (!result.success() || result.segments().size() < 3) {
                return null;
            }

            List<BlockPos> rawPath = result.segments().stream()
                    .map(RoadSegmentPlacement::middlePos)
                    .toList();
            List<RoadSegmentPlacement> segments = PathPostProcessor.process(
                    rawPath,
                    width,
                    level,
                    cache,
                    terrain,
                    generationConfig.bridgeMinWaterDepth(),
                    SplineHelper.CurveMode.CATMULL_ROM,
                    null);
            if (segments == null || segments.size() < 3) {
                return null;
            }

            List<RoadSpan> spans = extractSpans(segments, terrain, cache);
            List<Integer> targetY = computeTargetY(segments, spans);

            RoadData roadData = new RoadData(
                    width,
                    LongDriveRoadTypes.LONG_DRIVE,
                    materials,
                    slabMaterials,
                    segments,
                    spans,
                    targetY,
                    RoadData.NO_OWNER_2D,
                    RoadData.NO_OWNER_2D);
            RoadShardStorage.addRoad(level, roadData);
            return segments.get(segments.size() - 1).middlePos();
        } catch (Throwable throwable) {
            LOGGER.warn("LongDriveRoad: generation failed from {}", start, throwable);
            return null;
        } finally {
            cache.clear();
        }
    }

    private List<RoadSpan> extractSpans(List<RoadSegmentPlacement> segments,
            PathTerrainField terrain,
            TerrainSamplingCache cache) {
        return PathSpanExtractor.extractSpans(
                segments,
                level,
                cache,
                terrain,
                generationConfig.bridgeMinWaterDepth());
    }

    private List<Integer> computeTargetY(List<RoadSegmentPlacement> segments, List<RoadSpan> spans) {
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
            baseHeights[i] = count > 0 ? Math.round(sum / (float) count) : centers.get(i).getY();
        }

        if (!generationConfig.slopeLimitEnabled()) {
            List<Integer> result = new ArrayList<>(segmentCount);
            for (int value : baseHeights) {
                result.add(value);
            }
            return result;
        }

        int[] smoothed = baseHeights.clone();
        int stepPerTwoSegments = Math.max(0, Math.min(8, generationConfig.maxSlopeStepPerTwoSegments()));
        int firstStepLimit = Math.max(0, stepPerTwoSegments / 2);
        int followingStepLimit = Math.max(0, (stepPerTwoSegments + 1) / 2);

        int cursor = 0;
        while (cursor < segmentCount) {
            while (cursor < segmentCount && bridgeMask[cursor]) {
                cursor++;
            }
            int startIndex = cursor;
            while (cursor < segmentCount && !bridgeMask[cursor]) {
                cursor++;
            }
            int endIndex = cursor - 1;
            if (startIndex > endIndex) {
                continue;
            }

            for (int index = startIndex + 1; index <= endIndex; index++) {
                int y = smoothed[index];
                int prevY = smoothed[index - 1];
                int limit = index == startIndex + 1 ? firstStepLimit : followingStepLimit;
                if (y > prevY + limit) {
                    y = prevY + limit;
                }
                if (y < prevY - limit) {
                    y = prevY - limit;
                }
                if (index >= startIndex + 2) {
                    int prevPrevY = smoothed[index - 2];
                    y = Math.max(prevPrevY - stepPerTwoSegments, Math.min(prevPrevY + stepPerTwoSegments, y));
                }
                smoothed[index] = y;
            }

            for (int index = endIndex - 1; index >= startIndex; index--) {
                int y = smoothed[index];
                int nextY = smoothed[index + 1];
                int limit = index == endIndex - 1 ? firstStepLimit : followingStepLimit;
                if (y > nextY + limit) {
                    y = nextY + limit;
                }
                if (y < nextY - limit) {
                    y = nextY - limit;
                }
                if (index <= endIndex - 2) {
                    int nextNextY = smoothed[index + 2];
                    y = Math.max(nextNextY - stepPerTwoSegments, Math.min(nextNextY + stepPerTwoSegments, y));
                }
                smoothed[index] = y;
            }
        }

        List<Integer> result = new ArrayList<>(segmentCount);
        for (int value : smoothed) {
            result.add(value);
        }
        return result;
    }
}
