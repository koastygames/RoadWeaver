package net.shiroha233.roadweaver.config.structure

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.architectury.platform.Platform
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.LinkedHashSet
import java.util.Locale

/**
 * 结构选择配置
 *
 * 职责：
 * - 存储用户选择的启用/禁用结构
 * - 支持按标签批量选择
 * - 提供快速查询接口
 * - 持久化到单独的配置文件
 */
class StructureSelectionConfig private constructor() {

    // 启用的结构 ID 集合
    private val enabledStructures: MutableSet<String> = LinkedHashSet()

    // 启用的标签 ID 集合（用于 GUI 显示状态）
    private val enabledTags: MutableSet<String> = LinkedHashSet()

    // 是否使用标签模式（true = 只存储标签，false = 存储具体结构）
    private var useTagMode: Boolean = true

    /**
     * 检查结构是否启用
     */
    fun isStructureEnabled(structureId: String?): Boolean {
        if (structureId == null) return false
        val normalized = structureId.lowercase(Locale.ROOT)

        // 首先检查直接启用的结构
        if (enabledStructures.contains(normalized)) {
            return true
        }

        // 如果使用标签模式，检查结构是否属于任何启用的标签
        if (useTagMode && enabledTags.isNotEmpty()) {
            val result = StructureDiscoveryService.getResult()
            if (result != null) {
                for (tagId in enabledTags) {
                    if (result.isStructureInTag(normalized, tagId)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * 启用一个结构
     */
    fun enableStructure(structureId: String?) {
        if (structureId != null) {
            enabledStructures.add(structureId.lowercase(Locale.ROOT))
        }
    }

    /**
     * 禁用一个结构
     */
    fun disableStructure(structureId: String?) {
        if (structureId != null) {
            enabledStructures.remove(structureId.lowercase(Locale.ROOT))
        }
    }

    /**
     * 切换结构的启用状态
     */
    fun toggleStructure(structureId: String?) {
        if (structureId == null) return
        val normalized = structureId.lowercase(Locale.ROOT)
        if (enabledStructures.contains(normalized)) {
            enabledStructures.remove(normalized)
        } else {
            enabledStructures.add(normalized)
        }
    }

    /**
     * 检查标签是否启用
     */
    fun isTagEnabled(tagId: String?): Boolean {
        if (tagId == null) return false
        return enabledTags.contains(tagId.lowercase(Locale.ROOT))
    }

    /**
     * 启用一个标签（及其下的所有结构）
     */
    fun enableTag(tagId: String?) {
        if (tagId == null) return
        val normalized = tagId.lowercase(Locale.ROOT)
        enabledTags.add(normalized)

        // 如果不使用标签模式，把标签下的结构加入启用列表
        if (!useTagMode) {
            val result = StructureDiscoveryService.getResult()
            if (result != null) {
                val structures = result.getStructuresInTag(normalized)
                enabledStructures.addAll(structures)
            }
        }
    }

    /**
     * 禁用一个标签（及其下的所有结构）
     */
    fun disableTag(tagId: String?) {
        if (tagId == null) return
        val normalized = tagId.lowercase(Locale.ROOT)
        enabledTags.remove(normalized)

        // 如果不使用标签模式，把标签下的结构从启用列表移除
        if (!useTagMode) {
            val result = StructureDiscoveryService.getResult()
            if (result != null) {
                val structures = result.getStructuresInTag(normalized)
                enabledStructures.removeAll(structures)
            }
        }
    }

    /**
     * 切换标签的启用状态
     */
    fun toggleTag(tagId: String?) {
        if (tagId == null) return
        val normalized = tagId.lowercase(Locale.ROOT)
        if (enabledTags.contains(normalized)) {
            disableTag(tagId)
        } else {
            enableTag(tagId)
        }
    }

    /**
     * 启用所有村庄类结构（默认配置）
     */
    fun enableDefaultVillages() {
        enableTag("minecraft:village")
    }

    /**
     * 清除所有选择
     */
    fun clearAll() {
        enabledStructures.clear()
        enabledTags.clear()
    }

    /**
     * 启用所有结构
     */
    fun enableAll() {
        val result = StructureDiscoveryService.getResult()
        if (result != null) {
            for (entry in result.allStructures()) {
                enabledStructures.add(entry.id().toString().lowercase(Locale.ROOT))
            }
            for (tag in result.tags()) {
                enabledTags.add(tag.tagId().toString().lowercase(Locale.ROOT))
            }
        }
    }

    /**
     * 获取所有启用的结构 ID（用于实际筛选）
     */
    fun getEnabledStructures(): Set<String> {
        val result: MutableSet<String> = LinkedHashSet(enabledStructures)

        // 如果使用标签模式，展开所有启用的标签
        if (useTagMode && enabledTags.isNotEmpty()) {
            val discovery = StructureDiscoveryService.getResult()
            if (discovery != null) {
                for (tagId in enabledTags) {
                    result.addAll(discovery.getStructuresInTag(tagId))
                }
            }
        }

        return Collections.unmodifiableSet(result)
    }

    /**
     * 获取启用的标签 ID 集合
     */
    fun getEnabledTags(): Set<String> = Collections.unmodifiableSet(enabledTags)

    /**
     * 转换为白名单格式（兼容旧系统）
     *
     * 返回标签形式（#minecraft:village）或具体结构 ID
     */
    fun toWhitelist(): List<String> {
        val result: MutableList<String> = ArrayList()

        // 添加标签（带 # 前缀）
        for (tagId in enabledTags) {
            result.add("#" + tagId)
        }

        // 添加单独启用的结构（不在任何标签中的）
        val taggedStructures: MutableSet<String> = HashSet()
        val discovery = StructureDiscoveryService.getResult()
        if (discovery != null) {
            for (tagId in enabledTags) {
                taggedStructures.addAll(discovery.getStructuresInTag(tagId))
            }
        }

        for (structId in enabledStructures) {
            if (!taggedStructures.contains(structId)) {
                result.add(structId)
            }
        }

        return result
    }

    /**
     * 检查是否有任何选择
     */
    fun hasAnySelection(): Boolean = enabledStructures.isNotEmpty() || enabledTags.isNotEmpty()

    private fun getConfigFilePath(): Path = Platform.getConfigFolder().resolve("roadweaver").resolve(CONFIG_FILE)

    /**
     * 保存配置到文件
     */
    fun save() {
        try {
            val file = getConfigFilePath()
            Files.createDirectories(file.parent)

            val data = ConfigData()
            data.enabledStructures = ArrayList(enabledStructures)
            data.enabledTags = ArrayList(enabledTags)
            data.useTagMode = useTagMode

            Files.newBufferedWriter(file, StandardCharsets.UTF_8).use { writer: BufferedWriter ->
                GSON.toJson(data, writer)
            }

            LOGGER.debug("Saved structure selection config")
        } catch (e: Exception) {
            LOGGER.warn("Failed to save structure selection config", e)
        }
    }

    /**
     * 从文件加载配置
     */
    private fun load() {
        val file = getConfigFilePath()
        if (!Files.exists(file)) {
            // 默认启用村庄
            enableDefaultVillages()
            save()
            return
        }

        try {
            Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader: BufferedReader ->
                val data = GSON.fromJson(reader, ConfigData::class.java)
                if (data != null) {
                    if (data.enabledStructures != null) {
                        enabledStructures.addAll(data.enabledStructures!!)
                    }
                    if (data.enabledTags != null) {
                        enabledTags.addAll(data.enabledTags!!)
                    }
                    useTagMode = data.useTagMode
                }
            }
            LOGGER.debug(
                "Loaded structure selection: {} structures, {} tags",
                enabledStructures.size,
                enabledTags.size
            )
        } catch (e: Exception) {
            LOGGER.warn("Failed to load structure selection config, using defaults", e)
            enableDefaultVillages()
        }
    }

    private class ConfigData {
        var enabledStructures: List<String>? = ArrayList()
        var enabledTags: List<String>? = ArrayList()
        var useTagMode: Boolean = true
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger("RoadWeaver/StructureSelection")
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()
        private const val CONFIG_FILE: String = "structure_selection.json"

        @JvmStatic
        private var INSTANCE: StructureSelectionConfig? = null

        /**
         * 获取单例实例
         */
        @JvmStatic
        @Synchronized
        fun get(): StructureSelectionConfig {
            if (INSTANCE == null) {
                val inst = StructureSelectionConfig()
                inst.load()
                INSTANCE = inst
            }
            return INSTANCE!!
        }

        /**
         * 重新加载配置
         */
        @JvmStatic
        @Synchronized
        fun reload() {
            val inst = StructureSelectionConfig()
            inst.load()
            INSTANCE = inst
        }
    }
}
