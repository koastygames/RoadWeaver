package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
public final class ClientMapNotes {
    private ClientMapNotes() {}

    /** 当前加载的世界标识 */
    private static String currentWorldId = null;

    /** 当前世界的数据（内存缓存） */
    private static final Map<BlockPos, String> aliases = new HashMap<>();
    private static final Map<BlockPos, List<String>> notes = new HashMap<>();

    /** 是否有未保存的修改 */
    private static boolean dirty = false;

    // ========== 世界生命周期 ==========

    /** 进入世界时调用 - 加载数据 */
    public static void onWorldJoin() {
        String worldId = MapDataStorage.getWorldId();
        if (worldId == null) return;

        // 如果是同一个世界，不重新加载
        if (worldId.equals(currentWorldId)) return;

        // 保存旧世界数据
        if (currentWorldId != null && dirty) {
            saveToFile();
        }

        // 清空并加载新世界数据
        currentWorldId = worldId;
        aliases.clear();
        notes.clear();
        dirty = false;

        MapDataStorage.NotesData data = MapDataStorage.loadNotes();
        for (Map.Entry<String, String> e : data.aliases.entrySet()) {
            BlockPos pos = MapDataStorage.keyToPos(e.getKey());
            if (pos != null) aliases.put(pos, e.getValue());
        }
        for (Map.Entry<String, List<String>> e : data.notes.entrySet()) {
            BlockPos pos = MapDataStorage.keyToPos(e.getKey());
            if (pos != null) notes.put(pos, new ArrayList<>(e.getValue()));
        }
    }

    /** 退出世界时调用 - 保存数据 */
    public static void onWorldLeave() {
        if (dirty) {
            saveToFile();
        }
        currentWorldId = null;
        aliases.clear();
        notes.clear();
        dirty = false;
    }

    /** 保存到文件 */
    public static void saveToFile() {
        MapDataStorage.NotesData data = new MapDataStorage.NotesData();
        for (Map.Entry<BlockPos, String> e : aliases.entrySet()) {
            data.aliases.put(MapDataStorage.posToKey(e.getKey()), e.getValue());
        }
        for (Map.Entry<BlockPos, List<String>> e : notes.entrySet()) {
            data.notes.put(MapDataStorage.posToKey(e.getKey()), e.getValue());
        }
        MapDataStorage.saveNotes(data);
        dirty = false;
    }

    // ========== 别名操作 ==========

    @Nullable
    public static String getAlias(BlockPos pos) {
        return aliases.get(pos);
    }

    public static void setAlias(BlockPos pos, String alias) {
        if (alias == null || alias.isBlank()) {
            if (aliases.remove(pos) != null) {
                dirty = true;
                saveToFile(); // 立即保存
            }
        } else {
            aliases.put(pos, alias);
            dirty = true;
            saveToFile(); // 立即保存
        }
    }

    public static boolean hasAlias(BlockPos pos) {
        return aliases.containsKey(pos);
    }

    // ========== 笔记操作 ==========

    public static List<String> getNotes(BlockPos pos) {
        return notes.getOrDefault(pos, List.of());
    }

    public static void addNote(BlockPos pos, String note) {
        if (note == null || note.isBlank()) return;
        notes.computeIfAbsent(pos, k -> new ArrayList<>()).add(note);
        dirty = true;
        saveToFile(); // 立即保存
    }

    public static void clearNotes(BlockPos pos) {
        if (notes.remove(pos) != null) {
            dirty = true;
            saveToFile(); // 立即保存
        }
    }

    public static void setNotes(BlockPos pos, List<String> noteList) {
        if (noteList == null || noteList.isEmpty()) {
            if (notes.remove(pos) != null) {
                dirty = true;
                saveToFile();
            }
        } else {
            notes.put(pos, new ArrayList<>(noteList));
            dirty = true;
            saveToFile();
        }
    }

    public static boolean hasNotes(BlockPos pos) {
        List<String> n = notes.get(pos);
        return n != null && !n.isEmpty();
    }
}
