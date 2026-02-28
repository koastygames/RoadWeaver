package net.shiroha233.roadweaver.features.path.pathlogic.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;

import java.util.List;

/**
 * 道路方块放置器：负责在世界中放置道路方块，包括路基填充和地形适配
 */
public final class RoadBlockPlacer {
    private RoadBlockPlacer() {
    }

    public static void placeRoadBlock(WorldGenLevel world,
            BlockState blockBelow,
            BlockPos surfacePos,
            List<BlockState> materials,
            RandomSource random,
            ModConfig cfg) {
        if (!PlacementRules.placeAllowedCheck(blockBelow.getBlock()))
            return;
        BlockState chosen = materials.get(random.nextInt(materials.size()));

        boolean roadFillEnabled = true;
        if (cfg != null) {
            String dimId = world.getLevel().dimension().location().toString();
            roadFillEnabled = cfg.roadFillEnabledForDimension(dimId);
        }

        final int MAX_CAUSEWAY_DEPTH = Math.max(0, Math.min(RoadConstants.CAUSEWAY_MAX_DEPTH_MAX, (cfg == null ? 1 : cfg.roadAppearance().causewayMaxDepth())));
        BlockPos below1 = surfacePos.below();
        BlockPos below2 = surfacePos.below(RoadConstants.BELOW_DEPTH_2);
        boolean sturdy1 = world.getBlockState(below1).isFaceSturdy(world, below1, Direction.UP);
        boolean sturdy2 = world.getBlockState(below2).isFaceSturdy(world, below2, Direction.UP);

        if (!roadFillEnabled) {
            world.setBlock(below1, chosen, RoadConstants.BLOCK_UPDATE_FLAG);
        } else if (!sturdy1 && !sturdy2) {
            BlockPos cursor = below2;
            int depth = 0;
            BlockPos base = null;
            while (cursor.getY() > world.getMinBuildHeight() && depth < MAX_CAUSEWAY_DEPTH) {
                if (world.getBlockState(cursor).isFaceSturdy(world, cursor, Direction.UP)) {
                    base = cursor;
                    break;
                }
                cursor = cursor.below();
                depth++;
            }

            BlockPos fillStart = (base != null) ? base.above()
                    : below1.below(
                            Math.min(MAX_CAUSEWAY_DEPTH - 1, Math.max(0, below1.getY() - world.getMinBuildHeight())));
            if (fillStart.getY() < world.getMinBuildHeight()) {
                fillStart = new BlockPos(fillStart.getX(), world.getMinBuildHeight(), fillStart.getZ());
            }
            BlockPos pos = fillStart;
            while (pos.getY() <= below1.getY()) {
                world.setBlock(pos, chosen, RoadConstants.BLOCK_UPDATE_FLAG);
                pos = pos.above();
            }
        } else {
            world.setBlock(below1, chosen, RoadConstants.BLOCK_UPDATE_FLAG);
        }

        AboveColumnClearer.clearAboveColumn(world, surfacePos, cfg);

        BlockPos belowPos1 = surfacePos.below(RoadConstants.BELOW_DEPTH_2);
        BlockState belowState1 = world.getBlockState(belowPos1);
        if (belowState1.is(Blocks.GRASS_BLOCK)) {
            world.setBlock(belowPos1, Blocks.DIRT.defaultBlockState(), RoadConstants.BLOCK_UPDATE_FLAG);
        }
    }
}
