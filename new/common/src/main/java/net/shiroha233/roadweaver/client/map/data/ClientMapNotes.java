package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 客户端地图笔记存储 - 按存档隔离并持久化
 */
public final class ClientMapNotes {
    private ClientMapNotes() {}

    private static String currentWorldId = null;

    private static final Map<BlockPos, String> aliases = new HashMap<>();
    private static final Map<BlockPos, List<String>> notes = new HashMap<>();

    private static boolean dirty = false;

    public static void onWorldJoin() {
        String worldId = MapDataStorage.getWorldId();
        if (worldId == null) return;

        if (worldId.equals(currentWorldId)) return;

        if (currentWorldId != null && dirty) {
            saveToFile();
        }

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

    public static void onWorldLeave() {
        if (dirty) {
            saveToFile();
        }
        currentWorldId = null;
        aliases.clear();
        notes.clear();
        dirty = false;
    }

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

    @Nullable
    public static String getAlias(BlockPos pos) {
        return aliases.get(pos);
    }

    public static void setAlias(BlockPos pos, String alias) {
        if (alias == null || alias.isBlank()) {
            if (aliases.remove(pos) != null) {
                dirty = true;
                saveToFile();
            }
        } else {
            aliases.put(pos, alias);
            dirty = true;
            saveToFile();
        }
    }

    public static boolean hasAlias(BlockPos pos) {
        return aliases.containsKey(pos);
    }

    public static List<String> getNotes(BlockPos pos) {
        return notes.getOrDefault(pos, List.of());
    }

    public static void addNote(BlockPos pos, String note) {
        if (note == null || note.isBlank()) return;
        notes.computeIfAbsent(pos, k -> new ArrayList<>()).add(note);
        dirty = true;
        saveToFile();
    }

    public static void clearNotes(BlockPos pos) {
        if (notes.remove(pos) != null) {
            dirty = true;
            saveToFile();
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
