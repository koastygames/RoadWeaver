package net.shiroha233.roadweaver.features.path.decoration.system

import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.state.BlockState
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.features.path.decoration.base.Decoration

object NaturalDecorationSystem {
    @JvmStatic
    fun placeOnSurface(world: WorldGenLevel, placePos: BlockPos, material: List<BlockState>, random: RandomSource, cfg: ModConfig) {
        DecorationPlanner.placeOnSurface(world, placePos, material, random, cfg, DecorationPlanner.Mode.NATURAL)
    }

    @JvmStatic
    fun addDecoration(
        world: WorldGenLevel,
        out: MutableSet<Decoration>,
        placePos: BlockPos,
        segmentIndex: Int,
        nextPos: BlockPos,
        prevPos: BlockPos,
        middlePositions: List<BlockPos>,
        roadWidth: Int,
        random: RandomSource,
        cfg: ModConfig
    ) {
        DecorationPlanner.addDecoration(
            world,
            out,
            placePos,
            segmentIndex,
            nextPos,
            prevPos,
            middlePositions,
            roadWidth,
            random,
            cfg,
            DecorationPlanner.Mode.NATURAL
        )
    }
}
