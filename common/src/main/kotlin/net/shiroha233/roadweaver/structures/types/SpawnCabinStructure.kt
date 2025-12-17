package net.shiroha233.roadweaver.structures.types

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureStart
import net.minecraft.world.level.levelgen.structure.StructureType
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes
import java.util.Optional

/**
 * 初始小屋结构类型
 *
 * 继承原版 Structure，用于出生点附近的初始小屋。
 *
 * 特点：
 * - findGenerationPoint 返回 empty（不参与原版调度）
 * - 提供 placeAt 方法用于手动放置
 * - 支持保存到区块数据
 * - 通过 datapack JSON 定义
 */
@Suppress("unused")
class SpawnCabinStructure(
    settings: StructureSettings,
    private val templateId: ResourceLocation,
    private val sizeHint: Vec3i,
    private val withTerrace: Boolean,
    private val terraceInnerRadius: Int,
    private val terraceOuterRadius: Int
) : Structure(settings) {
    /**
     * 原版调度入口 - 返回 empty，不参与自动调度
     */
    override fun findGenerationPoint(context: GenerationContext): Optional<GenerationStub> {
        return Optional.empty()
    }

    override fun type(): StructureType<*> {
        return ModStructureTypes.SPAWN_CABIN
            ?: throw IllegalStateException("ModStructureTypes.SPAWN_CABIN 未初始化：请确认平台层已完成结构类型注册")
    }

    // ==================== 属性访问器 ====================

    fun templateId(): ResourceLocation = templateId

    fun sizeHint(): Vec3i = sizeHint

    fun withTerrace(): Boolean = withTerrace

    fun terraceInnerRadius(): Int = terraceInnerRadius

    fun terraceOuterRadius(): Int = terraceOuterRadius

    // ==================== 手动放置方法 ====================

    /**
     * 在指定位置手动放置结构
     *
     * @return 如果成功放置则返回 StructureStart，否则返回 null
     */
    fun placeAt(
        level: WorldGenLevel,
        structureManager: StructureManager,
        templateManager: StructureTemplateManager,
        generator: ChunkGenerator,
        pos: BlockPos,
        rotation: Rotation,
        random: RandomSource
    ): StructureStart? {
        val builder = StructurePiecesBuilder()

        val piece = SimpleTemplatePiece(
            templateManager,
            templateId,
            pos,
            rotation,
            Mirror.NONE
        )
        builder.addPiece(piece)

        val chunkPos = ChunkPos(pos)
        val container: PiecesContainer = builder.build()
        val start = StructureStart(this, chunkPos, 0, container)

        if (!start.isValid) {
            return null
        }

        val boundingBox = start.boundingBox
        start.placeInChunk(level, structureManager, generator, random, boundingBox, chunkPos)

        return start
    }

    companion object {
        @JvmField
        val CODEC: MapCodec<SpawnCabinStructure> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                settingsCodec(instance),
                ResourceLocation.CODEC.fieldOf("template").forGetter { it.templateId },
                Vec3i.CODEC.optionalFieldOf("size_hint", Vec3i(16, 10, 16)).forGetter { it.sizeHint },
                Codec.BOOL.optionalFieldOf("with_terrace", true).forGetter { it.withTerrace },
                Codec.INT.optionalFieldOf("terrace_inner_radius", 10).forGetter { it.terraceInnerRadius },
                Codec.INT.optionalFieldOf("terrace_outer_radius", 16).forGetter { it.terraceOuterRadius }
            ).apply(instance, ::SpawnCabinStructure)
        }
    }
}
