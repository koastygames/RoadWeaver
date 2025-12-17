package net.shiroha233.roadweaver.features.path.decoration.types

import net.minecraft.core.BlockPos
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.shiroha233.roadweaver.features.path.decoration.base.Decoration
import net.shiroha233.roadweaver.features.path.decoration.material.wood.BiomeWoodAware
import net.shiroha233.roadweaver.helpers.Records

class FenceWaypointDecoration(placePos: BlockPos, world: WorldGenLevel) : Decoration(placePos, world), BiomeWoodAware {
    private lateinit var wood: Records.WoodAssets

    override fun place() {
        if (!placeAllowed()) return
        val surfacePos = getPos()
        val world = getWorld()
        world.setBlock(surfacePos, wood.fence.defaultBlockState(), 3)
        world.setBlock(surfacePos.above(), Blocks.TORCH.defaultBlockState(), 3)
    }

    override fun setWoodType(assets: Records.WoodAssets) {
        wood = assets
    }
}
