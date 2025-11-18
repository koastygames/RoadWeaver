package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.core.BlockPos;

import java.util.*;

public final class ClientMapNotes {
    private ClientMapNotes() {}

    private static final Map<BlockPos, String> ALIAS = new HashMap<>();
    private static final Map<BlockPos, List<String>> NOTES = new HashMap<>();

    public static String getAlias(BlockPos pos) {
        return ALIAS.get(pos);
    }

    public static void setAlias(BlockPos pos, String alias) {
        if (alias == null || alias.isBlank()) ALIAS.remove(pos);
        else ALIAS.put(pos, alias);
    }

    public static List<String> getNotes(BlockPos pos) {
        return NOTES.getOrDefault(pos, List.of());
    }

    public static void addNote(BlockPos pos, String note) {
        if (note == null || note.isBlank()) return;
        NOTES.computeIfAbsent(pos, k -> new ArrayList<>()).add(note);
    }
}
