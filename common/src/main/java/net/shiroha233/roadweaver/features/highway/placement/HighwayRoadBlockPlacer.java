package net.shiroha233.roadweaver.features.highway.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;

import java.util.List;

/**
 * Highway 方块放置器：负责路面方块、简单路基填充与清障。
 */
public final class HighwayRoadBlockPlacer {
    private HighwayRoadBlockPlacer() {}

    public static void placeRoadBlock(WorldGenLevel world,
                                      BlockState blockBelow,
                                      BlockPos surfacePos,
                                      List<BlockState> materials,
                                      RandomSource random,
                                      ModConfig cfg) {
        if (materials == null || materials.isEmpty()) return;
        if (!HighwayPlacementRules.placeAllowedCheck(blockBelow.getBlock())) return;

        BlockState chosen = materials.get(random.nextInt(materials.size()));

        final int maxCausewayDepth = Math.max(0, Math.min(12, (cfg == null ? 1 : cfg.causewayMaxDepth())));
        BlockPos below1 = surfacePos.below();
        BlockPos below2 = surfacePos.below(2);
        boolean sturdy1 = world.getBlockState(below1).isFaceSturdy(world, below1, Direction.UP);
        boolean sturdy2 = world.getBlockState(below2).isFaceSturdy(world, below2, Direction.UP);

        if (!sturdy1 && !sturdy2) {
            BlockPos cursor = below2;
            int depth = 0;
            BlockPos base = null;
            while (cursor.getY() > world.getMinBuildHeight() && depth < maxCausewayDepth) {
                if (world.getBlockState(cursor).isFaceSturdy(world, cursor, Direction.UP)) {
                    base = cursor;
                    break;
                }
                cursor = cursor.below();
                depth++;
            }

            BlockPos fillStart = (base != null)
                    ? base.above()
                    : below1.below(Math.min(maxCausewayDepth - 1, Math.max(0, below1.getY() - world.getMinBuildHeight())));
            if (fillStart.getY() < world.getMinBuildHeight()) {
                fillStart = new BlockPos(fillStart.getX(), world.getMinBuildHeight(), fillStart.getZ());
            }
            BlockPos pos = fillStart;
            while (pos.getY() <= below1.getY()) {
                world.setBlock(pos, chosen, 3);
                pos = pos.above();
            }
        } else {
            world.setBlock(below1, chosen, 3);
        }

        HighwayAboveColumnClearer.clearAboveColumn(world, surfacePos, cfg);

        BlockPos belowPos1 = surfacePos.below(2);
        BlockState belowState1 = world.getBlockState(belowPos1);
        if (belowState1.is(Blocks.GRASS_BLOCK)) {
            world.setBlock(belowPos1, Blocks.DIRT.defaultBlockState(), 3);
        }
    }
}
