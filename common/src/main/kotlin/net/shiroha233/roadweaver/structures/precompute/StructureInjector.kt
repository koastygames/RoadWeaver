package net.shiroha233.roadweaver.structures.precompute

import net.minecraft.core.SectionPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureStart
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece
import net.shiroha233.roadweaver.structures.types.RoadsideStructure
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure
import org.slf4j.LoggerFactory

/**
 * 结构注入器
 *
 * 在区块 STRUCTURE_STARTS 阶段被 Mixin 调用，
 * 将预计算的路边结构注入到区块的结构数据中。
 */
object StructureInjector {
    private val LOGGER = LoggerFactory.getLogger("RoadWeaver/StructureInjector")

    /**
     * 在区块的 STRUCTURE_STARTS 阶段注入预计算的结构
     */
    @JvmStatic
    fun injectPendingStructures(level: ServerLevel, chunk: ChunkAccess) {
        val chunkPos = chunk.pos

        // 获取该区块的待放置结构
        val pending = PendingStructureStorage.getPendingStructures(level, chunkPos)
        if (pending.isEmpty()) {
            return
        }

        // 获取必要的管理器
        val structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
        val structureManager = level.structureManager()
        val templateManager = level.server.structureManager

        var injectedCount = 0

        for (pendingStructure in pending) {
            try {
                // 获取结构定义
                val structure: Structure? = structureRegistry[pendingStructure.structureId]
                if (structure == null) {
                    LOGGER.warn("Structure {} not found in registry, skipping", pendingStructure.structureId)
                    continue
                }

                // 创建结构片段（支持 RoadsideStructure 和 SpawnCabinStructure）
                val piece: SimpleTemplatePiece = when (structure) {
                    is RoadsideStructure -> {
                        // 路边结构：包含生物生成和战利品配置
                        SimpleTemplatePiece(
                            templateManager,
                            structure.templateId(),
                            pendingStructure.anchor,
                            pendingStructure.rotation,
                            Mirror.NONE,
                            structure.mobSpawns(),
                            structure.lootConfigs()
                        )
                    }

                    is SpawnCabinStructure -> {
                        // 初始小屋：暂不支持生物/战利品
                        SimpleTemplatePiece(
                            templateManager,
                            structure.templateId(),
                            pendingStructure.anchor,
                            pendingStructure.rotation,
                            Mirror.NONE
                        )
                    }

                    else -> {
                        LOGGER.warn("Structure {} is not a supported type, skipping", pendingStructure.structureId)
                        continue
                    }
                }

                // 创建 StructureStart
                val start = StructureStart(
                    structure,
                    chunkPos,
                    0,
                    PiecesContainer(listOf(piece))
                )

                // 注入到区块（使用 section 0，原版也是这样做的）
                val sectionPos = SectionPos.of(ChunkPos(chunkPos.x, chunkPos.z), 0)
                structureManager.setStartForStructure(sectionPos, structure, start, chunk)

                injectedCount++
            } catch (e: Exception) {
                LOGGER.error(
                    "Failed to inject structure {} at {}",
                    pendingStructure.structureId,
                    pendingStructure.anchor,
                    e
                )
            }
        }

        if (injectedCount > 0) {
            LOGGER.debug("Injected {} roadside structures into chunk [{}, {}]", injectedCount, chunkPos.x, chunkPos.z)
        }

        // 标记已处理
        PendingStructureStorage.markAsInjected(level, chunkPos)
    }
}
