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

    private val EXCLUDED_BIOMES: Set<String> = setOf(
        // 海洋：道路不会在海面铺设，这些条目只会让列表变长
        "minecraft:ocean",
        "minecraft:deep_ocean",
        "minecraft:warm_ocean",
        "minecraft:lukewarm_ocean",
        "minecraft:deep_lukewarm_ocean",
        "minecraft:cold_ocean",
        "minecraft:deep_cold_ocean",
        "minecraft:frozen_ocean",
        "minecraft:deep_frozen_ocean",

        // 地下群系：不属于地表道路预设范围
        "minecraft:dripstone_caves",
        "minecraft:lush_caves",
        "minecraft:deep_dark"
    )

    private fun shouldSkipBiome(biomeId: String): Boolean = EXCLUDED_BIOMES.contains(biomeId)

    // 缓存结构：biomeId -> NaturalBiomeDef（包含 materials 和 slabMaterials）
    private val PRESETS: AtomicReference<Map<String, NaturalBiomeDef>> = AtomicReference(LinkedHashMap())

    /**
     * 自然道路群系预设定义（内存缓存用）
     */
    data class NaturalBiomeDef(
        val materials: List<String>,
        val slabMaterials: List<String> = emptyList()
    )

    /**
     * 对外暴露的群系条目（用于 GUI 编辑）
     */
    data class BiomeEntry(
        val biomeId: String,
        val materials: List<String>,
        val slabMaterials: List<String> = emptyList()
    )

    // JSON 序列化 DTO
    private data class NaturalPresetFile(
        var biomes: List<NaturalBiomePreset>? = null
    )

    private data class NaturalBiomePreset(
        var biome: String? = null,
        var materials: List<String>? = null,
        var slabMaterials: List<String>? = null
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

        var loaded: Map<String, NaturalBiomeDef>? = null
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

    /**
     * 获取所有群系条目（用于 GUI 编辑）
     */
    @JvmStatic
    fun getAllEntries(): List<BiomeEntry> {
        if (PRESETS.get().isEmpty()) reload()
        return PRESETS.get().entries.map { (k, v) ->
            BiomeEntry(k, v.materials, v.slabMaterials)
        }
    }

    /**
     * 保存所有群系条目（从 GUI 编辑器调用）
     */
    @JvmStatic
    fun save(entries: List<BiomeEntry>) {
        val cfgRoot = Platform.getConfigFolder()
        val baseDir = cfgRoot.resolve(BASE_DIR)
        val file = baseDir.resolve(FILE_NAME)

        try {
            Files.createDirectories(baseDir)
        } catch (e: Exception) {
            LOGGER.warn("Failed to create natural preset dir: {}", baseDir, e)
        }

        val sanitized = sanitizeFromEntries(entries)
        try {
            writeFile(file, sanitized)
        } catch (e: Exception) {
            LOGGER.warn("Failed to write natural preset file {}: {}", file.fileName, e.toString())
        }
        PRESETS.set(sanitized)
    }

    /**
     * 给 Java 侧（BiomeRoadMaterialSelector）调用：返回自定义基础材质（若无则返回 null）。
     */
    @JvmStatic
    fun chooseCustomMaterialsForBiomeId(biomeId: String?): List<BlockState>? {
        if (biomeId == null || biomeId.isBlank()) return null
        if (PRESETS.get().isEmpty()) reload()
        val def = PRESETS.get()[biomeId] ?: return null
        if (def.materials.isEmpty()) return null
        return PresetService.toBlockStatesFromIds(def.materials)
    }

    /**
     * 给 Java 侧（BiomeRoadMaterialSelector）调用：返回自定义半砖材质（若无则返回空列表）。
     */
    @JvmStatic
    fun chooseCustomSlabMaterialsForBiomeId(biomeId: String?): List<BlockState> {
        if (biomeId == null || biomeId.isBlank()) return emptyList()
        if (PRESETS.get().isEmpty()) reload()
        val def = PRESETS.get()[biomeId] ?: return emptyList()
        if (def.slabMaterials.isEmpty()) return emptyList()
        return PresetService.toBlockStatesFromIds(def.slabMaterials)
    }

    private fun sanitize(dto: NaturalPresetFile?): Map<String, NaturalBiomeDef> {
        val out = LinkedHashMap<String, NaturalBiomeDef>()
        val list = dto?.biomes ?: emptyList()
        for (e in list) {
            val biome = e.biome?.trim().orEmpty()
            if (biome.isBlank()) continue
            if (shouldSkipBiome(biome)) continue
            val materials = sanitizeMaterialIds(e.materials)
            if (materials.isEmpty()) continue
            if (out.containsKey(biome)) continue
            val slabMaterials = sanitizeMaterialIds(e.slabMaterials)
            out[biome] = NaturalBiomeDef(materials, slabMaterials)
        }
        return out
    }

    private fun sanitizeFromEntries(entries: List<BiomeEntry>?): Map<String, NaturalBiomeDef> {
        val out = LinkedHashMap<String, NaturalBiomeDef>()
        if (entries == null) return out
        for (e in entries) {
            val biome = e.biomeId.trim()
            if (biome.isBlank()) continue
            if (shouldSkipBiome(biome)) continue
            val materials = sanitizeMaterialIds(e.materials)
            if (materials.isEmpty()) continue
            if (out.containsKey(biome)) continue
            val slabMaterials = sanitizeMaterialIds(e.slabMaterials)
            out[biome] = NaturalBiomeDef(materials, slabMaterials)
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

    /**
     * 原版主世界所有群系的默认自然道路预设。
     * 设计原则：材质应融入环境、美观自然，半砖用于坡度平滑过渡。
     */
    private fun defaultPresets(): Map<String, NaturalBiomeDef> {
        val m = LinkedHashMap<String, NaturalBiomeDef>()

        // ===== 平原类 =====
        m["minecraft:plains"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:gravel", "minecraft:coarse_dirt"),
            listOf("minecraft:oak_slab")
        )
        m["minecraft:sunflower_plains"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:gravel", "minecraft:coarse_dirt"),
            listOf("minecraft:oak_slab")
        )
        m["minecraft:meadow"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:moss_block", "minecraft:coarse_dirt"),
            listOf("minecraft:oak_slab")
        )

        // ===== 森林类 =====
        m["minecraft:forest"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
            listOf("minecraft:oak_slab")
        )
        m["minecraft:flower_forest"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:moss_block"),
            listOf("minecraft:oak_slab")
        )
        m["minecraft:birch_forest"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:gravel"),
            listOf("minecraft:birch_slab")
        )
        m["minecraft:old_growth_birch_forest"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
            listOf("minecraft:birch_slab")
        )
        m["minecraft:dark_forest"] = NaturalBiomeDef(
            listOf("minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:podzol"),
            listOf("minecraft:dark_oak_slab")
        )
        m["minecraft:cherry_grove"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:moss_block", "minecraft:pink_petals"),
            listOf("minecraft:cherry_slab")
        )

        // ===== 针叶林类 =====
        m["minecraft:taiga"] = NaturalBiomeDef(
            listOf("minecraft:coarse_dirt", "minecraft:podzol", "minecraft:gravel"),
            listOf("minecraft:spruce_slab")
        )
        m["minecraft:old_growth_pine_taiga"] = NaturalBiomeDef(
            listOf("minecraft:podzol", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
            listOf("minecraft:spruce_slab")
        )
        m["minecraft:old_growth_spruce_taiga"] = NaturalBiomeDef(
            listOf("minecraft:podzol", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
            listOf("minecraft:spruce_slab")
        )
        m["minecraft:snowy_taiga"] = NaturalBiomeDef(
            listOf("minecraft:snow_block", "minecraft:coarse_dirt", "minecraft:gravel"),
            listOf("minecraft:spruce_slab")
        )

        // ===== 雪地/冰原类 =====
        m["minecraft:snowy_plains"] = NaturalBiomeDef(
            listOf("minecraft:snow_block", "minecraft:packed_ice", "minecraft:gravel"),
            listOf("minecraft:spruce_slab")
        )
        m["minecraft:ice_spikes"] = NaturalBiomeDef(
            listOf("minecraft:packed_ice", "minecraft:blue_ice", "minecraft:snow_block"),
            listOf("minecraft:spruce_slab")
        )
        m["minecraft:snowy_slopes"] = NaturalBiomeDef(
            listOf("minecraft:snow_block", "minecraft:powder_snow", "minecraft:stone"),
            listOf("minecraft:stone_slab")
        )
        m["minecraft:frozen_peaks"] = NaturalBiomeDef(
            listOf("minecraft:packed_ice", "minecraft:snow_block", "minecraft:stone"),
            listOf("minecraft:stone_slab")
        )
        m["minecraft:grove"] = NaturalBiomeDef(
            listOf("minecraft:snow_block", "minecraft:powder_snow", "minecraft:dirt_path"),
            listOf("minecraft:spruce_slab")
        )

        // ===== 山地类 =====
        m["minecraft:windswept_hills"] = NaturalBiomeDef(
            listOf("minecraft:stone", "minecraft:gravel", "minecraft:cobblestone"),
            listOf("minecraft:stone_slab", "minecraft:cobblestone_slab")
        )
        m["minecraft:windswept_gravelly_hills"] = NaturalBiomeDef(
            listOf("minecraft:gravel", "minecraft:stone", "minecraft:cobblestone"),
            listOf("minecraft:stone_slab")
        )
        m["minecraft:windswept_forest"] = NaturalBiomeDef(
            listOf("minecraft:coarse_dirt", "minecraft:stone", "minecraft:gravel"),
            listOf("minecraft:oak_slab")
        )
        m["minecraft:stony_peaks"] = NaturalBiomeDef(
            listOf("minecraft:stone", "minecraft:calcite", "minecraft:andesite"),
            listOf("minecraft:stone_slab", "minecraft:andesite_slab")
        )
        m["minecraft:jagged_peaks"] = NaturalBiomeDef(
            listOf("minecraft:stone", "minecraft:snow_block", "minecraft:packed_ice"),
            listOf("minecraft:stone_slab")
        )

        // ===== 沙漠类 =====
        m["minecraft:desert"] = NaturalBiomeDef(
            listOf("minecraft:sandstone", "minecraft:smooth_sandstone", "minecraft:sand"),
            listOf("minecraft:sandstone_slab", "minecraft:smooth_sandstone_slab")
        )

        // ===== 恶地类 =====
        m["minecraft:badlands"] = NaturalBiomeDef(
            listOf("minecraft:red_sand", "minecraft:terracotta", "minecraft:orange_terracotta"),
            listOf("minecraft:red_sandstone_slab")
        )
        m["minecraft:eroded_badlands"] = NaturalBiomeDef(
            listOf("minecraft:red_sand", "minecraft:terracotta", "minecraft:red_terracotta"),
            listOf("minecraft:red_sandstone_slab")
        )
        m["minecraft:wooded_badlands"] = NaturalBiomeDef(
            listOf("minecraft:coarse_dirt", "minecraft:terracotta", "minecraft:red_sand"),
            listOf("minecraft:red_sandstone_slab", "minecraft:oak_slab")
        )

        // ===== 丛林类 =====
        m["minecraft:jungle"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:rooted_dirt", "minecraft:mud"),
            listOf("minecraft:jungle_slab")
        )
        m["minecraft:sparse_jungle"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:gravel"),
            listOf("minecraft:jungle_slab")
        )
        m["minecraft:bamboo_jungle"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:podzol", "minecraft:rooted_dirt"),
            listOf("minecraft:bamboo_slab", "minecraft:jungle_slab")
        )

        // ===== 沼泽类 =====
        m["minecraft:swamp"] = NaturalBiomeDef(
            listOf("minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:dirt_path"),
            listOf("minecraft:mud_brick_slab", "minecraft:oak_slab")
        )
        m["minecraft:mangrove_swamp"] = NaturalBiomeDef(
            listOf("minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:mangrove_roots"),
            listOf("minecraft:mud_brick_slab", "minecraft:mangrove_slab")
        )

        // ===== 热带草原类 =====
        m["minecraft:savanna"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:gravel"),
            listOf("minecraft:acacia_slab")
        )
        m["minecraft:savanna_plateau"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:stone"),
            listOf("minecraft:acacia_slab")
        )
        m["minecraft:windswept_savanna"] = NaturalBiomeDef(
            listOf("minecraft:coarse_dirt", "minecraft:stone", "minecraft:gravel"),
            listOf("minecraft:acacia_slab", "minecraft:stone_slab")
        )

        // ===== 海滩/河流类 =====
        m["minecraft:beach"] = NaturalBiomeDef(
            listOf("minecraft:sand", "minecraft:sandstone", "minecraft:gravel"),
            listOf("minecraft:sandstone_slab")
        )
        m["minecraft:snowy_beach"] = NaturalBiomeDef(
            listOf("minecraft:snow_block", "minecraft:sand", "minecraft:gravel"),
            listOf("minecraft:spruce_slab")
        )
        m["minecraft:stony_shore"] = NaturalBiomeDef(
            listOf("minecraft:stone", "minecraft:cobblestone", "minecraft:gravel"),
            listOf("minecraft:stone_slab", "minecraft:cobblestone_slab")
        )
        m["minecraft:river"] = NaturalBiomeDef(
            listOf("minecraft:dirt_path", "minecraft:gravel", "minecraft:sand"),
            listOf("minecraft:oak_slab")
        )
        m["minecraft:frozen_river"] = NaturalBiomeDef(
            listOf("minecraft:packed_ice", "minecraft:gravel", "minecraft:snow_block"),
            listOf("minecraft:spruce_slab")
        )

        // ===== 蘑菇岛 =====
        m["minecraft:mushroom_fields"] = NaturalBiomeDef(
            listOf("minecraft:mycelium", "minecraft:dirt_path", "minecraft:coarse_dirt"),
            listOf("minecraft:oak_slab")
        )

        // ===== 海洋类（虽然道路不太可能生成在海洋，但提供兜底）=====

        // ===== 洞穴类（地下群系，道路一般不会生成，但提供兜底）=====

        return m
    }

    private fun writeFile(file: Path, entries: Map<String, NaturalBiomeDef>) {
        val dto = NaturalPresetFile(
            biomes = entries.entries.map { (biome, def) ->
                NaturalBiomePreset(biome, def.materials, def.slabMaterials)
            }
        )
        Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { bw ->
            GSON.toJson(dto, bw)
        }
    }
}
