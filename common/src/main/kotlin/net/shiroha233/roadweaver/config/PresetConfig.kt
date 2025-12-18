package net.shiroha233.roadweaver.config

import java.util.ArrayList

/**
 * 道路“预设”配置（放在 config/roadweaver_presets.json）。
 * 统一管理道路宽度候选与材质组合（方块ID）。
 */
class PresetConfig {
    var materials: MutableList<List<String>> = ArrayList()

    companion object {
        @JvmStatic
        fun defaults(): PresetConfig {
            val cfg = PresetConfig()
            cfg.materials.add(listOf("minecraft:mud_bricks", "minecraft:packed_mud"))
            cfg.materials.add(listOf("minecraft:polished_andesite", "minecraft:stone_bricks"))
            cfg.materials.add(listOf("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"))
            return cfg
        }
    }

    fun sanitize() {
        if (materials == null) materials = ArrayList()
        if (materials.isEmpty()) materials.add(listOf("minecraft:stone_bricks"))
    }
}
