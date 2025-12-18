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
import net.shiroha233.roadweaver.structures.data.LootConfig
import net.shiroha233.roadweaver.structures.data.MobSpawnRule
import net.shiroha233.roadweaver.structures.data.RoadsidePlacementRule
import net.shiroha233.roadweaver.structures.data.StructureScale
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes
import java.util.Optional

/**
 * 路边结构类型
 *
 * 继承原版 Structure，用于数据驱动的路边结构定义。
 *
 * 特点：
 * - findGenerationPoint 返回 empty（不参与原版调度）
 * - 提供 placeAt 方法用于手动放置
 * - 支持保存到区块数据
 * - 完全通过 datapack JSON 定义
 */
@Suppress("unused")
class RoadsideStructure(
    settings: StructureSettings,
    private val templateId: ResourceLocation,
    private val sizeHint: Vec3i,
    private val weight: Int,
    private val faceRoad: Boolean,
    private val scale: StructureScale,
    private val placementRule: RoadsidePlacementRule,
    private val mobSpawns: List<MobSpawnRule>,
    private val lootConfigs: List<LootConfig>
) : Structure(settings) {
    /**
     * 原版调度入口 - 返回 empty，不参与自动调度
     */
    override fun findGenerationPoint(context: GenerationContext): Optional<GenerationStub> {
        return Optional.empty()
    }

    override fun type(): StructureType<*> {
        return ModStructureTypes.ROADSIDE
            ?: throw IllegalStateException("ModStructureTypes.ROADSIDE 未初始化：请确认平台层已完成结构类型注册")
    }

    // ==================== 属性访问器 ====================

    fun templateId(): ResourceLocation = templateId

    fun sizeHint(): Vec3i = sizeHint

    fun weight(): Int = weight

    fun faceRoad(): Boolean = faceRoad

    fun scale(): StructureScale = scale

    fun placementRule(): RoadsidePlacementRule = placementRule

    fun mobSpawns(): List<MobSpawnRule> = mobSpawns

    fun lootConfigs(): List<LootConfig> = lootConfigs

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
            Mirror.NONE,
            mobSpawns,
            lootConfigs
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

    /**
     * 将 StructureStart 保存到区块数据
     */
    fun saveToChunk(
        level: WorldGenLevel,
        structureManager: StructureManager,
        start: StructureStart,
        chunkPos: ChunkPos
    ) {
        // 原版会自动处理保存，这里只是标记
        // StructureManager 会在区块保存时自动序列化 StructureStart
    }

    companion object {
        @JvmField
        val CODEC: MapCodec<RoadsideStructure> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                settingsCodec(instance),
                ResourceLocation.CODEC.fieldOf("template").forGetter { it.templateId },
                Vec3i.CODEC.optionalFieldOf("size_hint", Vec3i(5, 5, 5)).forGetter { it.sizeHint },
                Codec.INT.optionalFieldOf("weight", 10).forGetter { it.weight },
                Codec.BOOL.optionalFieldOf("face_road", true).forGetter { it.faceRoad },
                StructureScale.CODEC.optionalFieldOf("scale", StructureScale.SMALL).forGetter { it.scale },
                RoadsidePlacementRule.CODEC.optionalFieldOf("placement_rule", RoadsidePlacementRule.UNIVERSAL)
                    .forGetter { it.placementRule },
                MobSpawnRule.LIST_CODEC.optionalFieldOf("mob_spawns", listOf()).forGetter { it.mobSpawns },
                LootConfig.LIST_CODEC.optionalFieldOf("loot_configs", listOf()).forGetter { it.lootConfigs }
            ).apply(instance, ::RoadsideStructure)
        }
    }
}
