package net.shiroha233.roadweaver.features.path.decoration.compat

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

object RoadFeatureCompat {
    private val DONT_PLACE: Set<Block> = hashSetOf(
        Blocks.TALL_SEAGRASS,
        Blocks.MANGROVE_ROOTS
    )

    @JvmStatic
    fun dontPlaceHere(b: Block): Boolean {
        return DONT_PLACE.contains(b)
    }
}
