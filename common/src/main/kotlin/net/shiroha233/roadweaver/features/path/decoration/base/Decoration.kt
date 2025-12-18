package net.shiroha233.roadweaver.features.path.decoration.base

import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import net.shiroha233.roadweaver.features.path.decoration.compat.RoadFeatureCompat

abstract class Decoration(
    private var placePos: BlockPos,
    private val world: WorldGenLevel
) {
    abstract fun place()

    fun computeSurfacePos(): BlockPos {
        return BlockPos(
            placePos.x,
            world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, placePos.x, placePos.z),
            placePos.z
        )
    }

    fun isPlaceAllowedAt(surfacePos: BlockPos): Boolean {
        val below: BlockState = world.getBlockState(surfacePos.below())
        val belowInvalid = below.`is`(Blocks.WATER) ||
            below.`is`(Blocks.LAVA) ||
            below.`is`(BlockTags.LOGS) ||
            RoadFeatureCompat.dontPlaceHere(below.block)
        return !belowInvalid
    }

    protected fun placeAllowed(): Boolean {
        val surfacePos = computeSurfacePos()
        placePos = surfacePos
        return isPlaceAllowedAt(surfacePos)
    }

    fun getPos(): BlockPos = placePos

    fun getWorld(): WorldGenLevel = world
}
