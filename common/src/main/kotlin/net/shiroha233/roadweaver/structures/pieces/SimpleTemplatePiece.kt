package net.shiroha233.roadweaver.structures.pieces

import com.mojang.serialization.DataResult
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
import net.minecraft.world.level.storage.loot.LootTable
import net.shiroha233.roadweaver.structures.data.LootConfig
import net.shiroha233.roadweaver.structures.data.MobSpawnRule
import org.slf4j.LoggerFactory

/**
 * 简单模板结构片段
 *
 * 用于放置单个 NBT 模板的结构片段，支持：
 * - 旋转和镜像
 * - 忽略结构空位方块
 * - 保存到区块数据
 * - 结构放置后生成生物
 * - 结构放置后设置战利品表
 */
@Suppress("MemberVisibilityCanBePrivate")
class SimpleTemplatePiece : TemplateStructurePiece {
    private val templateId: ResourceLocation
    private val mobSpawns: List<MobSpawnRule>
    private val lootConfigs: List<LootConfig>

    // 注意：结构通常会跨越多个区块，postProcess 会被多次调用。
    // 不能简单用一个 boolean 一刀切，否则会出现：
    // - 第一次调用时另一个区块的箱子还没生成出来 -> 战利品设置失败且不会重试
    // - 生物生成被多次执行 -> 女仆数量超出预期
    private var lootApplied: BooleanArray? = null
    private var lootApplyAttempts: IntArray? = null
    private var mobsSpawned: Boolean = false

    /**
     * 从模板创建结构片段（基础版本，无生物/战利品）
     */
    constructor(
        manager: StructureTemplateManager,
        templateId: ResourceLocation,
        pos: BlockPos,
        rotation: Rotation,
        mirror: Mirror
    ) : this(manager, templateId, pos, rotation, mirror, emptyList(), emptyList())

    /**
     * 从模板创建结构片段（完整版本，包含生物/战利品配置）
     */
    constructor(
        manager: StructureTemplateManager,
        templateId: ResourceLocation,
        pos: BlockPos,
        rotation: Rotation,
        mirror: Mirror,
        mobSpawns: List<MobSpawnRule>?,
        lootConfigs: List<LootConfig>?
    ) : super(
        ModStructurePieceTypes.SIMPLE_TEMPLATE!!,
        0,
        manager,
        templateId,
        templateId.toString(),
        createPlaceSettings(rotation, mirror),
        pos
    ) {
        this.templateId = templateId
        this.mobSpawns = mobSpawns ?: emptyList()
        this.lootConfigs = lootConfigs ?: emptyList()
    }

    /**
     * 从 NBT 反序列化（用于加载已保存的结构）
     */
    constructor(manager: StructureTemplateManager, tag: CompoundTag) : super(
        ModStructurePieceTypes.SIMPLE_TEMPLATE!!,
        tag,
        manager,
        { _ ->
            createPlaceSettings(
                Rotation.valueOf(tag.getString("Rot")),
                Mirror.valueOf(tag.getString("Mir"))
            )
        }
    ) {
        this.templateId = ResourceLocation(tag.getString("Template"))
        this.mobSpawns = deserializeMobSpawns(tag)
        this.lootConfigs = deserializeLootConfigs(tag)
    }

    override fun addAdditionalSaveData(context: StructurePieceSerializationContext, tag: CompoundTag) {
        super.addAdditionalSaveData(context, tag)
        tag.putString("Template", templateId.toString())
        tag.putString("Rot", placeSettings.rotation.name)
        tag.putString("Mir", placeSettings.mirror.name)

        // 序列化生物生成规则
        serializeMobSpawns(tag)
        // 序列化战利品配置
        serializeLootConfigs(tag)
    }

    override fun handleDataMarker(
        marker: String,
        pos: BlockPos,
        level: ServerLevelAccessor,
        random: RandomSource,
        box: BoundingBox
    ) {
        // 处理数据标记方块（如 jigsaw 方块）
        // 路边结构目前不使用数据标记，留空
    }

