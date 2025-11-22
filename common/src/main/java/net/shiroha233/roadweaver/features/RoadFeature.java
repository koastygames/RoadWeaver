package net.shiroha233.roadweaver.features;

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
import net.shiroha233.roadweaver.features.config.RoadFeatureConfig;
import net.shiroha233.roadweaver.features.decoration.base.Decoration;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.features.decoration.system.DecorationPlanner;
import net.shiroha233.roadweaver.features.decoration.system.DecorationExecutor;
import net.shiroha233.roadweaver.features.roadlogic.bridge.BridgeRangeCalculator;
import net.shiroha233.roadweaver.features.roadlogic.bridge.BridgeSegmentPlanner;
import net.shiroha233.roadweaver.features.roadlogic.core.SegmentPaver;
import net.shiroha233.roadweaver.features.roadlogic.surface.BridgeTransitionAdjuster;
import net.shiroha233.roadweaver.features.roadlogic.surface.HeightProfileService;

import java.util.*;

public class RoadFeature extends Feature<RoadFeatureConfig> {
    public RoadFeature(Codec<RoadFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<RoadFeatureConfig> ctx) {
        WorldGenLevel world = ctx.level();
        Level lvl = world.getLevel();
        if (!(lvl instanceof ServerLevel server)) return false;

        ChunkPos currentChunk = new ChunkPos(ctx.origin());
        int minX = currentChunk.getMinBlockX();
        int minZ = currentChunk.getMinBlockZ();
        int maxX = currentChunk.getMaxBlockX();
        int maxZ = currentChunk.getMaxBlockZ();
        List<Records.RoadData> roadDataList = RoadShardStorage.queryRect(server, minX, minZ, maxX, maxZ);
        if (roadDataList == null || roadDataList.isEmpty()) return false;

        // currentChunk already defined
        Set<BlockPos> processedMiddle = new HashSet<>();
        RandomSource random = ctx.random();
        ModConfig cfg = ConfigService.get();
        int averagingRadius = Math.max(0, cfg.averagingRadius());

        Set<Decoration> decorations = new HashSet<>();
        for (Records.RoadData data : roadDataList) {
            int roadType = data.roadType();
            int roadWidth = Math.max(1, data.width());
            List<BlockState> materials = data.materials();
            List<BlockState> slabMaterials = data.slabMaterials();
            List<Records.RoadSegmentPlacement> segments = data.roadSegmentList();
            if (segments == null || segments.size() < 5) continue;

            List<BlockPos> middlePositions = segments.stream().map(Records.RoadSegmentPlacement::middlePos).toList();
            boolean[] isBridge = new boolean[middlePositions.size()];
            List<int[]> bridgeRanges = new ArrayList<>();
            {
                BridgeRangeCalculator.RangeResult res = BridgeRangeCalculator.compute(middlePositions, data.spans());
                isBridge = res.isBridge();
                bridgeRanges = res.mergedRanges();
            }
            {
                java.util.List<Integer> targetY = data.targetY();
                HeightProfileService.HeightProfile hp = HeightProfileService.build(world, middlePositions, currentChunk, averagingRadius, cfg, targetY);
                boolean usePersisted = hp.usePersisted();
                int[] smoothedYArr = hp.smoothedY();
                int[] baseYArr;
                if (usePersisted && targetY != null && targetY.size() == middlePositions.size()) {
                    baseYArr = targetY.stream().mapToInt(Integer::intValue).toArray();
                } else {
                    baseYArr = smoothedYArr;
                }

                if (baseYArr != null && cfg.bridgeEnabled() && !bridgeRanges.isEmpty()) {
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

                    int topYCenter = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle.getX(), middle.getZ());
                    BlockPos averaged = new BlockPos(middle.getX(), topYCenter, middle.getZ());
                    int baseYForThis = (baseYArr != null ? baseYArr[i] : topYCenter);

                    Records.RoadSegmentPlacement seg = segments.get(i);
                    if (cfg.bridgeEnabled() && isBridge[i]) {
                        BridgeSegmentPlanner.processSegment(world, seg, middle, prev, next, roadWidth, baseYForThis, deckY, segmentIndex, random, cfg, bridgeRanges, baseYArr, i, bridgeCtx);
                    } else {
                        boolean useSlab = shouldUseSlabForSegment(baseYArr, i, roadType, slabMaterials);
                        SegmentPaver.paveSegment(world, seg, baseYForThis, roadType, materials, slabMaterials, useSlab, random, cfg);
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
                }
            }
        }
        DecorationExecutor.tryPlaceDecorations(decorations);
        return true;
    }

    private static boolean shouldUseSlabForSegment(int[] baseYArr,
                                                   int index,
                                                   int roadType,
                                                   java.util.List<BlockState> slabMaterials) {
        // 只在人工道路且预设中配置了 slab 材质时考虑使用 slab
        if (roadType != 0) return false;
        if (slabMaterials == null || slabMaterials.isEmpty()) return false;
        if (baseYArr == null) return false;
        if (index <= 0 || index >= baseYArr.length - 1) return false;

        int cur = baseYArr[index];
        int prev = baseYArr[index - 1];
        int next = baseYArr[index + 1];

        // 逻辑：只要当前位置放置半砖（+0.5高度）能缓解一侧的落差（即该侧比当前高），
        // 且不会导致另一侧“上不来”（即另一侧不比当前低，或者说是下坡/平路），就放置。
        // 1. 前方有坎 (next > cur)，且后方能走过来 (prev >= cur)
        boolean helpsNext = (next > cur) && (prev >= cur);
        // 2. 后方有坎 (prev > cur)，且前方能走回去 (next >= cur) -> 相当于下坡时的缓冲
        boolean helpsPrev = (prev > cur) && (next >= cur);

        return helpsNext || helpsPrev;
    }

}
