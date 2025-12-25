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
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeSegmentPlannerNew;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationPlanner;
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationExecutor;
import net.shiroha233.roadweaver.features.path.bridge.BuoyBuilder;
import net.shiroha233.roadweaver.features.path.bridge.BuoyMarkerPlanner;
import net.shiroha233.roadweaver.features.path.decoration.system.SkippedBridgeBankSignPlanner;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeRangeCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.BridgeSegmentPlanner;
import net.shiroha233.roadweaver.features.path.pathlogic.core.SegmentPaver;
import net.shiroha233.roadweaver.features.path.pathlogic.core.StructureAvoidanceService;
import net.shiroha233.roadweaver.features.path.pathlogic.surface.BridgeTransitionAdjuster;
import net.shiroha233.roadweaver.features.path.pathlogic.surface.HeightProfileService;
import net.shiroha233.roadweaver.util.Curve;

import java.util.*;

public class PathFeature extends Feature<PathFeatureConfig> {
    public PathFeature(Codec<PathFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<PathFeatureConfig> ctx) {
        WorldGenLevel world = ctx.level();
        Level lvl = world.getLevel();
        if (!(lvl instanceof ServerLevel server)) return false;

        ModConfig cfg = ConfigService.get();
        String dimId = server.dimension().location().toString();
        // 按维度：是否生成道路（path 道路系统）
        if (!cfg.roadsEnabledForDimension(dimId)) return false;

        ChunkPos currentChunk = new ChunkPos(ctx.origin());
        int minX = currentChunk.getMinBlockX();
        int minZ = currentChunk.getMinBlockZ();
        int maxX = currentChunk.getMaxBlockX();
        int maxZ = currentChunk.getMaxBlockZ();
        List<Records.RoadData> roadDataList = RoadShardStorage.queryRect(server, minX, minZ, maxX, maxZ);
        if (roadDataList == null || roadDataList.isEmpty()) return false;

        RandomSource random = ctx.random();
        int averagingRadius = Math.max(0, cfg.averagingRadius());

        Set<BlockPos> processedMiddle = new HashSet<>();
        Set<Decoration> decorations = new HashSet<>();
        for (Records.RoadData data : roadDataList) {
            processRoadDataInChunk(world, server, currentChunk, data, processedMiddle, decorations, random, cfg, averagingRadius);
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
                                               ModConfig cfg,
                                               int averagingRadius) {
        String dimId = server.dimension().location().toString();
        // 按维度：是否生成道路（path 道路系统）
        if (!cfg.roadsEnabledForDimension(dimId)) {
            return;
        }

        boolean bridgeEnabled = cfg.bridgeEnabledForDimension(dimId);
        boolean roadFillEnabled = cfg.roadFillEnabledForDimension(dimId);
        boolean interpolatedRoadbedFillEnabled = cfg.interpolatedRoadbedFillEnabledForDimension(dimId);
        int roadType = data.roadType();
        if (roadType != 0 && roadType != 1) {
            return;
        }
        int roadWidth = Math.max(1, data.width());
        List<BlockState> materials = data.materials();
        List<BlockState> slabMaterials = data.slabMaterials();
        List<Records.RoadSegmentPlacement> segments = data.roadSegmentList();
        if (segments == null || segments.size() < 5) return;

        List<BlockPos> middlePositions = segments.stream().map(Records.RoadSegmentPlacement::middlePos).toList();
        BridgeRangeCalculator.RangeResult res = BridgeRangeCalculator.compute(middlePositions, data.spans(), cfg, dimId);
        boolean[] isBridge = res.isBridge();
        List<int[]> bridgeRanges = res.mergedRanges();
        boolean[] skipSegments = res.skipSegments();

        BridgeSegment bridgeSegment = new BridgeSegment(isBridge, segments);

        boolean useBuoysInstead = bridgeEnabled && cfg.bridgeUseBuoysInstead();
        boolean useBuoysWhenSkipped = bridgeEnabled && cfg.bridgeUseBuoysWhenSkipped();

        int intervalBlocks = Math.max(4, cfg.buoyIntervalBlocks());
        boolean[] buoyMarkersForBridge = (useBuoysInstead ? BuoyMarkerPlanner.markersForBridgeRanges(middlePositions, bridgeRanges, intervalBlocks) : null);
        boolean[] buoyMarkersForSkipped = (useBuoysWhenSkipped ? BuoyMarkerPlanner.markersForMask(middlePositions, skipSegments, intervalBlocks) : null);

        java.util.List<Integer> targetY = data.targetY();
        boolean slopeLimitEnabled = cfg.slopeLimitEnabledForDimension(dimId);
        HeightProfileService.HeightProfile hp = HeightProfileService.build(
                world,
                middlePositions,
                currentChunk,
                averagingRadius,
                slopeLimitEnabled,
                cfg.maxSlopeStepPerTwoSegments(),
                targetY
        );
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
            if (!processedMiddle.add(middle)) continue;
            segmentIndex++;
            if (segmentIndex < 8 || segmentIndex > segments.size() - 8) continue;
            ChunkPos middleChunk = new ChunkPos(middle);
            if (!middleChunk.equals(currentChunk)) continue;

            BlockPos prev = middlePositions.get(i - 2);
            BlockPos next = middlePositions.get(i + 2);

            int sea = server.getSeaLevel();
            int motion = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle.getX(), middle.getZ());
            int surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, middle.getX(), middle.getZ());
            int topYCenter = (motion > sea + 2) ? motion : surface;
            BlockPos averaged = new BlockPos(middle.getX(), topYCenter, middle.getZ());
            int baseYForThis = (baseYArr != null ? baseYArr[i] : topYCenter);