    override fun postProcess(
        level: WorldGenLevel,
        structureManager: StructureManager,
        generator: ChunkGenerator,
        random: RandomSource,
        box: BoundingBox,
        chunkPos: ChunkPos,
        pivot: BlockPos
    ) {
        // 调用父类放置模板
        super.postProcess(level, structureManager, generator, random, box, chunkPos, pivot)

        // 获取结构锚点位置
        val anchorPos = templatePosition
        val rotation = placeSettings.rotation

        initPostProcessState()

        var allLootApplied = true

        // 处理战利品配置（先于生物生成，确保容器已放置）
        for (i in lootConfigs.indices) {
            val appliedArr = lootApplied ?: break
            if (appliedArr[i]) {
                continue
            }

            val lootConfig = lootConfigs[i]
            try {
                // 计算旋转后的偏移位置
                val rotatedOffset = transformOffset(lootConfig.offset, rotation)
                val containerPos = anchorPos.offset(rotatedOffset)

                lootApplyAttempts?.let { it[i] = it[i] + 1 }
                val applied = applyLootTable(level, containerPos, lootConfig, random)
                if (applied) {
                    appliedArr[i] = true
                } else {
                    allLootApplied = false
                }
            } catch (e: Exception) {
                allLootApplied = false
                LOGGER.warn("设置战利品表失败 at {}: {}", anchorPos, e.message)
            }
        }

        // 如果没有战利品配置，就不需要等待
        if (lootConfigs.isEmpty()) {
            allLootApplied = true
        }

        // 处理生物生成
        // 设计原则：等结构关键点（这里用“箱子全部就绪”作为信号）完成后再刷生物，避免刷到房屋外/空中。
        if (!mobsSpawned && allLootApplied) {
            for (spawnRule in mobSpawns) {
                try {
                    // 计算旋转后的偏移位置
                    val rotatedOffset = transformOffset(spawnRule.offset, rotation)
                    val spawnPos = anchorPos.offset(rotatedOffset)
                    spawnMob(level, spawnPos, spawnRule, random)
                } catch (e: Exception) {
                    LOGGER.warn("生成生物失败 at {}: {}", anchorPos, e.message)
                }
            }
            mobsSpawned = true
        }
    }

    private fun initPostProcessState() {
        if (lootApplied == null || lootApplied?.size != lootConfigs.size) {
            lootApplied = BooleanArray(lootConfigs.size)
            lootApplyAttempts = IntArray(lootConfigs.size)
        }
    }

    /**
     * 根据结构旋转变换偏移坐标
     */
    private fun transformOffset(offset: Vec3i, rotation: Rotation): BlockPos {
        // 根据旋转变换 XZ 坐标
        return when (rotation) {
            Rotation.NONE -> BlockPos(offset.x, offset.y, offset.z)
            Rotation.CLOCKWISE_90 -> BlockPos(-offset.z, offset.y, offset.x)
            Rotation.CLOCKWISE_180 -> BlockPos(-offset.x, offset.y, -offset.z)
            Rotation.COUNTERCLOCKWISE_90 -> BlockPos(offset.z, offset.y, -offset.x)
        }
    }

    /**
     * 为容器设置战利品表
     */
    private fun applyLootTable(level: WorldGenLevel, containerPos: BlockPos, config: LootConfig, random: RandomSource): Boolean {
        // 概率检查
        if (config.chance.toDouble() < 1.0 && random.nextFloat().toDouble() > config.chance.toDouble()) {
            return true
        }

        val blockEntity = level.getBlockEntity(containerPos)
        val container = blockEntity as? RandomizableContainerBlockEntity
        return if (container !== null) {
            container.setLootTable(config.lootTable, random.nextLong())
            LOGGER.debug("设置战利品表 {} at {}", config.lootTable, containerPos)
            true
        } else {
            LOGGER.debug("位置 {} 没有找到容器方块实体（可能该区块还未放置到这里）", containerPos)
            false
        }
    }

