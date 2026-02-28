package net.shiroha233.roadweaver.features.path.pathlogic.core;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.PresetService;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.features.path.decoration.system.SurfacePlacementUtil;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.RoadHeightInterpolator;

import java.util.List;

/**
 * 路面铺设器
 * 
 * 职责：铺设道路路面，每个方块根据其在中心线上的投影位置独立计算高度
 */
public final class SegmentPaver {
    private SegmentPaver() {}

    public static void paveSegment(WorldGenLevel world,
            RoadSegmentPlacement seg,
            int segmentIndex,
            List<BlockPos> centers,
            int[] targetY,
            int roadType,
            List<BlockState> materials,
            List<BlockState> slabMaterials,
            RandomSource random,
            ModConfig cfg) {
        List<BlockPos> positions = seg.positions();
        if (positions.isEmpty())
            return;

        int[] heights = RoadHeightInterpolator.batchInterpolate(positions, segmentIndex, centers, targetY);

        for (int i = 0; i < positions.size(); i++) {
            BlockPos widthBlock = positions.get(i);
            int y = heights[i];
            BlockPos pos = new BlockPos(widthBlock.getX(), y, widthBlock.getZ());

            if (StructureAvoidanceService.shouldAvoid(world, pos)) {
                continue;
            }

            List<BlockState> baseMats;
            if (roadType == 1) {
                PresetService.PresetDef biomePreset = PresetService.findNaturalPresetForBiome(
                        world.getLevel().dimension().location(),
                        world.getBiome(pos).unwrapKey().map(k -> k.location()).orElse(null));
                if (biomePreset != null) {
                    baseMats = PresetService.toBlockStatesFromIdsAllowEmpty(biomePreset.materials());
                } else {
                    baseMats = net.shiroha233.roadweaver.features.path.decoration.material.BiomeRoadMaterialSelector
                            .forBiome(world, pos);
                }
            } else if (materials != null && !materials.isEmpty()) {
                baseMats = materials;
            } else {
                baseMats = List.of();
            }

            SurfacePlacementUtil.placeOnSurface(world, pos, baseMats, 0, random, cfg);

            List<BlockState> slabs;
            if (roadType == 0 || roadType == 3) {
                slabs = slabMaterials;
            } else if (roadType == 1) {
                PresetService.PresetDef biomePreset = PresetService.findNaturalPresetForBiome(
                        world.getLevel().dimension().location(),
                        world.getBiome(pos).unwrapKey().map(k -> k.location()).orElse(null));
                slabs = biomePreset != null ? PresetService.toBlockStatesFromIdsAllowEmpty(biomePreset.slabMaterials())
                        : List.of();
            } else {
                slabs = List.of();
            }

            if (slabs != null && !slabs.isEmpty()) {
                if (shouldPlaceSlab(widthBlock.getX(), widthBlock.getZ(), y, centers, targetY)) {
                    BlockState slabState = slabs.get(random.nextInt(slabs.size()));
                    if (slabState.getBlock() instanceof SlabBlock) {
                        slabState = slabState.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                    }
                    world.setBlock(pos, slabState, 3);
                }
            }
        }
    }

    private static boolean shouldPlaceSlab(int x, int z, int currentY,
            List<BlockPos> centers, int[] targetY) {
        int yAhead = RoadHeightInterpolator.getInterpolatedY(x + 1, z, centers, targetY);
        int yBehind = RoadHeightInterpolator.getInterpolatedY(x - 1, z, centers, targetY);
        int yLeft = RoadHeightInterpolator.getInterpolatedY(x, z + 1, centers, targetY);
        int yRight = RoadHeightInterpolator.getInterpolatedY(x, z - 1, centers, targetY);

        boolean needsSlabX = (yAhead > currentY && yBehind >= currentY)
                || (yBehind > currentY && yAhead >= currentY);
        boolean needsSlabZ = (yLeft > currentY && yRight >= currentY)
                || (yRight > currentY && yLeft >= currentY);

        return needsSlabX || needsSlabZ;
    }
}
