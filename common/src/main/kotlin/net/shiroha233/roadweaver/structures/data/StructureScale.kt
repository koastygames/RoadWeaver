package net.shiroha233.roadweaver.structures.data

import com.mojang.serialization.Codec
import net.minecraft.util.StringRepresentable

/**
 * 结构规模枚举
 *
 * 定义不同规模结构的放置参数：
 * - 地形检查阈值（坡度、高度差）
 * - 托盘缓冲区大小
 * - 间距基准值
 *
 * 支持 Codec 序列化，可在 datapack JSON 中使用。
 */
@Suppress("unused")
enum class StructureScale(
    private val serializedName: String,
    val maxSlope: Int, // 允许的最大坡度（底部高度差）
    val maxHeightDiff: Int, // 与道路的最大高度差
    val terraceBuffer: Int, // 托盘缓冲区宽度
    val defaultSpacing: Int, // 同类型结构默认间距
    val defaultSeparation: Int // 与其他结构默认间距
) : StringRepresentable {
    /**
     * 小型结构（如长椅、路牌）
     * - 简单地形检查
     * - 较小托盘
     */
    SMALL("small", 3, 5, 4, 64, 16),

    /**
     * 中型结构（如咖啡屋、商店）
     * - 严格坡度检查
     * - 多点采样
     * - 较大托盘
     */
    MEDIUM("medium", 6, 10, 8, 256, 48),

    /**
     * 大型结构（预留）
     * - 最严格的地形要求
     * - 最大托盘
     */
    LARGE("large", 4, 12, 12, 512, 64);

    override fun getSerializedName(): String = serializedName

    companion object {
        @JvmField
        val CODEC: Codec<StructureScale> = StringRepresentable.fromEnum(StructureScale::values)
    }
}
