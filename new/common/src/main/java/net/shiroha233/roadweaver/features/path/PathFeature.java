package net.shiroha233.roadweaver.features.path;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.features.path.bridge.BridgeSegment;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.decoration.base.Decoration;
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationExecutor;
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationPlanner;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeRangeCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeSegmentPlanner;
import net.shiroha233.roadweaver.features.path.pathlogic.core.SegmentPaver;
import net.shiroha233.roadweaver.features.path.pathlogic.core.StructureAvoidanceService;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.BridgeTransitionAdjuster;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.HeightProfileService;
import net.shiroha233.roadweaver.features.path.pathlogic.surface.RoadTerrainAdapter;

import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BuoyBuilder;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BuoyMarkerPlanner;
import net.shiroha233.roadweaver.features.path.decoration.system.SkippedBridgeBankSignPlanner;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;

import java.util.*;

/**
 * 普通道路世界生成 Feature
 * 
 * 职责：在区块生成阶段读取持久化的道路数据并生成道路
 */
public class PathFeature extends Feature<PathFeatureConfig> {
    public PathFeature(Codec<PathFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<PathFeatureConfig> ctx) {
        WorldGenLevel world = ctx.level();
        Level lvl = world.getLevel();
        if (!(lvl instanceof ServerLevel server))
            return false;

        ModConfig cfg = ConfigService.get();
        String dimId = server.dimension().location().toString();
        
        if (!cfg.roadsEnabledForDimension(dimId))
            return false;

        ChunkPos currentChunk = new ChunkPos(ctx.origin());
        int minX = currentChunk.getMinBlockX();
        int minZ = currentChunk.getMinBlockZ();
        int maxX = currentChunk.getMaxBlockX();
        int maxZ = currentChunk.getMaxBlockZ();
        
        List<RoadData> roadDataList = RoadShardStorage.queryRect(server, minX, minZ, maxX, maxZ);
        if (roadDataList == null || roadDataList.isEmpty())
            return false;

        RandomSource random = ctx.random();
        int averagingRadius = Math.max(0, cfg.averagingRadius());

        Set<BlockPos> processedMiddle = new HashSet<>();
        Set<Decoration> decorations = new HashSet<>();
        
        for (RoadData data : roadDataList) {
            processRoadDataInChunk(world, server, currentChunk, data, processedMiddle, decorations, random, cfg,
                    averagingRadius);
        }
        
        DecorationExecutor.tryPlaceDecorations(decorations);
        return true;
    }

