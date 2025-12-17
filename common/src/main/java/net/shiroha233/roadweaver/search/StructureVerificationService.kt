package net.shiroha233.roadweaver.search

import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.chunk.storage.ChunkScanAccess
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureCheck
import net.minecraft.world.level.levelgen.structure.StructureCheckResult
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

        val registryAccess = level.registryAccess()
        val generator: ChunkGenerator = chunkCache.generator
        val randomState: RandomState = chunkCache.randomState()
        val biomeSource: BiomeSource = generator.biomeSource
        val seed = level.seed

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

        val placementCache = HashMap<Structure, StructurePlacement>()
        for (setHolder in structureSetRegistry.holders().toList()) {
            val set = setHolder.value()
            val placement = set.placement()
            for (entry in set.structures()) {
                val structure = entry.structure().value()
                placementCache.putIfAbsent(structure, placement)
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

            val placement = placementCache[structure]
            if (placement == null) {
                result.add(info)
                continue
            }

            val chunkPos = ChunkPos(info.pos.x shr 4, info.pos.z shr 4)

            val checkResult = try {
                checker.checkStart(chunkPos, structure, placement, false)
            } catch (_: Throwable) {
                result.add(info)
                continue
            }

            if (checkResult == StructureCheckResult.START_PRESENT) {
                result.add(info)
            } else if (checkResult == StructureCheckResult.CHUNK_LOAD_NEEDED) {
                result.add(info)
            }
        }

        return result
    }
}
