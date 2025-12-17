package net.shiroha233.roadweaver.features.path.decoration.types

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.Half
import net.shiroha233.roadweaver.features.path.decoration.base.OrientedDecoration
import net.shiroha233.roadweaver.features.path.decoration.material.wood.BiomeWoodAware
import net.shiroha233.roadweaver.features.path.decoration.material.wood.WoodTrapdoorMapper
import net.shiroha233.roadweaver.helpers.Records

class LamppostDecoration(pos: BlockPos, direction: Vec3i, world: WorldGenLevel) : OrientedDecoration(pos, direction, world), BiomeWoodAware {
    private lateinit var wood: Records.WoodAssets

    override fun place() {
        if (!placeAllowed()) return
        val basePos = getPos()
        val world = getWorld()
        placeLampStructure(basePos, world)
    }

    private fun placeLampStructure(pos: BlockPos, world: WorldGenLevel) {
        world.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 3)
        world.setBlock(pos.above(1), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3)
        world.setBlock(pos.above(2), wood.fence.defaultBlockState(), 3)
        world.setBlock(pos.above(3), wood.fence.defaultBlockState(), 3)
        world.setBlock(pos.above(4), Blocks.STONE_BRICK_WALL.defaultBlockState(), 3)

        val lampPos = pos.above(5)
        world.setBlock(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true), 3)

        world.setBlock(
            lampPos.above(),
            Blocks.DAYLIGHT_DETECTOR.defaultBlockState().setValue(BlockStateProperties.INVERTED, true),
            3
        )

        val trap: Block = WoodTrapdoorMapper.trapdoorForWood(wood)
        for (dir in arrayOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            val tpos = lampPos.relative(dir)
            var st: BlockState = trap.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, dir)
                .setValue(BlockStateProperties.OPEN, false)
                .setValue(BlockStateProperties.HALF, Half.TOP)
            if (st.hasProperty(BlockStateProperties.WATERLOGGED)) {
                st = st.setValue(BlockStateProperties.WATERLOGGED, false)
            }
            world.setBlock(tpos, st, 3)
        }
    }

    override fun setWoodType(assets: Records.WoodAssets) {
        wood = assets
    }
}
