package net.shiroha233.roadweaver.features.highway.placement;

import net.minecraft.core.BlockPos;
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
    private HighwayRoadBlockPlacer() {
    }

    public static void placeRoadBlock(WorldGenLevel world,
            BlockState blockBelow,
            BlockPos surfacePos,
            List<BlockState> materials,
            RandomSource random,
            ModConfig cfg) {
        if (materials == null || materials.isEmpty())
            return;
        if (!HighwayPlacementRules.placeAllowedCheck(blockBelow.getBlock()))
            return;

        BlockState chosen = materials.get(random.nextInt(materials.size()));

        // 公路系统不再进行任何路基填充（包括向下的堤道填充）
        BlockPos below1 = surfacePos.below();
        world.setBlock(below1, chosen, 3);

        HighwayAboveColumnClearer.clearAboveColumn(world, surfacePos, cfg);

        BlockPos belowPos1 = surfacePos.below(2);
        BlockState belowState1 = world.getBlockState(belowPos1);
        if (belowState1.is(Blocks.GRASS_BLOCK)) {
            world.setBlock(belowPos1, Blocks.DIRT.defaultBlockState(), 3);
        }
    }
}
