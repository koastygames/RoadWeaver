package net.shiroha233.roadweaver.search

import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.chunk.storage.ChunkScanAccess
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.structure.StructureCheckResult
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureCheck
import net.minecraft.world.level.levelgen.structure.StructureSet
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement
import net.shiroha233.roadweaver.helpers.Records

/**
 * 结构预测点验证服务
 */
object StructureVerificationService {
    @JvmStatic
    fun verifyPredictedStructures(level: ServerLevel, predicted: List<Records.StructureInfo>?): List<Records.StructureInfo> {
        if (predicted.isNullOrEmpty()) {
            return listOf()
        }

        val source = level.chunkSource
        val chunkCache = source as? ServerChunkCache ?: return ArrayList(predicted)
        val server = level.server ?: return ArrayList(predicted)

        val scanAccess: ChunkScanAccess = try {
            chunkCache.chunkScanner()
        } catch (_: Throwable) {
            return ArrayList(predicted)
        }

        val registryAccess: RegistryAccess = level.registryAccess()
        val generator: ChunkGenerator = chunkCache.generator
        val randomState: RandomState = chunkCache.randomState()
        val biomeSource: BiomeSource = generator.biomeSource
        // 与原版 StructurePlacement/ChunkGeneratorStructureState 保持一致
        val seed = level.chunkSource.generatorState.levelSeed

        val checker = StructureCheck(
            scanAccess,
            registryAccess,
            server.structureManager,
            level.dimension(),
            generator,
            randomState,
            level,
            biomeSource,
            seed,
            server.fixerUpper
        )

        val structureRegistry: Registry<Structure> = registryAccess.registryOrThrow(Registries.STRUCTURE)
        val structureSetRegistry: Registry<StructureSet> = registryAccess.registryOrThrow(Registries.STRUCTURE_SET)

        // 兼容性：部分结构可能出现在多个 StructureSet（不同 placement）；
        // 原版 locate 会对该结构的全部 placements 逐个 check。
        val placementCache = HashMap<Structure, List<StructurePlacement>>()
        for (setHolder in structureSetRegistry.holders().toList()) {
            val set = setHolder.value()
            val placement = set.placement()
            for (entry in set.structures()) {
                val structure = entry.structure().value()
                val prev = placementCache[structure]
                if (prev == null) {
                    placementCache[structure] = listOf(placement)
                } else if (!prev.contains(placement)) {
                    placementCache[structure] = prev + placement
                }
            }
        }

        val result = ArrayList<Records.StructureInfo>()

        for (info in predicted) {
            val idStr = info.structureId
            if (idStr.isNullOrEmpty()) {
                result.add(info)
                continue
            }

            val rl = ResourceLocation.tryParse(idStr)
            if (rl == null) {
                result.add(info)
                continue
            }

            val structure = structureRegistry.get(rl)
            if (structure == null) {
                result.add(info)
                continue
            }

            val placements = placementCache[structure]
            if (placements.isNullOrEmpty()) {
                result.add(info)
                continue
            }

            val chunkPos = ChunkPos(info.pos.x shr 4, info.pos.z shr 4)

            var best: StructureCheckResult = StructureCheckResult.START_NOT_PRESENT
            for (placement in placements) {
                val r = try {
                    checker.checkStart(chunkPos, structure, placement, false)
                } catch (_: Throwable) {
                    // 验证失败时保持“尽量不误删”策略
                    StructureCheckResult.CHUNK_LOAD_NEEDED
                }
                // START_PRESENT > CHUNK_LOAD_NEEDED > START_NOT_PRESENT
                if (r == StructureCheckResult.START_PRESENT) {
                    best = r
                    break
                }
                if (r == StructureCheckResult.CHUNK_LOAD_NEEDED) {
                    best = r
                }
            }

            if (best == StructureCheckResult.START_PRESENT || best == StructureCheckResult.CHUNK_LOAD_NEEDED) {
                result.add(info)
            }
        }

        return result
    }
}
