package net.shiroha233.roadweaver.config.structure

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.architectury.platform.Platform
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.levelgen.structure.Structure
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

/**
 * 结构发现服务
 *
 * 职责：
 * - 从服务端注册表收集所有结构和标签
 * - 缓存结果供客户端 GUI 使用
 * - 支持保存/加载到本地文件（供离线使用）
 */
object StructureDiscoveryService {

    private val LOGGER: Logger = LoggerFactory.getLogger("RoadWeaver/StructureDiscovery")
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
    private const val CACHE_FILE: String = "structure_cache.json"

    // 缓存的结构数据
    @Volatile
    private var cachedResult: DiscoveryResult? = null

    // 线程安全的标记，表示是否已从服务端收集过
    @Volatile
    private var hasDiscovered: Boolean = false

    /**
     * 发现结果
     */
    class DiscoveryResult(
        dimensions: List<ResourceLocation>,
        tags: List<StructureTagEntry>,
        allStructures: List<StructureEntry>
    ) {
        private val dimensions: MutableList<ResourceLocation> = ArrayList(dimensions)
        private val tags: MutableList<StructureTagEntry> = ArrayList(tags)
        private val allStructures: MutableList<StructureEntry> = ArrayList(allStructures)

        // tagId -> structureIds
        private val tagToStructures: MutableMap<String, Set<String>> = ConcurrentHashMap()

        init {
            Collections.sort(this.dimensions)
            Collections.sort(this.tags)
            Collections.sort(this.allStructures)

            for (tag in tags) {
                tagToStructures[tag.tagId().toString()] = tag.getAllStructureIds()
            }
        }

        fun dimensions(): List<ResourceLocation> = Collections.unmodifiableList(dimensions)

        fun tags(): List<StructureTagEntry> = Collections.unmodifiableList(tags)

        fun allStructures(): List<StructureEntry> = Collections.unmodifiableList(allStructures)

        /**
         * 获取指定标签下的所有结构 ID
         */
        fun getStructuresInTag(tagId: String): Set<String> = tagToStructures[tagId] ?: emptySet()

        /**
         * 检查结构是否属于指定标签
         */
        fun isStructureInTag(structureId: String, tagId: String): Boolean {
            val structures = tagToStructures[tagId]
            return structures !== null && structures.contains(structureId)
        }
    }

    /**
     * 兼容入口：从服务端 Level 提取注册表并进行结构发现。
     *
     * 说明：
     * - 该方法只负责“取注册表 + 转发”，保持 StructureDiscoveryService 的单一职责不被破坏。
     * - 供旧调用点（例如 ServerPlanningHooks）使用。
     */
    @JvmStatic
    fun discoverFromLevel(level: ServerLevel) {
        val access = level.registryAccess()
        val structureRegistry: Registry<Structure> = access.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
        val levelStemRegistry: Registry<LevelStem> = access.registryOrThrow(net.minecraft.core.registries.Registries.LEVEL_STEM)
        discoverFromRegistries(structureRegistry, levelStemRegistry)
    }

