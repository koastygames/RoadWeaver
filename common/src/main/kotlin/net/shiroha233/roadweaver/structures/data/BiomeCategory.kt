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
        private val PLAINS_ID = ResourceLocation("minecraft", "plains")
        private val SUNFLOWER_PLAINS_ID = ResourceLocation("minecraft", "sunflower_plains")
        private val MEADOW_ID = ResourceLocation("minecraft", "meadow")
        private val FOREST_ID = ResourceLocation("minecraft", "forest")
        private val FLOWER_FOREST_ID = ResourceLocation("minecraft", "flower_forest")
        private val BIRCH_FOREST_ID = ResourceLocation("minecraft", "birch_forest")
        private val OLD_GROWTH_BIRCH_FOREST_ID = ResourceLocation("minecraft", "old_growth_birch_forest")
        private val DARK_FOREST_ID = ResourceLocation("minecraft", "dark_forest")
        private val TAIGA_ID = ResourceLocation("minecraft", "taiga")
        private val OLD_GROWTH_PINE_TAIGA_ID = ResourceLocation("minecraft", "old_growth_pine_taiga")
        private val OLD_GROWTH_SPRUCE_TAIGA_ID = ResourceLocation("minecraft", "old_growth_spruce_taiga")
        private val SNOWY_TAIGA_ID = ResourceLocation("minecraft", "snowy_taiga")
        private val JUNGLE_ID = ResourceLocation("minecraft", "jungle")
        private val SPARSE_JUNGLE_ID = ResourceLocation("minecraft", "sparse_jungle")
        private val BAMBOO_JUNGLE_ID = ResourceLocation("minecraft", "bamboo_jungle")
        private val DESERT_ID = ResourceLocation("minecraft", "desert")
        private val SAVANNA_ID = ResourceLocation("minecraft", "savanna")
        private val SAVANNA_PLATEAU_ID = ResourceLocation("minecraft", "savanna_plateau")
        private val WINDSWEPT_SAVANNA_ID = ResourceLocation("minecraft", "windswept_savanna")
        private val BADLANDS_ID = ResourceLocation("minecraft", "badlands")
        private val WOODED_BADLANDS_ID = ResourceLocation("minecraft", "wooded_badlands")
        private val ERODED_BADLANDS_ID = ResourceLocation("minecraft", "eroded_badlands")
        private val SNOWY_PLAINS_ID = ResourceLocation("minecraft", "snowy_plains")
        private val ICE_SPIKES_ID = ResourceLocation("minecraft", "ice_spikes")
        private val SNOWY_SLOPES_ID = ResourceLocation("minecraft", "snowy_slopes")
        private val FROZEN_PEAKS_ID = ResourceLocation("minecraft", "frozen_peaks")
        private val SWAMP_ID = ResourceLocation("minecraft", "swamp")
        private val MANGROVE_SWAMP_ID = ResourceLocation("minecraft", "mangrove_swamp")
        private val CHERRY_GROVE_ID = ResourceLocation("minecraft", "cherry_grove")
        private val MUSHROOM_FIELDS_ID = ResourceLocation("minecraft", "mushroom_fields")
        private val WINDSWEPT_HILLS_ID = ResourceLocation("minecraft", "windswept_hills")
        private val WINDSWEPT_GRAVELLY_HILLS_ID = ResourceLocation("minecraft", "windswept_gravelly_hills")
        private val WINDSWEPT_FOREST_ID = ResourceLocation("minecraft", "windswept_forest")
        private val STONY_PEAKS_ID = ResourceLocation("minecraft", "stony_peaks")
        private val JAGGED_PEAKS_ID = ResourceLocation("minecraft", "jagged_peaks")
        private val BEACH_ID = ResourceLocation("minecraft", "beach")
        private val SNOWY_BEACH_ID = ResourceLocation("minecraft", "snowy_beach")
        private val STONY_SHORE_ID = ResourceLocation("minecraft", "stony_shore")

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
            if (id.equals(PLAINS_ID) || id.equals(SUNFLOWER_PLAINS_ID) || id.equals(MEADOW_ID)) {
                return PLAINS
            }

            // 森林类
            if (
                id.equals(FOREST_ID) || id.equals(FLOWER_FOREST_ID) ||
                id.equals(BIRCH_FOREST_ID) || id.equals(OLD_GROWTH_BIRCH_FOREST_ID) ||
                id.equals(DARK_FOREST_ID)
            ) {
                return FOREST
            }

            // 针叶林类
            if (
                id.equals(TAIGA_ID) || id.equals(OLD_GROWTH_PINE_TAIGA_ID) ||
                id.equals(OLD_GROWTH_SPRUCE_TAIGA_ID) || id.equals(SNOWY_TAIGA_ID)
            ) {
                return TAIGA
            }

            // 丛林类
            if (id.equals(JUNGLE_ID) || id.equals(SPARSE_JUNGLE_ID) || id.equals(BAMBOO_JUNGLE_ID)) {
                return JUNGLE
            }

            // 沙漠类
            if (id.equals(DESERT_ID)) {
                return DESERT
            }

            // 热带草原类
            if (id.equals(SAVANNA_ID) || id.equals(SAVANNA_PLATEAU_ID) || id.equals(WINDSWEPT_SAVANNA_ID)) {
                return SAVANNA
            }

            // 恶地类
            if (id.equals(BADLANDS_ID) || id.equals(WOODED_BADLANDS_ID) || id.equals(ERODED_BADLANDS_ID)) {
                return BADLANDS
            }

            // 雪地类
            if (id.equals(SNOWY_PLAINS_ID) || id.equals(ICE_SPIKES_ID) || id.equals(SNOWY_SLOPES_ID) || id.equals(FROZEN_PEAKS_ID)) {
                return SNOWY
            }

            // 沼泽类
            if (id.equals(SWAMP_ID) || id.equals(MANGROVE_SWAMP_ID)) {
                return SWAMP
            }

            // 樱花林
            if (id.equals(CHERRY_GROVE_ID)) {
                return CHERRY_GROVE
            }

            // 蘑菇岛
            if (id.equals(MUSHROOM_FIELDS_ID)) {
                return MUSHROOM
            }

            // 山地类
            if (
                id.equals(WINDSWEPT_HILLS_ID) || id.equals(WINDSWEPT_GRAVELLY_HILLS_ID) ||
                id.equals(WINDSWEPT_FOREST_ID) || id.equals(STONY_PEAKS_ID) || id.equals(JAGGED_PEAKS_ID)
            ) {
                return MOUNTAIN
            }

            // 海滩类
            if (id.equals(BEACH_ID) || id.equals(SNOWY_BEACH_ID) || id.equals(STONY_SHORE_ID)) {
                return BEACH
            }

            return OTHER
        }
    }
}
