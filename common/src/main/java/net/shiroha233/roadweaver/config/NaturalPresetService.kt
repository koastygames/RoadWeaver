package net.shiroha233.roadweaver.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.architectury.platform.Platform
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * 自然道路预设服务（单文件）。
 *
 * 设计目标：
 * - SRP：只负责自然道路“按群系”的材质预设加载/保存/校验/缓存。
 * - 运行期高性能：道路铺设时只读内存缓存，不做磁盘 IO。
 */
object NaturalPresetService {
    private val LOGGER = LoggerFactory.getLogger("roadweaver")
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private const val BASE_DIR = "roadweaver"
    private const val FILE_NAME = "natural_presets.json"

    private val PRESETS: AtomicReference<Map<String, List<String>>> = AtomicReference(LinkedHashMap())

    data class BiomeEntry(val biomeId: String, val materials: List<String>)

    private data class NaturalPresetFile(
        var biomes: List<NaturalBiomePreset>? = null
    )

    private data class NaturalBiomePreset(
        var biome: String? = null,
        var materials: List<String>? = null
    )

    @JvmStatic
    @Synchronized
    fun reload() {
        val cfgRoot = Platform.getConfigFolder()
        val baseDir = cfgRoot.resolve(BASE_DIR)
        val file = baseDir.resolve(FILE_NAME)

        try {
            Files.createDirectories(baseDir)
        } catch (e: Exception) {
            LOGGER.warn("Failed to create natural preset dir: {}", baseDir, e)
        }

        var loaded: Map<String, List<String>>? = null
        try {
            if (Files.exists(file)) {
                Files.newBufferedReader(file, StandardCharsets.UTF_8).use { br ->
                    val dto = GSON.fromJson(br, NaturalPresetFile::class.java)
                    loaded = sanitize(dto)
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("Failed to read natural preset file {}: {}", file.fileName, e.toString())
        }

        if (loaded == null || loaded!!.isEmpty()) {
            val defaults = defaultPresets()
            try {
                writeFile(file, defaults)
            } catch (e: Exception) {
                LOGGER.warn("Failed to write default natural preset file: {}", e.toString())
            }
            loaded = defaults
        }

        PRESETS.set(loaded!!)
        LOGGER.info("Natural presets loaded: {} entries", loaded!!.size)
    }

    @JvmStatic
    fun getAllEntries(): Map<String, List<String>> {
        if (PRESETS.get().isEmpty()) reload()
        return PRESETS.get()
    }

    @JvmStatic
    fun save(entries: Map<String, List<String>>) {
        val cfgRoot = Platform.getConfigFolder()
        val baseDir = cfgRoot.resolve(BASE_DIR)
        val file = baseDir.resolve(FILE_NAME)

        try {
            Files.createDirectories(baseDir)
        } catch (e: Exception) {
            LOGGER.warn("Failed to create natural preset dir: {}", baseDir, e)
        }

        val sanitized = sanitizeFromMap(entries)
        try {
            writeFile(file, sanitized)
        } catch (e: Exception) {
            LOGGER.warn("Failed to write natural preset file {}: {}", file.fileName, e.toString())
        }
        PRESETS.set(sanitized)
    }

    /**
     * 给 Java 侧（BiomeRoadMaterialSelector）调用：返回自定义材质（若无则返回 null）。
     */
    @JvmStatic
    fun chooseCustomMaterialsForBiomeId(biomeId: String?): List<BlockState>? {
        if (biomeId == null || biomeId.isBlank()) return null
        if (PRESETS.get().isEmpty()) reload()
        val ids = PRESETS.get()[biomeId] ?: return null
        if (ids.isEmpty()) return null
        return PresetService.toBlockStatesFromIds(ids)
    }

    private fun sanitize(dto: NaturalPresetFile?): Map<String, List<String>> {
        val out = LinkedHashMap<String, List<String>>()
        val list = dto?.biomes ?: emptyList()
        for (e in list) {
            val biome = e.biome?.trim().orEmpty()
            if (biome.isBlank()) continue
            val materials = sanitizeMaterialIds(e.materials)
            if (materials.isEmpty()) continue
            if (out.containsKey(biome)) continue
            out[biome] = materials
        }
        return out
    }

    private fun sanitizeFromMap(entries: Map<String, List<String>>?): Map<String, List<String>> {
        val out = LinkedHashMap<String, List<String>>()
        if (entries == null) return out
        for ((k, v) in entries) {
            val biome = k.trim()
            if (biome.isBlank()) continue
            val materials = sanitizeMaterialIds(v)
            if (materials.isEmpty()) continue
            if (out.containsKey(biome)) continue
            out[biome] = materials
        }
        return out
    }

    private fun sanitizeMaterialIds(list: List<String>?): List<String> {
        val inList = list ?: emptyList()
        val valid = ArrayList<String>(inList.size)
        for (s in inList) {
            val id = s.trim()
            if (id.isEmpty()) continue
            try {
                val rl = ResourceLocation.parse(id)
                val block = BuiltInRegistries.BLOCK.get(rl)
                if (block != Blocks.AIR) {
                    valid.add(id)
                }
            } catch (_: Throwable) {
            }
        }
        return valid
    }

    private fun defaultPresets(): Map<String, List<String>> {
        val m = LinkedHashMap<String, List<String>>()
        m["minecraft:plains"] = listOf("minecraft:dirt_path", "minecraft:gravel")
        m["minecraft:desert"] = listOf("minecraft:sandstone", "minecraft:cut_sandstone")
        m["minecraft:snowy_plains"] = listOf("minecraft:andesite", "minecraft:cobblestone")
        return m
    }

    private fun writeFile(file: Path, entries: Map<String, List<String>>) {
        val dto = NaturalPresetFile(
            biomes = entries.entries.map { NaturalBiomePreset(it.key, it.value) }
        )
        Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { bw ->
            GSON.toJson(dto, bw)
        }
    }
}
