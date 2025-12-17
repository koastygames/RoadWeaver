package net.shiroha233.roadweaver.features.path.decoration.types

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.shiroha233.roadweaver.features.path.decoration.base.OrientedDecoration
import net.shiroha233.roadweaver.features.path.decoration.material.wood.BiomeWoodAware
import net.shiroha233.roadweaver.features.path.decoration.text.SignTextService
import net.shiroha233.roadweaver.helpers.Records

class SeaQuestionSignDecoration(
    pos: BlockPos,
    direction: Vec3i,
    world: WorldGenLevel,
    private val isStart: Boolean
) : OrientedDecoration(pos, direction, world), BiomeWoodAware {
    private lateinit var wood: Records.WoodAssets

    override fun place() {
        if (!placeAllowed()) return

        val rotation = getCardinalRotationFromVector(getOrthogonalVector(), isStart)
        val props = getDirectionProperties(rotation)

        val basePos = getPos()
        val world = getWorld()

        val signPos = basePos.above(2).relative(props.offsetDirection.opposite)
        val signRotation = (rotation + 8) % 16
        world.setBlock(
            signPos,
            wood.hangingSign.defaultBlockState()
                .setValue(BlockStateProperties.ROTATION_16, signRotation)
                .setValue(BlockStateProperties.ATTACHED, true),
            3
        )
        updateSigns(world, signPos)

        placeFenceStructure(basePos, props)
    }

    private fun placeFenceStructure(pos: BlockPos, props: DirectionProperties) {
        val world = getWorld()
        world.setBlock(pos.above(3).relative(props.offsetDirection.opposite), wood.fence.defaultBlockState().setValue(props.directionProperty, true), 3)
        world.setBlock(pos.above(0), wood.fence.defaultBlockState(), 3)
        world.setBlock(pos.above(1), wood.fence.defaultBlockState(), 3)
        world.setBlock(pos.above(2), wood.fence.defaultBlockState(), 3)
        world.setBlock(pos.above(3), wood.fence.defaultBlockState().setValue(props.reverseDirectionProperty, true), 3)
    }

    private fun updateSigns(level: WorldGenLevel, pos: BlockPos) {
        SignTextService.writeSeaQuestionSign(level, pos)
    }

    override fun setWoodType(assets: Records.WoodAssets) {
        wood = assets
    }
}
