package net.shiroha233.roadweaver.config.structure

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.architectury.platform.Platform
import dev.architectury.utils.Env
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
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
import java.util.Locale
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
        tags: List<StructureTagEntry>,
        allStructures: List<StructureEntry>
    ) {
        private val tags: MutableList<StructureTagEntry> = ArrayList(tags)
        private val allStructures: MutableList<StructureEntry> = ArrayList(allStructures)

        // tagId -> structureIds
        private val tagToStructures: MutableMap<String, Set<String>> = ConcurrentHashMap()

        init {
            Collections.sort(this.tags)
            Collections.sort(this.allStructures)

            for (tag in tags) {
                tagToStructures[tag.tagId().toString()] = tag.getAllStructureIds()
            }
        }

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
            return structures != null && structures.contains(structureId)
        }
    }

    /**
     * 从服务端世界收集所有结构和标签信息
     *
     * 应该在进入世界后调用（服务端）
     */
    @JvmStatic
    fun discoverFromLevel(level: ServerLevel?) {
        if (level == null) {
            LOGGER.warn("Cannot discover structures: level is null")
            return
        }
        discoverFromRegistryAccess(level.registryAccess())
    }

    /**
     * 从 RegistryAccess 收集所有结构和标签信息
     *
     * 可以在客户端创建世界界面或服务端调用
     */
    @JvmStatic
    fun discoverFromRegistryAccess(registryAccess: RegistryAccess?) {
        if (registryAccess == null) {
            LOGGER.warn("Cannot discover structures: registryAccess is null")
            return
        }

        try {
            val structureRegistry: Registry<Structure> = registryAccess.registryOrThrow(Registries.STRUCTURE)

            // 收集所有结构
            val structureMap: MutableMap<ResourceLocation, StructureEntry> = LinkedHashMap()
            for (entry in structureRegistry.entrySet()) {
                val id = entry.key.location()
                val isVanilla = "minecraft" == id.namespace
                val displayName = formatDisplayName(id)
                structureMap[id] = StructureEntry(id, displayName, isVanilla)
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
                            if (se != null) {
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

            cachedResult = DiscoveryResult(tagEntries, ArrayList(structureMap.values))
            hasDiscovered = true

            LOGGER.info("Discovered {} structures and {} tags", structureMap.size, tagEntries.size)

            // 保存到缓存文件
            saveCacheToFile()
        } catch (e: Exception) {
            LOGGER.error("Failed to discover structures", e)
        }
    }

    /**
     * 获取缓存的发现结果
     *
     * 如果尚未发现，尝试从当前上下文或文件加载
     */
    @JvmStatic
    fun getResult(): DiscoveryResult? {
        if (cachedResult == null && !hasDiscovered) {
            // 尝试从当前上下文获取
            tryDiscoverFromCurrentContext()
        }
        if (cachedResult == null && !hasDiscovered) {
            // 从缓存文件加载
            loadCacheFromFile()
        }
        return cachedResult
    }

    /**
     * 尝试从当前上下文获取结构注册表
     *
     * 支持：
     * - 客户端已连接服务器（ClientLevel）
     * - 客户端创建世界界面（WorldCreationContext）
     */
    @JvmStatic
    fun tryDiscoverFromCurrentContext() {
        // 只在客户端执行
        if (dev.architectury.platform.Platform.getEnvironment() != Env.CLIENT) {
            return
        }

        try {
            // 尝试从客户端获取（使用反射避免直接引用客户端类）
            val access = ClientRegistryAccessHelper.tryGetClientRegistryAccess()
            if (access != null) {
                discoverFromRegistryAccess(access)
            }
        } catch (_: Exception) {
            // 静默失败，避免在某些加载阶段刷屏日志
        }
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
            data.structures = snapshot.allStructures().stream()
                .map { e -> CacheData.StructureData(e.id().toString(), e.displayName(), e.isVanilla()) }
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

            // 重建结构映射
            val structureMap: MutableMap<String, StructureEntry> = LinkedHashMap()
            for (sd in data.structures!!) {
                val id = ResourceLocation.tryParse(sd.id)
                if (id != null) {
                    structureMap[sd.id] = StructureEntry(id, sd.displayName, sd.isVanilla)
                }
            }

            // 重建标签列表
            val tagEntries: MutableList<StructureTagEntry> = ArrayList()
            for (td in data.tags!!) {
                val tagId = ResourceLocation.tryParse(td.tagId) ?: continue

                val tagStructures: MutableList<StructureEntry> = ArrayList()
                for (structId in td.structureIds) {
                    val se = structureMap[structId]
                    if (se != null) {
                        tagStructures.add(se)
                    }
                }

                if (tagStructures.isNotEmpty()) {
                    tagEntries.add(StructureTagEntry(tagId, td.displayName, tagStructures))
                }
            }

            cachedResult = DiscoveryResult(tagEntries, ArrayList(structureMap.values))
            LOGGER.info("Loaded structure cache: {} structures, {} tags", structureMap.size, tagEntries.size)
        } catch (e: Exception) {
            LOGGER.warn("Failed to load structure cache", e)
        }
    }

    /**
     * 客户端注册表访问帮助类（内部类，避免外层类加载客户端依赖）
     */
    private object ClientRegistryAccessHelper {
        fun tryGetClientRegistryAccess(): RegistryAccess? {
            try {
                // 使用反射加载 Minecraft 类，避免在服务端加载时失败
                val minecraftClass = Class.forName("net.minecraft.client.Minecraft")
                val mc = minecraftClass.getMethod("getInstance").invoke(null) ?: return null

                // 获取 level 字段
                val levelField = minecraftClass.getDeclaredField("level")
                levelField.isAccessible = true
                val level = levelField.get(mc)

                if (level != null) {
                    // 调用 level.registryAccess()
                    val registryAccessMethod = level.javaClass.getMethod("registryAccess")
                    return registryAccessMethod.invoke(level) as RegistryAccess
                }

                // 尝试从 CreateWorldScreen 获取
                val screenField = minecraftClass.getDeclaredField("screen")
                screenField.isAccessible = true
                val screen = screenField.get(mc)

                if (screen != null) {
                    val createWorldScreenClass = Class.forName("net.minecraft.client.gui.screens.worldselection.CreateWorldScreen")
                    if (createWorldScreenClass.isInstance(screen)) {
                        val uiStateField = createWorldScreenClass.getDeclaredField("uiState")
                        uiStateField.isAccessible = true
                        val uiState = uiStateField.get(screen)

                        val getSettingsMethod = uiState.javaClass.getMethod("getSettings")
                        val context = getSettingsMethod.invoke(uiState)

                        if (context != null) {
                            val worldgenLoadContextMethod = context.javaClass.getMethod("worldgenLoadContext")
                            return worldgenLoadContextMethod.invoke(context) as RegistryAccess
                        }
                    }
                }
            } catch (e: Exception) {
                LOGGER.warn("Failed to get client registry access: {}", e.message, e)
            }
            return null
        }
    }

    /**
     * 缓存数据格式（用于 JSON 序列化）
     */
    private class CacheData {
        var structures: List<StructureData>? = null
        var tags: List<TagData>? = null

        class StructureData(
            @JvmField val id: String,
            @JvmField val displayName: String,
            @JvmField val isVanilla: Boolean
        )

        class TagData(
            @JvmField val tagId: String,
            @JvmField val displayName: String,
            @JvmField val structureIds: List<String>
        )
    }
}
