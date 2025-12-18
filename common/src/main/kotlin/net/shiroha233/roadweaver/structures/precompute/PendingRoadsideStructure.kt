package net.shiroha233.roadweaver.structures.precompute

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Rotation

/**
 * 待放置的路边结构信息
 *
 * 在道路规划阶段预计算，存储到世界数据中。
 * 在区块 STRUCTURE_STARTS 阶段被 Mixin 注入到 StructureManager。
 */
@Suppress("unused")
data class PendingRoadsideStructure(
    val structureId: ResourceLocation, // 结构 ID
    val anchor: BlockPos, // 放置锚点
    val rotation: Rotation, // 旋转
    val sizeX: Int, // 结构尺寸 X
    val sizeY: Int, // 结构尺寸 Y
    val sizeZ: Int // 结构尺寸 Z
) {
    /**
     * 获取结构所在的区块 X
     */
    fun chunkX(): Int = anchor.x shr 4

    /**
     * 获取结构所在的区块 Z
     */
    fun chunkZ(): Int = anchor.z shr 4

    /**
     * 获取区块键（用于索引）
     */
    fun chunkKey(): Long = (chunkX().toLong() shl 32) or (chunkZ().toLong() and 0xFFFFFFFFL)

    companion object {
        @JvmField
        val CODEC: Codec<PendingRoadsideStructure> = RecordCodecBuilder.create { instance ->
            instance.group(
                ResourceLocation.CODEC.fieldOf("structure_id").forGetter(PendingRoadsideStructure::structureId),
                BlockPos.CODEC.fieldOf("anchor").forGetter(PendingRoadsideStructure::anchor),
                Codec.STRING.fieldOf("rotation")
                    .xmap(Rotation::valueOf, Rotation::name)
                    .forGetter(PendingRoadsideStructure::rotation),
                Codec.INT.fieldOf("size_x").forGetter(PendingRoadsideStructure::sizeX),
                Codec.INT.fieldOf("size_y").forGetter(PendingRoadsideStructure::sizeY),
                Codec.INT.fieldOf("size_z").forGetter(PendingRoadsideStructure::sizeZ)
            ).apply(instance, ::PendingRoadsideStructure)
        }
    }
}