    /**
     * 从 RegistryAccess 和 LevelStem Registry 收集所有结构和标签信息
     *
     * 由 CreateWorldScreen 的 Mixin 调用
     */
    @JvmStatic
    fun discoverFromRegistries(structureRegistry: Registry<Structure>, levelStemRegistry: Registry<LevelStem>) {
        try {
            // 收集所有维度（来自 LEVEL_STEM）
            val discoveredDimensions: List<ResourceLocation> = levelStemRegistry.keySet().stream()
                .sorted { a, b -> a.toString().compareTo(b.toString()) }
                .collect(Collectors.toList())

            // 预计算每个维度可能出现的生物群系集合，用于推断结构可生成维度
            val possibleBiomesByDimension: Map<ResourceLocation, Set<Holder<Biome>>> = levelStemRegistry.entrySet()
                .stream()
                .collect(
                    Collectors.toMap(
                        { e -> e.key.location() },
                        { e ->
                            try {
                                e.value.generator().biomeSource.possibleBiomes()
                            } catch (_: Exception) {
                                emptySet()
                            }
                        }
                    )
                )

            // 收集所有结构
            val structureMap: MutableMap<ResourceLocation, StructureEntry> = LinkedHashMap()
            for (entry in structureRegistry.entrySet()) {
                val id = entry.key.location()
                val structure = entry.value
                val isVanilla = "minecraft" == id.namespace
                val displayName = formatDisplayName(id)

                // 推断结构可生成维度：若结构 biomes 与维度 possibleBiomes 有交集，则认为该维度可生成
                val dimensions: MutableSet<ResourceLocation> = LinkedHashSet()
                try {
                    val structureBiomes = structure.biomes()
                    for (dimId in discoveredDimensions) {
                        val dimPossibleBiomes = possibleBiomesByDimension[dimId] ?: emptySet()
                        if (dimPossibleBiomes.isEmpty()) continue

                        val matches = structureBiomes.stream().anyMatch { biomeHolder ->
                            dimPossibleBiomes.contains(biomeHolder)
                        }
                        if (matches) {
                            dimensions.add(dimId)
                        }
                    }
                } catch (_: Exception) {
                    // 忽略 biome 访问错误
                }

                structureMap[id] = StructureEntry(id, displayName, isVanilla, dimensions)
            }

            // 收集所有标签及其包含的结构
            val tagEntries: MutableList<StructureTagEntry> = ArrayList()
            val processedTags: MutableSet<ResourceLocation> = HashSet()

            // 遍历所有结构的标签
            for (holder: Holder.Reference<Structure> in structureRegistry.holders().toList()) {
                holder.tags().forEach { tagKey ->
                    val tagId = tagKey.location()
                    if (processedTags.contains(tagId)) return@forEach
                    processedTags.add(tagId)

                    // 收集此标签下的所有结构
                    val tagStructures: MutableList<StructureEntry> = ArrayList()
                    for (h: Holder.Reference<Structure> in structureRegistry.holders().toList()) {
                        if (h.`is`(tagKey)) {
                            val structId = h.key().location()
                            val se = structureMap[structId]
                            if (se !== null) {
                                tagStructures.add(se)
                            }
                        }
                    }

                    if (tagStructures.isNotEmpty()) {
                        val displayName = formatTagDisplayName(tagId)
                        tagEntries.add(StructureTagEntry(tagId, displayName, tagStructures))
                    }
                }
            }

            cachedResult = DiscoveryResult(discoveredDimensions, tagEntries, ArrayList(structureMap.values))
            hasDiscovered = true

            LOGGER.info("Discovered {} structures and {} tags from registries", structureMap.size, tagEntries.size)

            // 保存到缓存文件
            saveCacheToFile()
        } catch (e: Exception) {
            LOGGER.error("Failed to discover structures", e)
        }
    }

    /**
     * 获取缓存的发现结果
     *
     * 如果尚未发现，尝试从文件加载
     */
    @JvmStatic
    fun getResult(): DiscoveryResult? {
        if (cachedResult === null && !hasDiscovered) {
            // 从缓存文件加载
            loadCacheFromFile()
        }
        return cachedResult
    }

    /**
     * 检查是否有可用的发现结果
     */
    @JvmStatic
    fun hasResult(): Boolean = getResult() != null

    /**
     * 清除缓存
     */
    @JvmStatic
    fun clearCache() {
        cachedResult = null
        hasDiscovered = false
    }

    /**
     * 格式化结构显示名称
     */
    private fun formatDisplayName(id: ResourceLocation): String {
        val path = id.path
        // 将下划线替换为空格，首字母大写
        val parts = path.split("_")
        val sb = StringBuilder()
        for (part in parts) {
            if (part.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(part[0].uppercaseChar())
                if (part.length > 1) sb.append(part.substring(1))
            }
        }
        return sb.toString()
    }

    /**
     * 格式化标签显示名称
     */
    private fun formatTagDisplayName(tagId: ResourceLocation): String = "#" + formatDisplayName(tagId)

    // ==================== 缓存文件操作 ====================

    private fun getCacheFilePath(): Path = Platform.getConfigFolder().resolve("roadweaver").resolve(CACHE_FILE)

