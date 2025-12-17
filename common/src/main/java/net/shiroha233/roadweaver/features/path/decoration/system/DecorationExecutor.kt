package net.shiroha233.roadweaver.features.path.decoration.system

import net.shiroha233.roadweaver.features.path.decoration.base.Decoration
import net.shiroha233.roadweaver.features.path.decoration.material.wood.BiomeWoodAware
import net.shiroha233.roadweaver.features.path.decoration.material.wood.WoodSelector

object DecorationExecutor {
    @JvmStatic
    fun tryPlaceDecorations(positions: MutableSet<Decoration>) {
        if (positions.isEmpty()) return
        val it = positions.iterator()
        while (it.hasNext()) {
            val dec = it.next()
            if (dec == null) {
                it.remove()
                continue
            }
            if (dec is BiomeWoodAware) {
                dec.setWoodType(WoodSelector.forBiome(dec.getWorld(), dec.getPos()))
            }
            dec.place()
            it.remove()
        }
    }
}
