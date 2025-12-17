package net.shiroha233.roadweaver.features.path.decoration.system

import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.state.BlockState
import net.shiroha233.roadweaver.config.ModConfig

object SurfacePlacementUtil {
    @JvmStatic
    fun placeOnSurface(
        world: WorldGenLevel,
        placePos: BlockPos,
        material: List<BlockState>,
        roadType: Int,
        random: RandomSource,
        cfg: ModConfig
    ) {
        val naturalBlockChance = 1.0
        val surfacePos = placePos
        val belowTop = surfacePos.below()
        val blockStateAtPos = world.getBlockState(belowTop)
        val doPlace = (roadType == 0) || random.nextDouble() < naturalBlockChance
        if (doPlace) {
            RoadBlockPlacer.placeRoadBlock(world, blockStateAtPos, surfacePos, material, random, cfg)
        } else {
            AboveColumnClearer.clearAboveColumn(world, surfacePos, cfg)
        }
    }

    @JvmStatic
    fun placeRoadBlock(
        world: WorldGenLevel,
        blockBelow: BlockState,
        surfacePos: BlockPos,
        materials: List<BlockState>,
        random: RandomSource,
        cfg: ModConfig
    ) {
        RoadBlockPlacer.placeRoadBlock(world, blockBelow, surfacePos, materials, random, cfg)
    }

    @JvmStatic
    fun clearAboveColumn(world: WorldGenLevel, surfacePos: BlockPos, cfg: ModConfig) {
        AboveColumnClearer.clearAboveColumn(world, surfacePos, cfg)
    }
}
