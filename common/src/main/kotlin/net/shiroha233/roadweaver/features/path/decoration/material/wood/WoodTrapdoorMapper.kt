package net.shiroha233.roadweaver.features.path.decoration.material.wood

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.shiroha233.roadweaver.helpers.Records

object WoodTrapdoorMapper {
    @JvmStatic
    fun trapdoorForWood(assets: Records.WoodAssets?): Block {
        val p = assets?.planks ?: return Blocks.SPRUCE_TRAPDOOR
        return when (p) {
            Blocks.SPRUCE_PLANKS -> Blocks.SPRUCE_TRAPDOOR
            Blocks.OAK_PLANKS -> Blocks.OAK_TRAPDOOR
            Blocks.BIRCH_PLANKS -> Blocks.BIRCH_TRAPDOOR
            Blocks.JUNGLE_PLANKS -> Blocks.JUNGLE_TRAPDOOR
            Blocks.ACACIA_PLANKS -> Blocks.ACACIA_TRAPDOOR
            Blocks.DARK_OAK_PLANKS -> Blocks.DARK_OAK_TRAPDOOR
            Blocks.MANGROVE_PLANKS -> Blocks.MANGROVE_TRAPDOOR
            Blocks.BAMBOO_PLANKS -> Blocks.BAMBOO_TRAPDOOR
            Blocks.CHERRY_PLANKS -> Blocks.CHERRY_TRAPDOOR
            Blocks.CRIMSON_PLANKS -> Blocks.CRIMSON_TRAPDOOR
            Blocks.WARPED_PLANKS -> Blocks.WARPED_TRAPDOOR
            else -> Blocks.SPRUCE_TRAPDOOR
        }
    }
}
