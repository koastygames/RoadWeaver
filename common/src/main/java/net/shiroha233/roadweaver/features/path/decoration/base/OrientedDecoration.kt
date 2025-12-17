package net.shiroha233.roadweaver.features.path.decoration.base

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import kotlin.math.abs

abstract class OrientedDecoration(
    placePos: BlockPos,
    private val orthogonalVector: Vec3i,
    world: WorldGenLevel
) : Decoration(placePos, world) {

    protected fun getCardinalRotationFromVector(vec: Vec3i, start: Boolean): Int {
        return if (start) {
            if (abs(vec.x) > abs(vec.z)) {
                if (vec.x > 0) 0 else 8
            } else {
                if (vec.z > 0) 4 else 12
            }
        } else {
            if (abs(vec.x) > abs(vec.z)) {
                if (vec.x > 0) 8 else 0
            } else {
                if (vec.z > 0) 12 else 4
            }
        }
    }

    fun getOrthogonalVector(): Vec3i = orthogonalVector

    class DirectionProperties(
        val offsetDirection: Direction,
        val reverseDirectionProperty: BooleanProperty,
        val directionProperty: BooleanProperty
    )

    protected fun getDirectionProperties(rotation: Int): DirectionProperties {
        return when (rotation) {
            12 -> DirectionProperties(Direction.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.NORTH)
            0 -> DirectionProperties(Direction.EAST, BlockStateProperties.WEST, BlockStateProperties.EAST)
            4 -> DirectionProperties(Direction.SOUTH, BlockStateProperties.NORTH, BlockStateProperties.SOUTH)
            else -> DirectionProperties(Direction.WEST, BlockStateProperties.EAST, BlockStateProperties.WEST)
        }
    }
}
