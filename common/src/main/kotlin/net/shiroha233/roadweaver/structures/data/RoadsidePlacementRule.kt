package net.shiroha233.roadweaver.structures.data

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.EnumSet

/**
 * 路边结构放置规则（数据驱动版）
 *
 * 定义单个结构类型的放置条件：
 * - 允许的群系分类
 * - 最小道路长度要求
 *
 * 支持 Codec 序列化，可在 datapack JSON 中配置。
 */
@Suppress("unused")
data class RoadsidePlacementRule(
    val allowedBiomes: Set<BiomeCategory>,
    val minRoadLength: Int
) {
    /**
     * 检查群系是否允许放置
     */
    fun isBiomeAllowed(category: BiomeCategory): Boolean = allowedBiomes.contains(category)

    /**
     * 检查道路长度是否满足要求
     */
    fun isRoadLongEnough(roadLength: Int): Boolean = roadLength >= minRoadLength

    companion object {
        @JvmField
        val CODEC: Codec<RoadsidePlacementRule> = RecordCodecBuilder.create { instance ->
            instance.group(
                BiomeCategory.CODEC.listOf()
                    .optionalFieldOf("allowed_biomes", listOf())
                    .forGetter { rule -> rule.allowedBiomes.toList() },
                Codec.INT.optionalFieldOf("min_road_length", 0)
                    .forGetter(RoadsidePlacementRule::minRoadLength)
            ).apply(instance) { biomes, minLen ->
                val biomeSet = if (biomes.isEmpty()) {
                    EnumSet.allOf(BiomeCategory::class.java)
                } else {
                    EnumSet.copyOf(biomes)
                }
                RoadsidePlacementRule(biomeSet, minLen)
            }
        }

        // ==================== 预定义规则 ====================

        /** 通用规则：所有群系，无道路长度限制 */
        @JvmField
        val UNIVERSAL = RoadsidePlacementRule(EnumSet.allOf(BiomeCategory::class.java), 0)

        /** 温带规则：平原、森林、针叶林、樱花林 */
        @JvmField
        val TEMPERATE = RoadsidePlacementRule(
            EnumSet.of(BiomeCategory.PLAINS, BiomeCategory.FOREST, BiomeCategory.TAIGA, BiomeCategory.CHERRY_GROVE),
            0
        )

        /** 寒带规则：雪地、针叶林 */
        @JvmField
        val COLD = RoadsidePlacementRule(EnumSet.of(BiomeCategory.SNOWY, BiomeCategory.TAIGA), 0)

        /** 热带规则：沙漠、热带草原、恶地 */
        @JvmField
        val HOT = RoadsidePlacementRule(EnumSet.of(BiomeCategory.DESERT, BiomeCategory.SAVANNA, BiomeCategory.BADLANDS), 0)

        /** 长距离规则：只在长道路上出现（100段以上） */
        @JvmField
        val LONG_ROAD_ONLY = RoadsidePlacementRule(EnumSet.allOf(BiomeCategory::class.java), 100)

        /** 樱花林专属 */
        @JvmField
        val CHERRY_ONLY = RoadsidePlacementRule(EnumSet.of(BiomeCategory.CHERRY_GROVE), 0)

        /** 森林专属 */
        @JvmField
        val FOREST_ONLY = RoadsidePlacementRule(EnumSet.of(BiomeCategory.FOREST), 0)
    }
}
