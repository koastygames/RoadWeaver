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
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.decoration.base.Decoration;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationPlanner;
import net.shiroha233.roadweaver.features.path.decoration.system.DecorationExecutor;
import net.shiroha233.roadweaver.features.path.bridge.BuoyBuilder;
import net.shiroha233.roadweaver.features.path.pathlogic.bridge.RealTimeWaterDetector;
import net.shiroha233.roadweaver.features.path.pathlogic.core.SegmentPaver;
import net.shiroha233.roadweaver.features.path.pathlogic.core.StructureAvoidanceService;
import net.shiroha233.roadweaver.features.path.pathlogic.surface.RealTimeHeightCalculator;
import net.shiroha233.roadweaver.features.path.pathlogic.surface.RoadHeightCache;

import java.util.*;

/**
 * 道路世界生成 Feature
 * 核心职责：在区块生成阶段放置道路和浮标
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
            processRoadDataInChunk(world, server, currentChunk, data, processedMiddle, decorations, random, cfg);
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
        
        // 实时高度计算
        int[] baseYArr = RealTimeHeightCalculator.calculateHeights(
                world, server, middlePositions, currentChunk, cfg);

        int seaLevel = server.getSeaLevel();
        int minWaterDepth = Math.max(1, 2); // 最小水深固定为2格
        
        // 浮标模式（水域上放浮标）
        int buoyInterval = Math.max(4, cfg.buoyIntervalBlocks());
        
        int segmentIndex = 0;
        for (int i = 2; i < segments.size() - 2; i++) {
            BlockPos middle = middlePositions.get(i);
            if (!processedMiddle.add(middle)) continue;
            
            segmentIndex++;
            if (segmentIndex < 8 || segmentIndex > segments.size() - 8) continue;
            
            ChunkPos middleChunk = new ChunkPos(middle);
            if (!middleChunk.equals(currentChunk)) continue;

            BlockPos prev = middlePositions.get(i - 2);
            BlockPos next = middlePositions.get(i + 2);

            int motion = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 
                    middle.getX(), middle.getZ());
            int surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 
                    middle.getX(), middle.getZ());
            int topYCenter = (motion > seaLevel + 2) ? motion : surface;
            BlockPos averaged = new BlockPos(middle.getX(), topYCenter, middle.getZ());
            int baseYForThis = (baseYArr != null && i < baseYArr.length) ? baseYArr[i] : topYCenter;

            Records.RoadSegmentPlacement seg = segments.get(i);
            if (StructureAvoidanceService.shouldAvoid(world, middle)) continue;
            
            // 检测是否在水域上（用于放置浮标）
            boolean isWater = RealTimeWaterDetector.shouldBeBridge(
                    world, middle.getX(), middle.getZ(), roadWidth, seaLevel, minWaterDepth);
            
            // 水域处理：放置浮标
            if (isWater) {
                // 按间隔放置浮标
                if (segmentIndex % Math.max(1, buoyInterval / 4) == 0) {
                    BuoyBuilder.placeBuoy(world, middle, seaLevel, random, cfg);
                }
                continue; // 水域上不铺路
            }

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
            
            // 缓存已放置的道路高度
            if (baseYArr != null && i < baseYArr.length) {
                RoadHeightCache.cachePlacedHeight(server, middle.getX(), middle.getZ(), baseYArr[i]);
            }

            // 装饰
            DecorationPlanner.addDecoration(world, decorations, averaged, segmentIndex, next, prev,
                    middlePositions, roadWidth, random, cfg,
                    (roadType == 0 ? DecorationPlanner.Mode.ARTIFICIAL : DecorationPlanner.Mode.NATURAL));
        }
    }
}