    /**
     * 生成生物
     */
    private fun spawnMob(level: WorldGenLevel, spawnPos: BlockPos, rule: MobSpawnRule, random: RandomSource) {
        // 概率检查
        if (rule.chance.toDouble() < 1.0 && random.nextFloat().toDouble() > rule.chance.toDouble()) {
            return
        }

        // 软依赖：实体不存在时跳过（例如未安装对应前置模组）
        val resolvedType: EntityType<*>? = rule.resolveEntityType().orElse(null)
        if (resolvedType === null) {
            return
        }

        // 计算生成数量
        var count = rule.countMin
        if (rule.countMax > rule.countMin) {
            count = rule.countMin + random.nextInt(rule.countMax - rule.countMin + 1)
        }

        for (i in 0 until count) {
            // 之前使用 [-1, 1] 的随机偏移，容易把生物刷到房屋外。
            // 这里缩小随机范围，尽量保证生成点仍在室内。
            val spread = 0.3
            val x = spawnPos.x + 0.5 + (random.nextDouble() - 0.5) * spread * 2
            val y = spawnPos.y.toDouble()
            val z = spawnPos.z + 0.5 + (random.nextDouble() - 0.5) * spread * 2

            val entity = resolvedType.create(level.level) ?: continue
            entity.moveTo(x, y, z, random.nextFloat() * 360.0f, 0.0f)

            // 如果是 Mob，调用 finalizeSpawn 进行初始化
            if (entity is Mob) {
                entity.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.STRUCTURE, null, null)
                entity.setPersistenceRequired()
            }

            if (level.addFreshEntity(entity)) {
                LOGGER.debug("生成生物 {} at ({}, {}, {})", rule.entityId, x, y, z)
            }
        }
    }

    /**
     * 获取模板 ID
     */
    fun getTemplateId(): ResourceLocation = templateId

    override fun getType(): StructurePieceType {
        return requireNotNull(ModStructurePieceTypes.SIMPLE_TEMPLATE) {
            "ModStructurePieceTypes.SIMPLE_TEMPLATE 尚未注册（平台初始化顺序错误）"
        }
    }

    // ==================== 序列化/反序列化辅助方法 ====================

    private fun serializeMobSpawns(tag: CompoundTag) {
        if (mobSpawns.isEmpty()) return

        val listTag = ListTag()
        for (rule in mobSpawns) {
            val result: DataResult<Tag> = MobSpawnRule.CODEC.encodeStart(NbtOps.INSTANCE, rule)
            result.result().ifPresent(listTag::add)
        }
        if (!listTag.isEmpty()) {
            tag.put("MobSpawns", listTag)
        }
    }

    private fun serializeLootConfigs(tag: CompoundTag) {
        if (lootConfigs.isEmpty()) return

        val listTag = ListTag()
        for (config in lootConfigs) {
            val result: DataResult<Tag> = LootConfig.CODEC.encodeStart(NbtOps.INSTANCE, config)
            result.result().ifPresent(listTag::add)
        }
        if (!listTag.isEmpty()) {
            tag.put("LootConfigs", listTag)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("RoadWeaver/SimpleTemplatePiece")

        /**
         * 创建放置设置
         */
        private fun createPlaceSettings(rotation: Rotation, mirror: Mirror): StructurePlaceSettings {
            return StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(mirror)
                .setIgnoreEntities(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
        }

        private fun deserializeMobSpawns(tag: CompoundTag): List<MobSpawnRule> {
            if (!tag.contains("MobSpawns")) return emptyList()

            val result = ArrayList<MobSpawnRule>()
            val listTag = tag.getList("MobSpawns", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until listTag.size) {
                val ruleTag = listTag.getCompound(i)
                val parseResult: DataResult<MobSpawnRule> = MobSpawnRule.CODEC.parse(NbtOps.INSTANCE, ruleTag)
                parseResult.result().ifPresent(result::add)
            }
            return result
        }

        private fun deserializeLootConfigs(tag: CompoundTag): List<LootConfig> {
            if (!tag.contains("LootConfigs")) return emptyList()

            val result = ArrayList<LootConfig>()
            val listTag = tag.getList("LootConfigs", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until listTag.size) {
                val configTag = listTag.getCompound(i)
                val parseResult: DataResult<LootConfig> = LootConfig.CODEC.parse(NbtOps.INSTANCE, configTag)
                parseResult.result().ifPresent(result::add)
            }
            return result
        }
    }
}
