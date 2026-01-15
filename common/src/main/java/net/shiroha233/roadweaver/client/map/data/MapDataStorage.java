package net.shiroha233.roadweaver.client.map.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public final class MapDataStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_DIR = "config/roadweaver/mapdata";
    private static final String NOTES_FILE = "notes.json";

    private MapDataStorage() {}

    /** 笔记数据结构（用于 JSON 序列化） */
    public static class NotesData {
        public Map<String, String> aliases = new HashMap<>();      // "x,y,z" -> alias
        public Map<String, List<String>> notes = new HashMap<>();  // "x,y,z" -> [note1, note2, ...]
    }

    // ========== 路径工具 ==========

    /** 获取数据根目录 */
    private static Path getDataRoot() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(DATA_DIR);
    }

    /** 获取当前世界的数据目录 */
    public static Path getWorldDataDir() {
        String worldId = getWorldId();
        if (worldId == null) return null;
        
        // 清理非法文件名字符
        String safeName = worldId.replaceAll("[<>:\"/\\\\|?*]", "_");
        return getDataRoot().resolve(safeName);
    }

    /** 获取当前世界标识（存档名或服务器地址） */
    public static String getWorldId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return null;

        if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
            // 单人模式：使用存档名
            return mc.getSingleplayerServer().getWorldData().getLevelName();
        } else if (mc.getCurrentServer() != null) {
            // 多人模式：使用服务器地址
            return mc.getCurrentServer().ip;
        }
        return null;
    }

    // ========== 笔记数据读写 ==========

    /** 加载笔记数据 */
    public static NotesData loadNotes() {
        Path dir = getWorldDataDir();
        if (dir == null) return new NotesData();

        Path file = dir.resolve(NOTES_FILE);
        if (!Files.exists(file)) return new NotesData();

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            NotesData data = GSON.fromJson(json, NotesData.class);
            LOGGER.debug("[RoadWeaver] 已加载地图笔记: {}", file);
            return data != null ? data : new NotesData();
        } catch (IOException e) {
            LOGGER.error("[RoadWeaver] 加载地图笔记失败: {}", file, e);
            return new NotesData();
        }
    }

    /** 保存笔记数据 */
    public static void saveNotes(NotesData data) {
        Path dir = getWorldDataDir();
        if (dir == null) return;

        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(NOTES_FILE);
            String json = GSON.toJson(data);
            Files.writeString(file, json, StandardCharsets.UTF_8);
            LOGGER.debug("[RoadWeaver] 已保存地图笔记: {}", file);
        } catch (IOException e) {
            LOGGER.error("[RoadWeaver] 保存地图笔记失败", e);
        }
    }

    // ========== BlockPos 序列化 ==========

    public static String posToKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static BlockPos keyToPos(String key) {
        String[] parts = key.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