    private static void processRoadDataInChunk(WorldGenLevel world,
            ServerLevel server,
            ChunkPos currentChunk,
            RoadData data,
            Set<BlockPos> processedMiddle,
            Set<Decoration> decorations,
            RandomSource random,
            ModConfig cfg,
            int averagingRadius) {
        String dimId = server.dimension().location().toString();
        
        if (!cfg.roadsEnabledForDimension(dimId)) {
            return;
        }

        boolean bridgeEnabled = cfg.bridgeEnabledForDimension(dimId);
        boolean roadFillEnabled = cfg.roadFillEnabledForDimension(dimId);
        boolean interpolatedRoadbedFillEnabled = cfg.interpolatedRoadbedFillEnabledForDimension(dimId);
        
        int roadType = data.roadType();
        if (roadType != 0 && roadType != 1 && roadType != 3) {
            return;
        }
        
        int roadWidth = Math.max(1, data.width());
        var segments = data.roadSegmentList();
        if (segments == null || segments.size() < 5)
            return;

        var middlePositions = segments.stream().map(seg -> seg.middlePos()).toList();
        BridgeRangeCalculator.RangeResult res = BridgeRangeCalculator.compute(middlePositions, data.spans(), cfg,
                dimId);
        boolean[] isBridge = res.isBridge();
        List<int[]> bridgeRanges = res.mergedRanges();
        boolean[] skipSegments = res.skipSegments();

        new BridgeSegment(isBridge, segments);

        boolean useBuoysInstead = bridgeEnabled && cfg.bridgeUseBuoysInstead();
        boolean useBuoysWhenSkipped = bridgeEnabled && cfg.bridgeUseBuoysWhenSkipped();

        int intervalBlocks = Math.max(4, cfg.buoyIntervalBlocks());
        boolean[] buoyMarkersForBridge = (useBuoysInstead
                ? BuoyMarkerPlanner.markersForBridgeRanges(middlePositions, bridgeRanges, intervalBlocks)
                : null);
        boolean[] buoyMarkersForSkipped = (useBuoysWhenSkipped
                ? BuoyMarkerPlanner.markersForMask(middlePositions, skipSegments, intervalBlocks)
                : null);

        var targetY = data.targetY();
        boolean slopeLimitEnabled = cfg.slopeLimitEnabledForDimension(dimId);
        HeightProfileService.HeightProfile hp = HeightProfileService.build(
                world,
                middlePositions,
                currentChunk,
                averagingRadius,
                slopeLimitEnabled,
                cfg.maxSlopeStepPerTwoSegments(),
                targetY);
        boolean usePersisted = hp.usePersisted();
        int[] smoothedYArr = hp.smoothedY();
        int[] baseYArr;
        if (usePersisted && targetY != null && targetY.size() == middlePositions.size()) {
            baseYArr = targetY.stream().mapToInt(Integer::intValue).toArray();
        } else {
            baseYArr = smoothedYArr;
        }

        if (baseYArr != null && bridgeEnabled && !useBuoysInstead && !bridgeRanges.isEmpty()) {
            baseYArr = BridgeTransitionAdjuster.adjust(baseYArr, bridgeRanges, cfg);
        }

        int deckY = server.getSeaLevel() + cfg.bridgeDeckClearance();
        int segmentIndex = 0;
        BridgeSegmentPlanner.Context bridgeCtx = BridgeSegmentPlanner.newContext();
        
        for (int i = 2; i < segments.size() - 2; i++) {
            BlockPos middle = middlePositions.get(i);
            if (!processedMiddle.add(middle))
                continue;
            segmentIndex++;
            if (segmentIndex < 8 || segmentIndex > segments.size() - 8)
                continue;
            ChunkPos middleChunk = new ChunkPos(middle);
            if (!middleChunk.equals(currentChunk))
                continue;

            BlockPos prev = middlePositions.get(i - 2);
            BlockPos next = middlePositions.get(i + 2);

            int baseYForThis = (baseYArr != null ? baseYArr[i] : middle.getY());

            var seg = segments.get(i);
            if (StructureAvoidanceService.shouldAvoid(world, middle)) {
                continue;
            }
            
            if (skipSegments != null && i >= 0 && i < skipSegments.length && skipSegments[i]) {
                if (useBuoysWhenSkipped && buoyMarkersForSkipped != null && i < buoyMarkersForSkipped.length
                        && buoyMarkersForSkipped[i]) {
                    BuoyBuilder.placeBuoy(world, middle, server.getSeaLevel(), random, cfg);
                }
                continue;
            }

            if (useBuoysInstead && isBridge != null && i >= 0 && i < isBridge.length && isBridge[i]) {
                if (buoyMarkersForBridge != null && i < buoyMarkersForBridge.length && buoyMarkersForBridge[i]) {
                    BuoyBuilder.placeBuoy(world, middle, server.getSeaLevel(), random, cfg);
                }
                continue;
            }

            if (bridgeEnabled && isBridge[i]) {
                BridgeSegmentPlanner.processSegment(world, seg, middle, prev, next, roadWidth, baseYForThis, deckY,
                        segmentIndex, random, cfg, bridgeRanges, baseYArr, i, bridgeCtx);
            } else {
                if (roadFillEnabled) {
                    if (interpolatedRoadbedFillEnabled) {
                        RoadTerrainAdapter.adaptWithInterpolation(
                                world, middle, i, middlePositions, baseYArr, roadWidth, random, cfg);
                    } else {
                        RoadTerrainAdapter.adaptWithoutInterpolation(
                                world, middle, roadWidth, baseYForThis, random, cfg);
                    }
                }

                SegmentPaver.paveSegment(world, seg, i, middlePositions, baseYArr, roadType, data.materials(),
                        data.slabMaterials(),
                        random, cfg);

                if (cfg.roadSignsEnabledForDimension(dimId)) {
                    BlockPos averaged = new BlockPos(middle.getX(), baseYForThis, middle.getZ());
                    SkippedBridgeBankSignPlanner.addIfSkippedBridgeBank(
                            world,
                            decorations,
                            averaged,
                            next,
                            prev,
                            roadWidth,
                            skipSegments,
                            i);
                }
            }

            if (!isBridge[i] || cfg.bridgeKeepLamps()) {
                BlockPos averaged = new BlockPos(middle.getX(), baseYForThis, middle.getZ());
                DecorationPlanner.addDecoration(
                        world,
                        decorations,
                        averaged,
                        segmentIndex,
                        next,
                        prev,
                        middlePositions,
                        roadWidth,
                        random,
                        cfg,
                        (roadType == 0 ? DecorationPlanner.Mode.ARTIFICIAL : DecorationPlanner.Mode.NATURAL));
            }
        }
    }
}
