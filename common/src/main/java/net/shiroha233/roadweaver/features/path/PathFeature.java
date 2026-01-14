package net.shiroha233.roadweaver.features.path;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.phys.Vec3;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.decoration.base.Decoration;
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationExecutor;
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationPlanner;
import net.shiroha233.roadweaver.features.path.decoration.system.SkippedBridgeBankSignPlanner;
import net.shiroha233.roadweaver.features.path.bridge.BuoyBuilder;
import net.shiroha233.roadweaver.features.path.bridge.BuoyMarkerPlanner;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeContextCache;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeRangeCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeSegmentPlannerNew;
import net.shiroha233.roadweaver.features.path.pathlogic.core.SegmentPaver;
import net.shiroha233.roadweaver.features.path.pathlogic.core.StructureAvoidanceService;
import net.shiroha233.roadweaver.features.path.pathlogic.surface.BridgeTransitionAdjuster;
import net.shiroha233.roadweaver.features.path.pathlogic.surface.RealTimeHeightCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.surface.RoadHeightCache;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.util.Line;

import java.util.*;

/**
 * 道路世界生成 Feature
 * 核心职责：在区块生成阶段放置道路、桥梁和浮标
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
        List<Records.RoadData> roadDataList = RoadShardStorage.queryRect(server, minX, minZ, maxX, maxZ);
        if (roadDataList == null || roadDataList.isEmpty())
            return false;

        RandomSource random = ctx.random();

        Set<BlockPos> processedMiddle = new HashSet<>();
        Set<Decoration> decorations = new HashSet<>();
        for (Records.RoadData data : roadDataList) {
            processRoadDataInChunk(world, server, currentChunk, data, processedMiddle, 
                    decorations, random, cfg);
        }
        DecorationExecutor.tryPlaceDecorations(decorations);
        return true;
    }

    private static void processRoadDataInChunk(WorldGenLevel world,
            ServerLevel server,
            ChunkPos currentChunk,
            Records.RoadData data,
            Set<BlockPos> processedMiddle,
            Set<Decoration> decorations,
            RandomSource random,
            ModConfig cfg) {
        String dimId = server.dimension().location().toString();
        if (!cfg.roadsEnabledForDimension(dimId)) return;

        boolean bridgeEnabled = cfg.bridgeEnabledForDimension(dimId);
        boolean roadFillEnabled = cfg.roadFillEnabledForDimension(dimId);
        boolean interpolatedRoadbedFillEnabled = cfg.interpolatedRoadbedFillEnabledForDimension(dimId);
        int roadType = data.roadType();
        if (roadType != 0 && roadType != 1) return;
        
        int roadWidth = Math.max(1, data.width());
        List<BlockState> materials = data.materials();
        List<BlockState> slabMaterials = data.slabMaterials();
        List<Records.RoadSegmentPlacement> segments = data.roadSegmentList();
        if (segments == null || segments.size() < 5) return;

        List<BlockPos> middlePositions = segments.stream()
                .map(Records.RoadSegmentPlacement::middlePos).toList();
        
        // 计算桥梁区间 - 优先使用实时检测，兼容旧的预计算数据
        BridgeRangeCalculator.RangeResult res;
        List<Records.RoadSpan> spans = data.spans();
        if (spans != null && !spans.isEmpty()) {
            // 使用预计算的 spans 数据（兼容旧数据）
            res = BridgeRangeCalculator.compute(middlePositions, spans, cfg, dimId);
        } else {
            // 使用实时检测模式（1.20.1 新架构）
            res = BridgeRangeCalculator.computeRealTime(world, middlePositions, roadWidth, cfg, dimId);
        }
        boolean[] isBridge = res.isBridge();
        List<int[]> bridgeRanges = res.mergedRanges();
        boolean[] skipSegments = res.skipSegments();

        // 构建桥梁段映射
        BridgeSegment bridgeSegment = new BridgeSegment(isBridge, segments, bridgeRanges);
        
        // 生成道路指纹（基于道路首尾坐标，确保跨区块一致）
        BlockPos firstPos = middlePositions.get(0);
        BlockPos lastPos = middlePositions.get(middlePositions.size() - 1);
        long roadFingerprint = BridgeContextCache.generateRoadFingerprint(
                firstPos.getX(), firstPos.getZ(), lastPos.getX(), lastPos.getZ());

        boolean useBuoysInstead = bridgeEnabled && cfg.bridgeUseBuoysInstead();
        boolean useBuoysWhenSkipped = bridgeEnabled && cfg.bridgeUseBuoysWhenSkipped();

        int intervalBlocks = Math.max(4, cfg.buoyIntervalBlocks());
        boolean[] buoyMarkersForBridge = useBuoysInstead
                ? BuoyMarkerPlanner.markersForBridgeRanges(middlePositions, bridgeRanges, intervalBlocks)
                : null;
        boolean[] buoyMarkersForSkipped = useBuoysWhenSkipped
                ? BuoyMarkerPlanner.markersForMask(middlePositions, skipSegments, intervalBlocks)
                : null;

        // 实时高度计算 - 使用 RealTimeHeightCalculator 获取真实地形高度
        int[] baseYArr = RealTimeHeightCalculator.calculateHeights(
                world, server, middlePositions, currentChunk, cfg);

        // 桥梁过渡调整
        if (baseYArr != null && bridgeEnabled && !useBuoysInstead && !bridgeRanges.isEmpty()) {
            baseYArr = BridgeTransitionAdjuster.adjust(baseYArr, bridgeRanges, cfg);
        }

        int deckY = server.getSeaLevel() + cfg.bridgeDeckClearance();
        
        // 预计算每座桥梁的高度（基于端点道路高度）
        Map<Integer, Integer> bridgeDeckYCache = new HashMap<>();
        for (int[] range : bridgeRanges) {
            int startIdx = range[0];
            int endIdx = range[1];
            
            // 获取桥梁两端的道路高度
            int startY = (baseYArr != null && startIdx > 0 && startIdx - 1 < baseYArr.length) 
                    ? baseYArr[startIdx - 1] : deckY;
            int endY = (baseYArr != null && endIdx + 1 < baseYArr.length) 
                    ? baseYArr[endIdx + 1] : deckY;
            
            // 桥梁高度取两端较大值，确保不低于海平面
            int bridgeY = Math.max(Math.max(startY, endY), server.getSeaLevel() + cfg.bridgeDeckClearance());
            
            // 缓存这座桥的高度
            for (int idx = startIdx; idx <= endIdx; idx++) {
                bridgeDeckYCache.put(idx, bridgeY);
            }
        }
        
        for (int i = 2; i < segments.size() - 2; i++) {
            BlockPos middle = middlePositions.get(i);
            ChunkPos middleChunk = new ChunkPos(middle);
            if (!middleChunk.equals(currentChunk)) continue;

            if (!processedMiddle.add(middle)) continue;

            int segmentIndex = i;
            if (segmentIndex < 8 || segmentIndex > segments.size() - 8) continue;

            BlockPos prev = middlePositions.get(i - 2);
            BlockPos next = middlePositions.get(i + 2);

            int sea = server.getSeaLevel();
            int motion = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 
                    middle.getX(), middle.getZ());
            int surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 
                    middle.getX(), middle.getZ());
            int topYCenter = (motion > sea + 2) ? motion : surface;
            BlockPos averaged = new BlockPos(middle.getX(), topYCenter, middle.getZ());
            int baseYForThis = (baseYArr != null && i < baseYArr.length) ? baseYArr[i] : topYCenter;

            Records.RoadSegmentPlacement seg = segments.get(i);
            if (StructureAvoidanceService.shouldAvoid(world, middle)) continue;
            
            // 超长水域跨度：整段跳过生成
            if (skipSegments != null && i >= 0 && i < skipSegments.length && skipSegments[i]) {
                if (useBuoysWhenSkipped && buoyMarkersForSkipped != null 
                        && i < buoyMarkersForSkipped.length && buoyMarkersForSkipped[i]) {
                    BuoyBuilder.placeBuoy(world, middle, server.getSeaLevel(), random, cfg);
                }
                continue;
            }

            // 浮标模式：水域跨度不放桥也不铺路
            if (useBuoysInstead && isBridge != null && i >= 0 && i < isBridge.length && isBridge[i]) {
                if (buoyMarkersForBridge != null && i < buoyMarkersForBridge.length 
                        && buoyMarkersForBridge[i]) {
                    BuoyBuilder.placeBuoy(world, middle, server.getSeaLevel(), random, cfg);
                }
                continue;
            }

            // 桥梁处理 - 统一使用模板桥梁
            if (bridgeEnabled && isBridge[i]) {
                var lineInfo = bridgeSegment.getLineInfo(i);
                if (lineInfo != null && lineInfo.line.getTotalLength() > 3) {
                    // 使用道路指纹 + 桥梁区间索引生成稳定的桥梁ID
                    long bridgeId = BridgeContextCache.generateBridgeId(roadFingerprint, lineInfo.rangeIndex);
                    
                    // 使用预计算的桥梁高度（确保与道路对齐）
                    int bridgeDeckY = bridgeDeckYCache.getOrDefault(i, deckY);
                    
                    // 使用模板桥梁规划器（通过缓存确保跨区块一致性）
                    BridgeSegmentPlannerNew.processWithContext(
                            world, server, currentChunk, bridgeId, lineInfo.line, bridgeDeckY);
                    
                    // 缓存桥梁高度
                    RoadHeightCache.cachePlacedHeight(server, middle.getX(), middle.getZ(), bridgeDeckY);
                }
            } else {
                // 普通道路处理
                if (roadFillEnabled) {
                    if (interpolatedRoadbedFillEnabled) {
                        net.shiroha233.roadweaver.features.path.pathlogic.surface.RoadTerrainAdapter
                                .adaptWithInterpolation(world, middle, i, middlePositions, baseYArr, 
                                        roadWidth, random, cfg);
                    } else {
                        net.shiroha233.roadweaver.features.path.pathlogic.surface.RoadTerrainAdapter
                                .adaptWithoutInterpolation(world, middle, roadWidth, baseYForThis, 
                                        random, cfg);
                    }
                }

                SegmentPaver.paveSegment(world, seg, i, middlePositions, baseYArr, roadType, 
                        materials, slabMaterials, random, cfg);

                // 缓存已放置的道路高度（用于跨区块衔接）
                if (baseYArr != null && i < baseYArr.length) {
                    RoadHeightCache.cachePlacedHeight(server, middle.getX(), middle.getZ(), baseYArr[i]);
                }

                // 跨海被跳过时：在两端岸边放置提示路牌
                if (cfg.roadSignsEnabledForDimension(dimId)) {
                    SkippedBridgeBankSignPlanner.addIfSkippedBridgeBank(
                            world, decorations, averaged, next, prev, roadWidth, skipSegments, i);
                }
            }

            // 装饰（桥上可选保留路灯）
            if (!isBridge[i] || cfg.bridgeKeepLamps()) {
                DecorationPlanner.addDecoration(world, decorations, averaged, segmentIndex, 
                        next, prev, middlePositions, roadWidth, random, cfg,
                        (roadType == 0 ? DecorationPlanner.Mode.ARTIFICIAL : DecorationPlanner.Mode.NATURAL));
            }
        }
    }

    /** 桥梁线信息 */
    public static class BridgeLineInfo {
        public final Line line;
        public final int rangeIndex;
        public final int startSegmentIdx;
        public final int endSegmentIdx;
        
        public BridgeLineInfo(Line line, int rangeIndex, int startIdx, int endIdx) {
            this.line = line;
            this.rangeIndex = rangeIndex;
            this.startSegmentIdx = startIdx;
            this.endSegmentIdx = endIdx;
        }
    }

    /** 桥梁段映射，用于获取桥梁曲线和区间索引 */
    public static class BridgeSegment {
        private final Map<Integer, BridgeLineInfo> segmentToLineInfo = new HashMap<>();

        public BridgeSegment(boolean[] isBridge, List<Records.RoadSegmentPlacement> segments, 
                           List<int[]> bridgeRanges) {
            // 使用 bridgeRanges 来确定桥梁区间，确保跨区块一致性
            for (int rangeIdx = 0; rangeIdx < bridgeRanges.size(); rangeIdx++) {
                int[] range = bridgeRanges.get(rangeIdx);
                int startIdx = range[0];
                int endIdx = range[1];
                
                if (startIdx >= segments.size() || endIdx >= segments.size()) continue;
                if (startIdx > endIdx) continue;
                
                // 使用区间的起点和终点坐标构建 Line
                BlockPos startPos = segments.get(startIdx).middlePos();
                BlockPos endPos = segments.get(endIdx).middlePos();
                
                Vec3 start = new Vec3(startPos.getX(), 0, startPos.getZ());
                Vec3 end = new Vec3(endPos.getX(), 0, endPos.getZ());
                Line line = new Line(start, end);
                
                BridgeLineInfo info = new BridgeLineInfo(line, rangeIdx, startIdx, endIdx);
                
                // 将区间内所有段映射到同一个 LineInfo
                for (int i = startIdx; i <= endIdx; i++) {
                    segmentToLineInfo.put(i, info);
                }
            }
        }

        public BridgeLineInfo getLineInfo(int index) {
            return segmentToLineInfo.get(index);
        }
        
        public Line getLine(int index) {
            BridgeLineInfo info = segmentToLineInfo.get(index);
            return info != null ? info.line : null;
        }
    }
}
