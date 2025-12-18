package net.shiroha233.roadweaver.client.map.data

import net.minecraft.core.BlockPos
import java.util.ArrayList

/**
 * 客户端地图笔记存储 - 按存档隔离并持久化
 *
 * 设计原理（参考 JourneyMap/Xaero's Map）：
 * 1. 数据存储在游戏目录下，而非存档内（玩家私人数据）
 * 2. 按存档名/服务器地址分文件夹隔离
 * 3. 进入世界时自动加载，退出/修改时自动保存
 *
 * 文件路径：.minecraft/config/roadweaver/mapdata/<存档名>/notes.json
 */
object ClientMapNotes {
    /** 当前加载的世界标识 */
    private var currentWorldId: String? = null

    /** 当前世界的数据（内存缓存） */
    private val aliases: MutableMap<BlockPos, String> = HashMap()
    private val notes: MutableMap<BlockPos, MutableList<String>> = HashMap()

    /** 是否有未保存的修改 */
    private var dirty: Boolean = false

    // ========== 世界生命周期 ==========

    /** 进入世界时调用 - 加载数据 */
    @JvmStatic
    fun onWorldJoin() {
        val worldId = MapDataStorage.getWorldId() ?: return

        // 如果是同一个世界，不重新加载
        if (worldId == currentWorldId) return

        // 保存旧世界数据
        if (currentWorldId !== null && dirty) {
            saveToFile()
        }

        // 清空并加载新世界数据
        currentWorldId = worldId
        aliases.clear()
        notes.clear()
        dirty = false

        val data = MapDataStorage.loadNotes()
        for ((k, v) in data.aliases) {
            val pos = MapDataStorage.keyToPos(k) ?: continue
            aliases[pos] = v
        }
        for ((k, v) in data.notes) {
            val pos = MapDataStorage.keyToPos(k) ?: continue
            notes[pos] = ArrayList(v)
        }
    }

    /** 退出世界时调用 - 保存数据 */
    @JvmStatic
    fun onWorldLeave() {
        if (dirty) {
            saveToFile()
        }
        currentWorldId = null
        aliases.clear()
        notes.clear()
        dirty = false
    }

    /** 保存到文件 */
    @JvmStatic
    fun saveToFile() {
        val data = MapDataStorage.NotesData()
        for ((pos, alias) in aliases) {
            data.aliases[MapDataStorage.posToKey(pos)] = alias
        }
        for ((pos, list) in notes) {
            data.notes[MapDataStorage.posToKey(pos)] = list
        }
        MapDataStorage.saveNotes(data)
        dirty = false
    }

    // ========== 别名操作 ==========

    @JvmStatic
    fun getAlias(pos: BlockPos): String? {
        return aliases[pos]
    }

    @JvmStatic
    fun setAlias(pos: BlockPos, alias: String?) {
        if (alias.isNullOrBlank()) {
            if (aliases.remove(pos) != null) {
                dirty = true
                saveToFile() // 立即保存
            }
        } else {
            aliases[pos] = alias
            dirty = true
            saveToFile() // 立即保存
        }
    }

    @JvmStatic
    fun hasAlias(pos: BlockPos): Boolean {
        return aliases.containsKey(pos)
    }

    // ========== 笔记操作 ==========

    @JvmStatic
    fun getNotes(pos: BlockPos): List<String> {
        return notes[pos] ?: emptyList()
    }

    @JvmStatic
    fun addNote(pos: BlockPos, note: String?) {
        if (note.isNullOrBlank()) return
        notes.computeIfAbsent(pos) { ArrayList() }.add(note)
        dirty = true
        saveToFile() // 立即保存
    }

    @JvmStatic
    fun clearNotes(pos: BlockPos) {
        if (notes.remove(pos) != null) {
            dirty = true
            saveToFile() // 立即保存
        }
    }

    @JvmStatic
    fun setNotes(pos: BlockPos, noteList: List<String>?) {
        if (noteList.isNullOrEmpty()) {
            if (notes.remove(pos) != null) {
                dirty = true
                saveToFile()
            }
        } else {
            notes[pos] = ArrayList(noteList)
            dirty = true
            saveToFile()
        }
    }

    @JvmStatic
    fun hasNotes(pos: BlockPos): Boolean {
        val n = notes[pos]
        return n !== null && n.isNotEmpty()
    }
}
