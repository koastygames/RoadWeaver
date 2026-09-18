package net.shiroha233.roadweaver.features.path.decoration.system;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ModConfig;

import java.util.List;

/**
 * Road block placement with controlled material variation.
 *
 * The first palette entry is treated as the local biome's primary material and
 * is deliberately favoured. Secondary materials add visual variation without
 * turning the road into noisy random block spam.
 */
public final class RoadBlockPlacer {
    private RoadBlockPlacer() {}

    public static void placeRoadBlock(WorldGenLevel world,
            BlockState blockBelow,
            BlockPos surfacePos,
            List<BlockState> materials,
            RandomSource random,
            ModConfig cfg) {
        if (!PlacementRules.placeAllowedCheck(blockBelow.getBlock()) || materials == null || materials.isEmpty())
            return;

        BlockState chosen = chooseMaterial(materials, random);
        BlockPos below1 = surfacePos.below();
        world.setBlock(below1, chosen, 3);

        AboveColumnClearer.clearAboveColumn(world, surfacePos, cfg);

        BlockPos belowPos1 = surfacePos.below(2);
        BlockState belowState1 = world.getBlockState(belowPos1);
        if (belowState1.is(Blocks.GRASS_BLOCK)) {
            world.setBlock(belowPos1, Blocks.DIRT.defaultBlockState(), 3);
        }
    }

    private static BlockState chooseMaterial(List<BlockState> materials, RandomSource random) {
        if (materials.size() == 1) {
            return materials.get(0);
        }

        // Keep the dominant surface material visually coherent. The remaining
        // 30% is spread across secondary materials for natural variation.
        if (random.nextInt(100) < 70) {
            return materials.get(0);
        }
        return materials.get(1 + random.nextInt(materials.size() - 1));
    }
}
