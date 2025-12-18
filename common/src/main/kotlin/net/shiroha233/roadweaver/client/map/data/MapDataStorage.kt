package net.shiroha233.roadweaver.client.map.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * 地图数据本地存储管理器
 *
 * 设计原理（参考 JourneyMap/Xaero's Map）：
 * 1. 数据存储在游戏目录下，而非存档内（玩家私人数据）
 * 2. 按存档名/服务器地址分文件夹隔离
 * 3. 使用 JSON 格式便于调试和手动编辑
 *
 * 文件结构：
 * .minecraft/config/roadweaver/mapdata/
 * ├── <存档名>/notes.json
 * └── <服务器地址>/notes.json
 */
object MapDataStorage {
    private val LOGGER = LoggerFactory.getLogger("RoadWeaver")
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private const val DATA_DIR: String = "config/roadweaver/mapdata"
    private const val NOTES_FILE: String = "notes.json"

    /** 笔记数据结构（用于 JSON 序列化） */
    class NotesData {
        var aliases: MutableMap<String, String> = HashMap()      // "x,y,z" -> alias
        var notes: MutableMap<String, List<String>> = HashMap()  // "x,y,z" -> [note1, note2, ...]
    }

    // ========== 路径工具 ==========

    /** 获取数据根目录 */
    private fun getDataRoot(): Path {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(DATA_DIR)
    }

    /** 获取当前世界的数据目录 */
    @JvmStatic
    fun getWorldDataDir(): Path? {
        val worldId = getWorldId() ?: return null

        // 清理非法文件名字符
        val safeName = worldId.replace(Regex("[<>:\"/\\\\|?*]"), "_")
        return getDataRoot().resolve(safeName)
    }

    /** 获取当前世界标识（存档名或服务器地址） */
    @JvmStatic
    fun getWorldId(): String? {
        val mc = Minecraft.getInstance() ?: return null
        if (mc.level == null) return null

        return if (mc.isLocalServer && mc.singleplayerServer != null) {
            // 单人模式：使用存档名
            mc.singleplayerServer!!.worldData.levelName
        } else if (mc.currentServer != null) {
            // 多人模式：使用服务器地址
            mc.currentServer!!.ip
        } else {
            null
        }
    }

    // ========== 笔记数据读写 ==========

    /** 加载笔记数据 */
    @JvmStatic
    fun loadNotes(): NotesData {
        val dir = getWorldDataDir() ?: return NotesData()

        val file = dir.resolve(NOTES_FILE)
        if (!Files.exists(file)) return NotesData()

        return try {
            val json = Files.readString(file, StandardCharsets.UTF_8)
            val data = GSON.fromJson(json, NotesData::class.java)
            LOGGER.debug("[RoadWeaver] 已加载地图笔记: {}", file)
            data ?: NotesData()
        } catch (e: IOException) {
            LOGGER.error("[RoadWeaver] 加载地图笔记失败: {}", file, e)
            NotesData()
        }
    }

    /** 保存笔记数据 */
    @JvmStatic
    fun saveNotes(data: NotesData) {
        val dir = getWorldDataDir() ?: return

        try {
            Files.createDirectories(dir)
            val file = dir.resolve(NOTES_FILE)
            val json = GSON.toJson(data)
            Files.writeString(file, json, StandardCharsets.UTF_8)
            LOGGER.debug("[RoadWeaver] 已保存地图笔记: {}", file)
        } catch (e: IOException) {
            LOGGER.error("[RoadWeaver] 保存地图笔记失败", e)
        }
    }

    // ========== BlockPos 序列化 ==========

    @JvmStatic
    fun posToKey(pos: BlockPos): String {
        return pos.x.toString() + "," + pos.y + "," + pos.z
    }

    @JvmStatic
    fun keyToPos(key: String): BlockPos? {
        val parts = key.split(",")
        if (parts.size != 3) return null

        return try {
            BlockPos(
                parts[0].toInt(),
                parts[1].toInt(),
                parts[2].toInt()
            )
        } catch (_: NumberFormatException) {
            null
        }
    }
}
