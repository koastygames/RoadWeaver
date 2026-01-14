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
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeRangeCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeSegmentPlanner;
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
        BridgeSegment bridgeSegment = new BridgeSegment(isBridge, segments);

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
        int segmentIndex = 0;
        BridgeSegmentPlanner.Context bridgeCtx = BridgeSegmentPlanner.newContext();
        
        for (int i = 2; i < segments.size() - 2; i++) {
            BlockPos middle = middlePositions.get(i);
            if (!processedMiddle.add(middle)) continue;
            
            segmentIndex++;
            if (segmentIndex < 8 || segmentIndex > segments.size() - 8) continue;
            
            ChunkPos middleChunk = new ChunkPos(middle);
            if (!middleChunk.equals(currentChunk)) continue;

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

            // 桥梁处理
            if (bridgeEnabled && isBridge[i]) {
                var line = bridgeSegment.getLine(i);
                int rampN = Math.max(0, cfg.bridgeRampSegments());
                boolean inRamp = false;
                if (rampN > 0 && bridgeRanges != null && !bridgeRanges.isEmpty()) {
                    for (int[] r : bridgeRanges) {
                        if (i >= r[0] && i <= r[1]) {
                            int dStart = i - r[0];
                            int dEnd = r[1] - i;
                            if (dStart < rampN || dEnd < rampN) {
                                inRamp = true;
                            }
                            break;
                        }
                    }
                }
                // 若桥曲线长度小于5，则沿用简单桥生成方法
                if (inRamp || line == null || line.getTotalLength() <= 5) {
                    BridgeSegmentPlanner.processSegment(world, seg, middle, prev, next, 
                            roadWidth, baseYForThis, deckY, segmentIndex, random, cfg, 
                            bridgeRanges, baseYArr, i, bridgeCtx);
                } else {
                    // 尝试使用模板桥梁规划器
                    boolean success = BridgeSegmentPlannerNew.processSegment(world, line, seg, middle, prev, deckY);
                    if (!success) {
                        // 模板不可用，回退到简单桥梁生成器
                        BridgeSegmentPlanner.processSegment(world, seg, middle, prev, next, 
                                roadWidth, baseYForThis, deckY, segmentIndex, random, cfg, 
                                bridgeRanges, baseYArr, i, bridgeCtx);
                    }
                }
                
                // 缓存桥梁高度（用于跨区块衔接）
                int bridgeHeight = bridgeCtx.lastBridgeDeckY != null ? bridgeCtx.lastBridgeDeckY : deckY;
                RoadHeightCache.cachePlacedHeight(server, middle.getX(), middle.getZ(), bridgeHeight);
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

    /** 桥梁段映射，用于获取桥梁曲线 */
    public static class BridgeSegment {
        public final Map<Set<Integer>, Line> bridgeLines = new HashMap<>();

        public BridgeSegment(boolean[] isBridge, List<Records.RoadSegmentPlacement> segments) {
            List<List<Vec3>> list1 = new ArrayList<>();
            List<Set<Integer>> list2 = new ArrayList<>();
            list1.add(new ArrayList<>());
            list2.add(new HashSet<>());
            
            for (int i = 0; i < segments.size(); i++) {
                if (isBridge[i]) {
                    list1.get(list1.size() - 1).add(segments.get(i).middlePos().getCenter());
                    list2.get(list2.size() - 1).add(i);
                } else {
                    if (!list1.get(list1.size() - 1).isEmpty()) {
                        list1.add(new ArrayList<>());
                        list2.add(new HashSet<>());
                    }
                }
            }
            if (list1.get(list1.size() - 1).isEmpty()) {
                list1.remove(list1.size() - 1);
                list2.remove(list2.size() - 1);
            }

            for (int i = 0; i < list1.size(); i++) {
                List<Vec3> seg = list1.get(i);
                if (seg.size() < 2) continue;
                var line = new Line(seg.get(0), seg.get(seg.size() - 1));
                bridgeLines.put(list2.get(i), line);
            }
        }

        public Line getLine(int index) {
            for (Map.Entry<Set<Integer>, Line> entry : bridgeLines.entrySet()) {
                if (entry.getKey().contains(index)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }
}
