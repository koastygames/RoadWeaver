package net.shiroha233.roadweaver.search

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.QuartPos
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureSet
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement
import net.shiroha233.roadweaver.config.structure.StructureSelectionConfig
import net.shiroha233.roadweaver.helpers.Records
import java.util.Locale

object StructurePredictor {
    @JvmStatic
    fun predictOverworldVillagesAroundSpawn(level: ServerLevel, radiusChunks: Int, biomePrefilter: Boolean): List<Records.StructureInfo> {
        val registryAccess: RegistryAccess = level.registryAccess()
        val setRegistry: Registry<StructureSet> = registryAccess.registryOrThrow(Registries.STRUCTURE_SET)
        val optVillages = setRegistry.getHolder(BuiltinStructureSets.VILLAGES)
        if (optVillages.isEmpty) return listOf()
        val set = optVillages.get().value()
        val placement = set.placement()
        val rssp = placement as? RandomSpreadStructurePlacement ?: return listOf()

        val spawn = level.sharedSpawnPos
        val cx = spawn.x shr 4
        val cz = spawn.z shr 4
        val minX = cx - radiusChunks
        val maxX = cx + radiusChunks
        val minZ = cz - radiusChunks
        val maxZ = cz + radiusChunks

        val state: ChunkGeneratorStructureState = level.chunkSource.generatorState
        val randomState: RandomState = state.randomState()
        val biomeSource: BiomeSource = level.chunkSource.generator.biomeSource

        var allowedBiomes: MutableSet<Holder<Biome>>? = null
        if (biomePrefilter) {
            allowedBiomes = HashSet()
            for (entry in set.structures()) {
                val structure = entry.structure().value()
                for (b in structure.biomes()) {
                    allowedBiomes.add(b)
                }
            }
        }

        val spacing = rssp.spacing()
        val startI = Math.floorDiv(minX, spacing)
        val endI = Math.floorDiv(maxX, spacing)
        val startJ = Math.floorDiv(minZ, spacing)
        val endJ = Math.floorDiv(maxZ, spacing)

        val seed = level.seed
        val result = ArrayList<Records.StructureInfo>()

        for (i in startI..endI) {
            for (j in startJ..endJ) {
                val baseX = i * spacing
                val baseZ = j * spacing
                val candidate = rssp.getPotentialStructureChunk(seed, baseX, baseZ)
                val x = candidate.x
                val z = candidate.z
                if (x < minX || x > maxX || z < minZ || z > maxZ) continue
                if (!placement.isStructureChunk(state, x, z)) continue

                val locatePos = placement.getLocatePos(candidate)
                if (biomePrefilter && allowedBiomes != null) {
                    val qx = QuartPos.fromBlock(locatePos.x)
                    val qy = QuartPos.fromBlock(64)
                    val qz = QuartPos.fromBlock(locatePos.z)
                    val sample = biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler())
                    if (!allowedBiomes.contains(sample)) continue
                }

                result.add(Records.StructureInfo(locatePos, "village"))
            }
        }

