package net.shiroha233.roadweaver.structures.data

import com.mojang.serialization.Codec
import net.minecraft.core.Holder
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BiomeTags
import net.minecraft.util.StringRepresentable
import net.minecraft.world.level.biome.Biome

/**
 * 群系分类枚举
 *
 * 用于将 Minecraft 群系分组，便于路边结构根据群系类型选择放置。
 * 支持 Codec 序列化，可在 datapack JSON 中使用。
 */
@Suppress("unused")
enum class BiomeCategory(private val serializedName: String) : StringRepresentable {
    /** 平原类（平原、向日葵平原、草甸） */
    PLAINS("plains"),

    /** 森林类（森林、桦木森林、黑森林、繁花森林） */
    FOREST("forest"),

    /** 针叶林类（针叶林、云杉林、老生长针叶林） */
    TAIGA("taiga"),

    /** 丛林类 */
    JUNGLE("jungle"),

    /** 沙漠类 */
    DESERT("desert"),

    /** 热带草原/稀树草原 */
    SAVANNA("savanna"),

    /** 恶地/荒原 */
    BADLANDS("badlands"),

    /** 雪地/冰原 */
    SNOWY("snowy"),

    /** 沼泽类 */
    SWAMP("swamp"),

    /** 樱花树林 */
    CHERRY_GROVE("cherry_grove"),

    /** 蘑菇岛 */
    MUSHROOM("mushroom"),

    /** 山地/高原 */
    MOUNTAIN("mountain"),

    /** 海滩/河岸 */
    BEACH("beach"),

    /** 其他/未分类（默认） */
    OTHER("other");

    override fun getSerializedName(): String = serializedName

    companion object {
        @JvmField
        val CODEC: Codec<BiomeCategory> = StringRepresentable.fromEnum(BiomeCategory::values)

        // 群系 ID 常量
        private val PLAINS_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "plains")
        private val SUNFLOWER_PLAINS_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "sunflower_plains")
        private val MEADOW_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "meadow")
        private val FOREST_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "forest")
        private val FLOWER_FOREST_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "flower_forest")
        private val BIRCH_FOREST_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "birch_forest")
        private val OLD_GROWTH_BIRCH_FOREST_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "old_growth_birch_forest")
        private val DARK_FOREST_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "dark_forest")
        private val TAIGA_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "taiga")
        private val OLD_GROWTH_PINE_TAIGA_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "old_growth_pine_taiga")
        private val OLD_GROWTH_SPRUCE_TAIGA_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "old_growth_spruce_taiga")
        private val SNOWY_TAIGA_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "snowy_taiga")
        private val JUNGLE_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "jungle")
        private val SPARSE_JUNGLE_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "sparse_jungle")
        private val BAMBOO_JUNGLE_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "bamboo_jungle")
        private val DESERT_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "desert")
        private val SAVANNA_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "savanna")
        private val SAVANNA_PLATEAU_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "savanna_plateau")
        private val WINDSWEPT_SAVANNA_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "windswept_savanna")
        private val BADLANDS_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "badlands")
        private val WOODED_BADLANDS_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "wooded_badlands")
        private val ERODED_BADLANDS_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "eroded_badlands")
        private val SNOWY_PLAINS_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "snowy_plains")
        private val ICE_SPIKES_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "ice_spikes")
        private val SNOWY_SLOPES_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "snowy_slopes")
        private val FROZEN_PEAKS_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "frozen_peaks")
        private val SWAMP_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "swamp")
        private val MANGROVE_SWAMP_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "mangrove_swamp")
        private val CHERRY_GROVE_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "cherry_grove")
        private val MUSHROOM_FIELDS_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "mushroom_fields")
        private val WINDSWEPT_HILLS_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "windswept_hills")
        private val WINDSWEPT_GRAVELLY_HILLS_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "windswept_gravelly_hills")
        private val WINDSWEPT_FOREST_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "windswept_forest")
        private val STONY_PEAKS_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "stony_peaks")
        private val JAGGED_PEAKS_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "jagged_peaks")
        private val BEACH_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "beach")
        private val SNOWY_BEACH_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "snowy_beach")
        private val STONY_SHORE_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "stony_shore")

        /**
         * 根据群系 Holder 判断其分类
         */
        @JvmStatic
        fun fromBiome(biome: Holder<Biome>?): BiomeCategory {
            if (biome == null) {
                return OTHER
            }

            val biomeId = biome.unwrapKey().map { it.location() }.orElse(null) ?: return OTHER
            return fromBiomeId(biomeId, biome)
        }

        private fun fromBiomeId(id: ResourceLocation, biome: Holder<Biome>): BiomeCategory {
            // 平原类
            if (id == PLAINS_ID || id == SUNFLOWER_PLAINS_ID || id == MEADOW_ID) {
                return PLAINS
            }

            // 森林类
            if (
                id == FOREST_ID || id == FLOWER_FOREST_ID ||
                id == BIRCH_FOREST_ID || id == OLD_GROWTH_BIRCH_FOREST_ID ||
                id == DARK_FOREST_ID
            ) {
                return FOREST
            }

            // 针叶林类
            if (
                id == TAIGA_ID || id == OLD_GROWTH_PINE_TAIGA_ID ||
                id == OLD_GROWTH_SPRUCE_TAIGA_ID || id == SNOWY_TAIGA_ID
            ) {
                return TAIGA
            }

            // 丛林类
            if (id == JUNGLE_ID || id == SPARSE_JUNGLE_ID || id == BAMBOO_JUNGLE_ID) {
                return JUNGLE
            }

            // 沙漠类
            if (id == DESERT_ID) {
                return DESERT
            }

            // 热带草原类
            if (id == SAVANNA_ID || id == SAVANNA_PLATEAU_ID || id == WINDSWEPT_SAVANNA_ID) {
                return SAVANNA
            }

            // 恶地类
            if (id == BADLANDS_ID || id == WOODED_BADLANDS_ID || id == ERODED_BADLANDS_ID) {
                return BADLANDS
            }

            // 雪地类
            if (id == SNOWY_PLAINS_ID || id == ICE_SPIKES_ID || id == SNOWY_SLOPES_ID || id == FROZEN_PEAKS_ID) {
                return SNOWY
            }

            // 沼泽类
            if (id == SWAMP_ID || id == MANGROVE_SWAMP_ID) {
                return SWAMP
            }

            // 樱花林
            if (id == CHERRY_GROVE_ID) {
                return CHERRY_GROVE
            }

            // 蘑菇岛
            if (id == MUSHROOM_FIELDS_ID) {
                return MUSHROOM
            }

            // 山地类
            if (
                id == WINDSWEPT_HILLS_ID || id == WINDSWEPT_GRAVELLY_HILLS_ID ||
                id == WINDSWEPT_FOREST_ID || id == STONY_PEAKS_ID || id == JAGGED_PEAKS_ID
            ) {
                return MOUNTAIN
            }

            // 海滩类
            if (id == BEACH_ID || id == SNOWY_BEACH_ID || id == STONY_SHORE_ID) {
                return BEACH
            }

            return OTHER
        }
    }
}