            Records.RoadSegmentPlacement seg = segments.get(i);
            if (StructureAvoidanceService.shouldAvoid(world, middle)) {
                continue;
            }
            if (skipSegments != null && i >= 0 && i < skipSegments.length && skipSegments[i]) {
                // 超长水域跨度：整段跳过生成
                if (useBuoysWhenSkipped && buoyMarkersForSkipped != null && i < buoyMarkersForSkipped.length && buoyMarkersForSkipped[i]) {
                    BuoyBuilder.placeBuoy(world, middle, server.getSeaLevel(), random, cfg);
                }
                continue;
            }

            if (useBuoysInstead && isBridge != null && i >= 0 && i < isBridge.length && isBridge[i]) {
                if (buoyMarkersForBridge != null && i < buoyMarkersForBridge.length && buoyMarkersForBridge[i]) {
                    BuoyBuilder.placeBuoy(world, middle, server.getSeaLevel(), random, cfg);
                }
                // 浮标模式：水域跨度不放桥也不铺路，避免在水里生成"路堤"
                continue;
            }

            if (bridgeEnabled && isBridge[i]) {
                var curve = bridgeSegment.getCurve(i);
                // 若桥曲线长度小于20，则沿用原桥生成方法
                if (curve == null || curve.getTotalLength() <= 20) {
                    BridgeSegmentPlanner.processSegment(world, seg, middle, prev, next, roadWidth, baseYForThis, deckY, segmentIndex, random, cfg, bridgeRanges, baseYArr, i, bridgeCtx);
                } else {
                    BridgeSegmentPlannerNew.processSegment(world, curve, seg, middle, prev);
                }
            } else {
                // 按维度：道路填充（路基/地形适配）与插值路基填充开关
                if (roadFillEnabled) {
                    if (interpolatedRoadbedFillEnabled) {
                        // 使用插值高度计算，确保与路面铺设的高度一致
                        net.shiroha233.roadweaver.features.path.pathlogic.surface.RoadTerrainAdapter.adaptWithInterpolation(
                                world, middle, i, middlePositions, baseYArr, roadWidth, random, cfg);
                    } else {
                        // 回退到旧的“按路段统一高度”的路基填充（不使用插值）
                        net.shiroha233.roadweaver.features.path.pathlogic.surface.RoadTerrainAdapter.adapt(
                                world, middle, roadWidth, baseYForThis, random, cfg);
                    }
                }

                SegmentPaver.paveSegment(world, seg, i, middlePositions, baseYArr, roadType, materials, slabMaterials, random, cfg);

                // 跨海被跳过（超长水域跨度）时：在两端岸边放置提示路牌
                // 仅在"岸边正常路段"触发一次；真正落地仍由 Decoration.placeAllowed 做表面与禁放判断
                if (cfg.roadSignsEnabledForDimension(dimId)) {
                    SkippedBridgeBankSignPlanner.addIfSkippedBridgeBank(
                            world,
                            decorations,
                            averaged,
                            next,
                            prev,
                            roadWidth,
                            skipSegments,
                            i
                    );
                }
            }

            if (!isBridge[i] || cfg.bridgeKeepLamps()) {
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
                        (roadType == 0 ? DecorationPlanner.Mode.ARTIFICIAL : DecorationPlanner.Mode.NATURAL)
                );
            }
            // 路边结构现在通过预计算系统在 STRUCTURE_STARTS 阶段注入
            // 参见 RoadsideStructurePrecomputer 和 StructureInjector
        }
    }

    public static class BridgeSegment {
        public final Map<Set<Integer>, Curve> curves = new HashMap<>();

        public BridgeSegment(boolean[] isBridge, List<Records.RoadSegmentPlacement> segments) {
            List<List<Vec3>> list1 = new ArrayList<>();
            List<Set<Integer>> list2 = new ArrayList<>();
            list1.add(new ArrayList<>());
            list2.add(new HashSet<>());
            for (int i = 0; i < segments.size(); i++) {
                if (isBridge[i]) {
                    list1.get(list1.size() - 1).add(segments.get(i).middlePos().getCenter());
                    list2.get(list1.size() - 1).add(i);
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
                // 删除过于密集的点
                Iterator<Vec3> iterator = seg.iterator();
                int index = 0;
                while (iterator.hasNext()) {
                    iterator.next();
                    if (index % 4 != 0 && index != seg.size() - 1) {
                        iterator.remove();
                    }
                    index++;
                }

                // 创建曲线
                var curve = new Curve();
                for (int j = 0; j < seg.size() - 2; j++) {
                    Vec3 a = seg.get(j);
                    Vec3 b = seg.get(j + 1);
                    Vec3 c = seg.get(j + 2);
                    Vec3 startAxis = b.subtract(a).normalize();
                    Vec3 endAxis = b.subtract(c).normalize();
                    curve.addSegment0(a, b, startAxis, endAxis);
                }
                curve.addLineSegment(seg.get(seg.size() - 2), seg.get(seg.size() - 1));
                curves.put(list2.get(i), curve);
            }
        }

        // 通过索引获取曲线
        public Curve getCurve(int index) {
            for (Map.Entry<Set<Integer>, Curve> setCurveEntry : curves.entrySet()) {
                var set = setCurveEntry.getKey();
                if (set.contains(index)) {
                    return setCurveEntry.getValue();
                }
            }
            return null;
        }
    }
}