    private fun saveCacheToFile() {
        val snapshot = cachedResult ?: return

        try {
            val file = getCacheFilePath()
            Files.createDirectories(file.parent)

            // 转换为可序列化的格式
            val data = CacheData()
            data.dimensions = snapshot.dimensions().stream()
                .map { it.toString() }
                .collect(Collectors.toList())
            data.structures = snapshot.allStructures().stream()
                .map { e -> CacheData.StructureData(
                    e.id().toString(), 
                    e.displayName(), 
                    e.isVanilla(),
                    e.dimensions().stream().map { it.toString() }.collect(Collectors.toList())
                ) }
                .collect(Collectors.toList())
            data.tags = snapshot.tags().stream()
                .map { t ->
                    CacheData.TagData(
                        t.tagId().toString(),
                        t.displayName(),
                        t.structures().stream().map { s -> s.id().toString() }.collect(Collectors.toList())
                    )
                }
                .collect(Collectors.toList())

            Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { writer: BufferedWriter ->
                GSON.toJson(data, writer)
            }

            LOGGER.debug("Saved structure cache to {}", file)
        } catch (e: Exception) {
            LOGGER.warn("Failed to save structure cache", e)
        }
    }

    private fun loadCacheFromFile() {
        val file = getCacheFilePath()
        if (!Files.exists(file)) {
            LOGGER.debug("No structure cache file found")
            return
        }

        try {
            val data: CacheData?
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader: BufferedReader ->
                data = GSON.fromJson(reader, CacheData::class.java)
            }

            if (data == null || data.structures == null || data.tags == null) {
                LOGGER.warn("Invalid structure cache file")
                return
            }

            // 读取维度列表（如果不存在则稍后从结构条目中推导）
            val discoveredDimensions: MutableList<ResourceLocation> = ArrayList()
            if (data.dimensions !== null) {
                for (dimStr in data.dimensions!!) {
                    val rl = ResourceLocation.tryParse(dimStr)
                    if (rl !== null) {
                        discoveredDimensions.add(rl)
                    }
                }
            }

            // 重建结构映射
            val structureMap: MutableMap<String, StructureEntry> = LinkedHashMap()
            for (sd in data.structures!!) {
                val id = ResourceLocation.tryParse(sd.id)
                if (id !== null) {
                    val dimensions: MutableSet<ResourceLocation> = LinkedHashSet()

                    // 新格式：dimensions = ["minecraft:overworld", ...]
                    if (sd.dimensions !== null) {
                        for (dimStr in sd.dimensions) {
                            val rl = ResourceLocation.tryParse(dimStr)
                            if (rl !== null) {
                                dimensions.add(rl)
                            }
                        }
                    }

                    structureMap[sd.id] = StructureEntry(id, sd.displayName, sd.isVanilla, dimensions)
                }
            }

            // 若缓存未提供维度列表，则从所有结构的 dimensions 推导
            if (discoveredDimensions.isEmpty()) {
                val derived: MutableSet<ResourceLocation> = LinkedHashSet()
                for (se in structureMap.values) {
                    derived.addAll(se.dimensions())
                }
                discoveredDimensions.addAll(derived)
                Collections.sort(discoveredDimensions)
            }

            // 重建标签列表
            val tagEntries: MutableList<StructureTagEntry> = ArrayList()
            for (td in data.tags!!) {
                val tagId = ResourceLocation.tryParse(td.tagId) ?: continue

                val tagStructures: MutableList<StructureEntry> = ArrayList()
                for (structId in td.structureIds) {
                    val se = structureMap[structId]
                    if (se !== null) {
                        tagStructures.add(se)
                    }
                }

                if (tagStructures.isNotEmpty()) {
                    tagEntries.add(StructureTagEntry(tagId, td.displayName, tagStructures))
                }
            }

            cachedResult = DiscoveryResult(discoveredDimensions, tagEntries, ArrayList(structureMap.values))
            LOGGER.info("Loaded structure cache: {} structures, {} tags", structureMap.size, tagEntries.size)
        } catch (e: Exception) {
            LOGGER.warn("Failed to load structure cache", e)
        }
    }

    /**
     * 缓存数据格式（用于 JSON 序列化）
     */
    private class CacheData {
        var dimensions: List<String>? = null
        var structures: List<StructureData>? = null
        var tags: List<TagData>? = null

        class StructureData(
            @JvmField val id: String,
            @JvmField val displayName: String,
            @JvmField val isVanilla: Boolean,
            @JvmField val dimensions: List<String>? = null
        )

        class TagData(
            @JvmField val tagId: String,
            @JvmField val displayName: String,
            @JvmField val structureIds: List<String>
        )
    }
}