        return result
    }

    @JvmStatic
    fun predictOverworldStructuresInRect(
        level: ServerLevel,
        minChunkX: Int,
        minChunkZ: Int,
        maxChunkX: Int,
        maxChunkZ: Int,
        biomePrefilter: Boolean,
        whitelist: List<String>?,
        blacklist: List<String>?
    ): List<Records.StructureInfo> {
        val access = level.registryAccess()
        val setRegistry: Registry<StructureSet> = access.registryOrThrow(Registries.STRUCTURE_SET)

        val state = level.chunkSource.generatorState
        val randomState = state.randomState()
        val biomeSource = level.chunkSource.generator.biomeSource

        val filters = Filters.of(whitelist, blacklist)
        val result = ArrayList<Records.StructureInfo>()

        for (holder in setRegistry.holders().toList()) {
            val set = holder.value()
            val placement = set.placement()
            val rssp = placement as? RandomSpreadStructurePlacement ?: continue

            val matchedStructures = ArrayList<Holder<Structure>>()
            for (entry in set.structures()) {
                val structureHolder = entry.structure()
                val key = structureHolder.unwrapKey()
                if (key.isEmpty) continue
                val id = key.get().location()
                if (filters.matches(structureHolder, id)) {
                    matchedStructures.add(structureHolder)
                }
            }

            if (matchedStructures.isEmpty()) {
                if (filters.hasWhitelist()) continue
                for (entry in set.structures()) {
                    val structureHolder = entry.structure()
                    val key = structureHolder.unwrapKey()
                    if (key.isEmpty) continue
                    val id = key.get().location()
                    if (!filters.isBlacklisted(structureHolder, id)) {
                        matchedStructures.add(structureHolder)
                    }
                }
                if (matchedStructures.isEmpty()) continue
            }

            var allowedBiomes: MutableSet<Holder<Biome>>? = null
            if (biomePrefilter) {
                allowedBiomes = HashSet()
                for (h in matchedStructures) {
                    for (b in h.value().biomes()) {
                        allowedBiomes.add(b)
                    }
                }
            }

            val spacing = rssp.spacing()
            val startI = Math.floorDiv(minChunkX, spacing)
            val endI = Math.floorDiv(maxChunkX, spacing)
            val startJ = Math.floorDiv(minChunkZ, spacing)
            val endJ = Math.floorDiv(maxChunkZ, spacing)

            val labelId = matchedStructures
                .asSequence()
                .map { h -> h.unwrapKey().map { it.location().toString() }.orElse("structure") }
                .firstOrNull() ?: "structure"

            for (i in startI..endI) {
                for (j in startJ..endJ) {
                    val baseX = i * spacing
                    val baseZ = j * spacing
                    val candidate = rssp.getPotentialStructureChunk(level.seed, baseX, baseZ)
                    val x = candidate.x
                    val z = candidate.z
                    if (x < minChunkX || x > maxChunkX || z < minChunkZ || z > maxChunkZ) continue
                    if (!placement.isStructureChunk(state, x, z)) continue

                    val locatePos = placement.getLocatePos(candidate)
                    val qx = QuartPos.fromBlock(locatePos.x)
                    val qy = QuartPos.fromBlock(64)
                    val qz = QuartPos.fromBlock(locatePos.z)
                    val sample = biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler())
                    if (biomePrefilter && allowedBiomes != null) {
                        if (!allowedBiomes.contains(sample)) continue
                    }

                    var chosenId = labelId
                    for (h in matchedStructures) {
                        if (h.value().biomes().contains(sample)) {
                            chosenId = h.unwrapKey().map { it.location().toString() }.orElse(labelId)
                            break
                        }
                    }
                    result.add(Records.StructureInfo(locatePos, chosenId))
                }
            }
        }

        return result
    }

    /**
     * 预测出生点附近的结构位置（使用 StructureSelectionConfig）
     */
    @JvmStatic
    fun predictOverworldStructuresAroundSpawn(level: ServerLevel, radiusChunks: Int, biomePrefilter: Boolean): List<Records.StructureInfo> {
        val whitelist = StructureSelectionConfig.get().toWhitelist()
        return predictOverworldStructuresAroundSpawn(level, radiusChunks, biomePrefilter, whitelist, listOf())
    }

    /**
     * @deprecated 使用 predictOverworldStructuresAroundSpawn(level, radiusChunks, biomePrefilter)
     */
    @Deprecated("Use predictOverworldStructuresAroundSpawn(level, radiusChunks, biomePrefilter)")
    @JvmStatic
    fun predictOverworldStructuresAroundSpawn(
        level: ServerLevel,
        radiusChunks: Int,
        biomePrefilter: Boolean,
        whitelist: List<String>,
        blacklist: List<String>
    ): List<Records.StructureInfo> {
        val spawn = level.sharedSpawnPos
        val cx = spawn.x shr 4
        val cz = spawn.z shr 4
        return predictOverworldStructuresInRect(
            level,
            cx - radiusChunks,
            cz - radiusChunks,
            cx + radiusChunks,
            cz + radiusChunks,
            biomePrefilter,
            whitelist,
            blacklist
        )
    }

    private class Filters private constructor(
        whitelist: List<String>?,
        blacklist: List<String>?
    ) {
        private val whitelist: List<String> = normalize(whitelist)
        private val blacklist: List<String> = normalize(blacklist)

        fun hasWhitelist(): Boolean = whitelist.isNotEmpty()

        fun matches(holder: Holder<Structure>, id: ResourceLocation): Boolean {
            val whiteOk = whitelist.isEmpty() || whitelist.any { p -> matchesPattern(holder, id, p) }
            val blackHit = blacklist.any { p -> matchesPattern(holder, id, p) }
            return whiteOk && !blackHit
        }

        fun isBlacklisted(holder: Holder<Structure>, id: ResourceLocation): Boolean {
            return blacklist.any { p -> matchesPattern(holder, id, p) }
        }

        private fun matchesPattern(holder: Holder<Structure>, id: ResourceLocation, pattern: String?): Boolean {
            if (pattern.isNullOrEmpty()) return false
            val p = pattern.trim().lowercase(Locale.ROOT)
            val idStr = id.toString().lowercase(Locale.ROOT)

            if (p.startsWith("#")) {
                val raw = p.substring(1)
                val tagId = ResourceLocation.tryParse(raw) ?: return false
                val tag = TagKey.create(Registries.STRUCTURE, tagId)
                return holder.`is`(tag)
            }
            if (p.endsWith("/*")) {
                val base = p.substring(0, p.length - 2)
                return idStr.startsWith(base + "/") || idStr.startsWith(base + "_") || idStr.startsWith(base + "-") || idStr.startsWith(base + ".")
            }
            if (p.endsWith(":*")) {
                var ns = p.substring(0, p.length - 2)
                val idx = ns.indexOf(':')
                if (idx > 0) ns = ns.substring(0, idx)
                return id.namespace.equals(ns, ignoreCase = true)
            }
            return idStr == p
        }

        companion object {
            fun of(whitelist: List<String>?, blacklist: List<String>?): Filters {
                return Filters(whitelist, blacklist)
            }

            private fun normalize(src: List<String>?): List<String> {
                val out = ArrayList<String>()
                if (src == null) return out
                for (s in src) {
                    val v = s?.trim()?.lowercase(Locale.ROOT)
                    if (!v.isNullOrEmpty()) out.add(v)
                }
                return out
            }
        }
    }
}
