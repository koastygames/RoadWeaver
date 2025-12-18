package net.shiroha233.roadweaver.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.architectury.platform.Platform
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicReference

object PresetService {
    private val LOGGER: Logger = LoggerFactory.getLogger("roadweaver")
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private const val BASE_DIR: String = "roadweaver"
    private const val PRESET_DIR: String = "presets"

    private val PRESETS: AtomicReference<Map<String, PresetDef>> = AtomicReference(LinkedHashMap())

    @JvmStatic
    @Synchronized
    fun reload() {
        val cfgRoot: Path = Platform.getConfigFolder()
        val baseDir: Path = cfgRoot.resolve(BASE_DIR)
        val presetDir: Path = baseDir.resolve(PRESET_DIR)

        var map: MutableMap<String, PresetDef> = LinkedHashMap()

        try {
            try {
                Files.createDirectories(presetDir)
            } catch (e: Exception) {
                LOGGER.warn("Failed to create preset directory: {}", presetDir, e)
            }

            Files.newDirectoryStream(presetDir, "*.json").use { ds: DirectoryStream<Path> ->
                for (p in ds) {
                    val dto = readPresetFile(p) ?: continue
                    var id = dto.id
                    if (id === null || id.isBlank()) id = stripExt(p.fileName.toString())

                    val mats: List<String> = dto.materials ?: listOf()
                    val valid: MutableList<String> = ArrayList()
                    for (s in mats) {
                        try {
                            val rl = ResourceLocation(s)
                            val b: Block = BuiltInRegistries.BLOCK.get(rl)
                            if (b !== Blocks.AIR) valid.add(s)
                        } catch (_: Throwable) {
                        }
                    }

                    val slabIds: List<String> = dto.slabMaterials ?: listOf()
                    val validSlabs: MutableList<String> = ArrayList()
                    for (s in slabIds) {
                        try {
                            val rl = ResourceLocation(s)
                            val b: Block = BuiltInRegistries.BLOCK.get(rl)
                            if (b !== Blocks.AIR) validSlabs.add(s)
                        } catch (_: Throwable) {
                        }
                    }

                    if (valid.isEmpty()) {
                        LOGGER.warn("Skip preset {} due to empty/invalid materials", p.fileName)
                        continue
                    }

                    val fileId: String = id
                    val rawName: String? = dto.name
                    val name: String = if (rawName === null || rawName.isBlank()) fileId else rawName
                    val def = PresetDef(fileId, name, Collections.unmodifiableList(valid), Collections.unmodifiableList(validSlabs))

                    if (map.containsKey(id)) {
                        LOGGER.warn("Duplicate preset id '{}', file {} is ignored", id, p.fileName)
                        continue
                    }

                    map[id] = def
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Failed scanning presets: {}", presetDir, e)
        }

        if (map.isEmpty()) {
            try {
                writeSamplePresets(presetDir)
            } catch (e: Exception) {
                LOGGER.warn("Failed to write sample presets: {}", e.toString())
            }
            map = defaultPresets().toMutableMap()
        }

        PRESETS.set(map)
        LOGGER.info("Presets loaded: {} entries", map.size)
    }

    private fun stripExt(fn: String): String {
        val i = fn.lastIndexOf('.')
        return if (i > 0) fn.substring(0, i) else fn
    }

    private fun readPresetFile(p: Path): PresetFile? {
        return try {
            Files.newBufferedReader(p, StandardCharsets.UTF_8).use { br: BufferedReader ->
                GSON.fromJson(br, PresetFile::class.java)
            }
        } catch (e: Exception) {
            LOGGER.warn("Failed to read preset file {}: {}", p.fileName, e.toString())
            null
        }
    }

    private fun writeSamplePresets(dir: Path) {
        try {
            Files.createDirectories(dir)
        } catch (_: Exception) {
        }

        val a = PresetFile().apply {
            id = "stone_street"
            name = "Stone Street"
            materials = listOf("minecraft:stone_bricks", "minecraft:polished_andesite")
            slabMaterials = listOf("minecraft:stone_brick_slab", "minecraft:polished_andesite_slab")
        }
        val b = PresetFile().apply {
            id = "mud_road"
            name = "Mud Road"
            materials = listOf("minecraft:mud_bricks", "minecraft:packed_mud")
            slabMaterials = listOf("minecraft:mud_brick_slab")
        }
        val c = PresetFile().apply {
            id = "aged_stone"
            name = "Aged Stone"
            materials = listOf("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks")
            slabMaterials = listOf("minecraft:stone_brick_slab", "minecraft:mossy_stone_brick_slab")
        }

        writePreset(dir.resolve("stone_street.json"), a)
        writePreset(dir.resolve("mud_road.json"), b)
        writePreset(dir.resolve("aged_stone.json"), c)
    }

    private fun writePreset(file: Path, dto: PresetFile) {
        try {
            Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { bw: BufferedWriter ->
                GSON.toJson(dto, bw)
            }
        } catch (_: Exception) {
        }
    }

    private fun defaultPresets(): Map<String, PresetDef> {
        val m: MutableMap<String, PresetDef> = LinkedHashMap()

        val a = PresetDef(
            "mud_road",
            "Mud Road",
            listOf("minecraft:mud_bricks", "minecraft:packed_mud"),
            listOf("minecraft:mud_brick_slab")
        )
        val b = PresetDef(
            "stone_street",
            "Stone Street",
            listOf("minecraft:polished_andesite", "minecraft:stone_bricks"),
            listOf("minecraft:polished_andesite_slab", "minecraft:stone_brick_slab")
        )
        val c = PresetDef(
            "aged_stone",
            "Aged Stone",
            listOf("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"),
            listOf("minecraft:stone_brick_slab", "minecraft:mossy_stone_brick_slab")
        )

        m[a.id] = a
        m[b.id] = b
        m[c.id] = c

        return m
    }

    @JvmStatic
    @Synchronized
    fun getAllPresets(): List<PresetDef> {
        if (PRESETS.get().isEmpty()) reload()
        return PRESETS.get().values.toList()
    }

    @JvmStatic
    @Synchronized
    fun choosePresetForArtificial(rnd: RandomSource, cfg: ModConfig): PresetDef {
        if (PRESETS.get().isEmpty()) reload()
        val all = PRESETS.get()
        var pool: MutableList<PresetDef> = ArrayList(all.values)
        if (pool.isEmpty()) {
            pool = ArrayList(defaultPresets().values)
        }
        return pickPreset(rnd, pool)
    }

    @JvmStatic
    @Synchronized
    fun chooseMaterialsForArtificial(rnd: RandomSource, cfg: ModConfig): List<BlockState> {
        val chosen = choosePresetForArtificial(rnd, cfg)
        return toBlockStates(chosen.materials)
    }

    private fun pickPreset(rnd: RandomSource, pool: List<PresetDef>): PresetDef {
        return pool[rnd.nextInt(pool.size)]
    }

    private fun toBlockStates(ids: List<String>?): List<BlockState> {
        val out: MutableList<BlockState> = ArrayList()
        if (ids == null) return out

        for (s in ids) {
            try {
                val rl = ResourceLocation(s)
                val b: Block = BuiltInRegistries.BLOCK.get(rl)
                if (b !== Blocks.AIR) out.add(b.defaultBlockState())
            } catch (_: Throwable) {
            }
        }

        if (out.isEmpty()) out.add(Blocks.STONE_BRICKS.defaultBlockState())
        return out
    }

    @JvmStatic
    fun toBlockStatesFromIds(ids: List<String>?): List<BlockState> = toBlockStates(ids)

    @JvmStatic
    @Synchronized
    fun getMaterialCombos(): List<List<String>> {
        if (PRESETS.get().isEmpty()) reload()
        val combos: MutableList<List<String>> = ArrayList()
        for (d in PRESETS.get().values) combos.add(d.materials)
        return combos
    }

    @JvmStatic
    @Synchronized
    fun saveOrUpdatePresetFile(id: String?, name: String?, materials: List<String>?) {
        saveOrUpdatePresetFile(id, name, materials, null)
    }

    @JvmStatic
    @Synchronized
    fun saveOrUpdatePresetFile(id: String?, name: String?, materials: List<String>?, slabMaterials: List<String>?) {
        if (id === null || id.isBlank()) {
            return
        }

        val cfgRoot: Path = Platform.getConfigFolder()
        val baseDir: Path = cfgRoot.resolve(BASE_DIR)
        val presetDir: Path = baseDir.resolve(PRESET_DIR)

        try {
            Files.createDirectories(presetDir)
        } catch (e: Exception) {
            LOGGER.warn("Failed to create preset directory: {}", presetDir, e)
        }

        val dto = PresetFile().apply {
            this.id = id
            this.name = name
            this.materials = if (materials == null) listOf() else ArrayList(materials)
            this.slabMaterials = if (slabMaterials == null) listOf() else ArrayList(slabMaterials)
        }

        writePreset(presetDir.resolve("$id.json"), dto)
    }

    @JvmStatic
    @Synchronized
    fun deletePresetFile(id: String?) {
        if (id === null || id.isBlank()) {
            return
        }

        val cfgRoot: Path = Platform.getConfigFolder()
        val baseDir: Path = cfgRoot.resolve(BASE_DIR)
        val presetDir: Path = baseDir.resolve(PRESET_DIR)

        try {
            Files.deleteIfExists(presetDir.resolve("$id.json"))
        } catch (e: Exception) {
            LOGGER.warn("Failed to delete preset file for id {}: {}", id, e.toString())
        }
    }

    private class PresetFile {
        @JvmField var id: String? = null
        @JvmField var name: String? = null
        @JvmField var materials: List<String>? = null
        @JvmField var slabMaterials: List<String>? = null
    }

    data class PresetDef(
        val id: String,
        val name: String,
        val materials: List<String>,
        val slabMaterials: List<String>
    ) {
        fun id(): String = id

        fun name(): String = name

        fun materials(): List<String> = materials

        fun slabMaterials(): List<String> = slabMaterials
    }
}
