package net.shiroha233.roadweaver.features.roadlogic.core;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.features.decoration.system.SurfacePlacementUtil;
import net.shiroha233.roadweaver.helpers.Records;

import java.util.List;

public final class SegmentPaver {
    private SegmentPaver() {}

    public static void paveSegment(WorldGenLevel world,
                                   Records.RoadSegmentPlacement seg,
                                   int averageY,
                                   int roadType,
                                   List<BlockState> materials,
                                   List<BlockState> slabMaterials,
                                   boolean useSlab,
                                   RandomSource random,
                                   ModConfig cfg) {
        for (BlockPos widthBlock : seg.positions()) {
            BlockPos pos = new BlockPos(widthBlock.getX(), averageY, widthBlock.getZ());

            // 1. 先铺基础整方块道路（保持原有支撑逻辑）
            List<BlockState> baseMats;
            if (roadType == 1) {
                baseMats = net.shiroha233.roadweaver.features.decoration.material.surface.BiomeRoadMaterialSelector.forBiome(world, pos);
            } else {
                baseMats = materials;
            }
            SurfacePlacementUtil.placeOnSurface(world, pos, baseMats, 0, random, cfg);

            // 2. 如需平滑过渡，则在基础路面上额外覆盖一层 slab（下半砖），不破坏下面的整方块支撑
            if (roadType == 0 && useSlab && slabMaterials != null && !slabMaterials.isEmpty()) {
                BlockState slabState = slabMaterials.get(random.nextInt(slabMaterials.size()));
                if (slabState.getBlock() instanceof SlabBlock) {
                    slabState = slabState.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                }
                // pos 本身在 SurfacePlacementUtil 中被视作“路面位置”，下面一格是实际整方块道路
                // 这里在 pos 这一层放置 slab，实现“在原基础上多覆盖一层”的效果
                world.setBlock(pos, slabState, 3);
            }
        }
    }
}
