package net.shiroha233.roadweaver.features.path.decoration.types

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.shiroha233.roadweaver.features.path.decoration.base.OrientedDecoration
import net.shiroha233.roadweaver.features.path.decoration.material.wood.BiomeWoodAware
import net.shiroha233.roadweaver.helpers.Records

class LanternPostDecoration(pos: BlockPos, direction: Vec3i, world: WorldGenLevel) : OrientedDecoration(pos, direction, world), BiomeWoodAware {
    private lateinit var wood: Records.WoodAssets

    override fun place() {
        if (!placeAllowed()) return
        val base = getPos()
        val world = getWorld()
        world.setBlock(base, wood.fence.defaultBlockState(), 3)
        world.setBlock(base.above(1), wood.fence.defaultBlockState(), 3)
        world.setBlock(base.above(2), wood.fence.defaultBlockState(), 3)
        world.setBlock(base.above(3), Blocks.LANTERN.defaultBlockState(), 3)
    }

    override fun setWoodType(assets: Records.WoodAssets) {
        wood = assets
    }
}
