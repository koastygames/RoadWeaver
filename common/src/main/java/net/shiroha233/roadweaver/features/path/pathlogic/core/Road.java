package net.shiroha233.roadweaver.features.path.pathlogic.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.PresetService;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.RoadPathCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.RoadPathCalculator.PathCalculationResult;
import net.shiroha233.roadweaver.pathfinding.PathSpanExtractor;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.structures.precompute.RoadsideStructurePrecomputer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责生成普通道路并写入持久化道路数据。
 */
public final class Road {
    private final ServerLevel level;
    private final StructureConnection connection;
    private final PathFeatureConfig featureConfig;
    private final RoadGenerationConfig generationConfig;

    public Road(ServerLevel level,
            StructureConnection connection,
            PathFeatureConfig featureConfig,
            RoadGenerationConfig generationConfig) {
        this.level = level;
        this.connection = connection;
        this.featureConfig = featureConfig;
        this.generationConfig = generationConfig;
    }

    @Deprecated
    public Road(ServerLevel level, StructureConnection connection, PathFeatureConfig config) {
        this(level, connection, config, RoadGenerationConfig.from(ConfigService.get()));
    }

    public RoadData generateRoad(int maxSteps) {
        if (Thread.currentThread().isInterrupted()) {
            return null;
        }
        RandomSource random = RandomSource.create();
        int width = generationConfig.effectiveRoadWidth(getRandomWidth(random, featureConfig));
        boolean allowArtificial = generationConfig.allowArtificial();
        boolean allowNatural = generationConfig.allowNatural();
        if (!allowArtificial && !allowNatural) {
            return null;
        }

        int roadType = allowArtificial && allowNatural ? (random.nextBoolean() ? 0 : 1) : (allowArtificial ? 0 : 1);
        List<BlockState> materials;
        List<BlockState> slabMaterials;
        PresetService.RoadType presetType = roadType == 0
                ? PresetService.RoadType.ARTIFICIAL
                : PresetService.RoadType.NATURAL;
        Identifier dimensionId = level.dimension().identifier();

        if (presetType == PresetService.RoadType.ARTIFICIAL) {
            PresetService.PresetDef preset = PresetService.choosePreset(random, dimensionId, presetType);
            materials = PresetService.toBlockStatesFromIds(preset.materials());
            slabMaterials = PresetService.toBlockStatesFromIds(preset.slabMaterials());
        } else {
            materials = List.of();
            slabMaterials = List.of();
        }

        BlockPos rawStart = connection.from();
        BlockPos rawEnd = connection.to();
        TerrainSamplingCache cache = new TerrainSamplingCache();
        try {
            PathCalculationResult pathResult = RoadPathCalculator.calculateAStarRoadPathDetailed(
                    rawStart,
                    rawEnd,
                    width,
                    level,
                    maxSteps,
                    cache,
                    generationConfig);
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            List<RoadSegmentPlacement> rawSegments = pathResult.segments();
            if (rawSegments == null || rawSegments.size() < 5) {
                return null;
            }

            List<RoadSegmentPlacement> segments = StructureRoadOffsetService.trimPathNearStructure(
                    level,
                    rawSegments,
                    rawStart,
                    rawEnd);
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            if (segments == null || segments.size() < 5) {
                return null;
            }

            PathTerrainField terrain = pathResult.terrain();
            List<RoadSpan> spans = PathSpanExtractor.extractSpans(
                    segments,
                    level,
                    cache,
                    terrain,
                    generationConfig.bridgeMinWaterDepth());
            List<Integer> targetY = computeTargetY(segments, spans);
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            long ownerA2d = PlanningUtils.pos2dKey(rawStart);
            long ownerB2d = PlanningUtils.pos2dKey(rawEnd);

            RoadData roadData = new RoadData(
                    width,
                    roadType,
                    materials,
                    slabMaterials,
                    segments,
                    spans,
                    targetY,
                    ownerA2d,
                    ownerB2d);
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            RoadShardStorage.addRoad(level, roadData);
            RoadsideStructurePrecomputer.precomputeStructures(level, segments, spans, width, cache, random, targetY);
            return roadData;
        } finally {
            cache.clear();
        }
    }

    private static int getRandomWidth(RandomSource random, PathFeatureConfig config) {
        return 0;
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
                BlockPos pos = centers.get(index);
                sum += pos.getY();
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

        int[] smoothed = baseHeights.clone();
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

            int stepPerTwoSegments = Math.max(0, Math.min(8, generationConfig.maxSlopeStepPerTwoSegments()));
            int firstStepLimit = Math.max(0, stepPerTwoSegments / 2);
            int followingStepLimit = Math.max(0, (stepPerTwoSegments + 1) / 2);

            for (int index = startIndex + 1; index <= endIndex; index++) {
                int y = smoothed[index];
                if (index == startIndex + 1) {
                    int prevY = smoothed[index - 1];
                    if (y > prevY + firstStepLimit) {
                        y = prevY + firstStepLimit;
                    }
                    if (y < prevY - firstStepLimit) {
                        y = prevY - firstStepLimit;
                    }
                } else {
                    int prevY = smoothed[index - 1];
                    if (y > prevY + followingStepLimit) {
                        y = prevY + followingStepLimit;
                    }
                    if (y < prevY - followingStepLimit) {
                        y = prevY - followingStepLimit;
                    }
                    int prevPrevY = smoothed[index - 2];
                    int maxY = prevPrevY + stepPerTwoSegments;
                    int minY = prevPrevY - stepPerTwoSegments;
                    if (y > maxY) {
                        y = maxY;
                    }
                    if (y < minY) {
                        y = minY;
                    }
                }
                smoothed[index] = y;
            }

            for (int index = endIndex - 1; index >= startIndex; index--) {
                int y = smoothed[index];
                if (index == endIndex - 1) {
                    int nextY = smoothed[index + 1];
                    if (y > nextY + firstStepLimit) {
                        y = nextY + firstStepLimit;
                    }
                    if (y < nextY - firstStepLimit) {
                        y = nextY - firstStepLimit;
                    }
                } else {
                    int nextY = smoothed[index + 1];
                    if (y > nextY + followingStepLimit) {
                        y = nextY + followingStepLimit;
                    }
                    if (y < nextY - followingStepLimit) {
                        y = nextY - followingStepLimit;
                    }
                    int nextNextY = smoothed[index + 2];
                    int maxY = nextNextY + stepPerTwoSegments;
                    int minY = nextNextY - stepPerTwoSegments;
                    if (y > maxY) {
                        y = maxY;
                    }
                    if (y < minY) {
                        y = minY;
                    }
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
