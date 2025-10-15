package net.countered.settlementroads.config.neoforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * NeoForge 端与 Fabric 一致的 JSON 配置实现（保存在 config/roadweaver.json）。
 */
public class NeoForgeJsonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("roadweaver.json");

    private static ConfigData data = new ConfigData();

    // 结构配置（多行：每行一个结构ID或标签）
    public static List<String> getStructuresToLocate() { return data.structuresToLocate; }
    public static void setStructuresToLocate(List<String> value) { data.structuresToLocate = value != null ? value : new ArrayList<>(); }

    public static int getStructureSearchRadius() { return data.structureSearchRadius; }
    public static void setStructureSearchRadius(int value) { data.structureSearchRadius = value; }

    // 预生成配置
    public static int getInitialLocatingCount() { return data.initialLocatingCount; }
    public static void setInitialLocatingCount(int value) { data.initialLocatingCount = value; }

    public static int getMaxConcurrentRoadGeneration() { return data.maxConcurrentRoadGeneration; }
    public static void setMaxConcurrentRoadGeneration(int value) { data.maxConcurrentRoadGeneration = value; }

    public static int getStructureSearchTriggerDistance() { return data.structureSearchTriggerDistance; }
    public static void setStructureSearchTriggerDistance(int value) { 
        data.structureSearchTriggerDistance = Math.max(150, Math.min(1500, value)); 
    }

    // 道路配置
    public static int getAveragingRadius() { return data.averagingRadius; }
    public static void setAveragingRadius(int value) { data.averagingRadius = value; }

    public static boolean getAllowArtificial() { return data.allowArtificial; }
    public static void setAllowArtificial(boolean value) { data.allowArtificial = value; }

    public static boolean getAllowNatural() { return data.allowNatural; }
    public static void setAllowNatural(boolean value) { data.allowNatural = value; }

    public static int getStructureDistanceFromRoad() { return data.structureDistanceFromRoad; }
    public static void setStructureDistanceFromRoad(int value) { data.structureDistanceFromRoad = value; }

    public static int getMaxHeightDifference() { return data.maxHeightDifference; }
    public static void setMaxHeightDifference(int value) { data.maxHeightDifference = value; }

    public static int getMaxTerrainStability() { return data.maxTerrainStability; }
    public static void setMaxTerrainStability(int value) { data.maxTerrainStability = value; }

    // 装饰配置
    public static boolean getPlaceWaypoints() { return data.placeWaypoints; }
    public static void setPlaceWaypoints(boolean value) { data.placeWaypoints = value; }

    public static boolean getPlaceRoadFences() { return data.placeRoadFences; }
    public static void setPlaceRoadFences(boolean value) { data.placeRoadFences = value; }

    public static boolean getPlaceSwings() { return data.placeSwings; }
    public static void setPlaceSwings(boolean value) { data.placeSwings = value; }

    public static boolean getPlaceBenches() { return data.placeBenches; }
    public static void setPlaceBenches(boolean value) { data.placeBenches = value; }

    public static boolean getPlaceGloriettes() { return data.placeGloriettes; }
    public static void setPlaceGloriettes(boolean value) { data.placeGloriettes = value; }

    // 手动模式配置
    public static int getManualMaxHeightDifference() { return data.manualMaxHeightDifference; }
    public static void setManualMaxHeightDifference(int value) { data.manualMaxHeightDifference = value; }

    public static int getManualMaxTerrainStability() { return data.manualMaxTerrainStability; }
    public static void setManualMaxTerrainStability(int value) { data.manualMaxTerrainStability = value; }
    
    public static boolean getManualIgnoreWater() { return data.manualIgnoreWater; }
    public static void setManualIgnoreWater(boolean value) { data.manualIgnoreWater = value; }
    
    // 道路旁结构生成配置
    public static boolean getEnableRoadsideStructures() { return data.enableRoadsideStructures; }
    public static void setEnableRoadsideStructures(boolean value) { data.enableRoadsideStructures = value; }
    
    public static List<String> getRoadsideStructureTags() { return data.roadsideStructureTags; }
    public static void setRoadsideStructureTags(List<String> value) { data.roadsideStructureTags = value != null ? value : new ArrayList<>(); }
    
    public static float getRoadsideStructureSpawnChance() { return data.roadsideStructureSpawnChance; }
    public static void setRoadsideStructureSpawnChance(float value) { data.roadsideStructureSpawnChance = Math.max(0.0f, Math.min(1.0f, value)); }
    
    public static int getMinDistanceBetweenRoadsideStructures() { return data.minDistanceBetweenRoadsideStructures; }
    public static void setMinDistanceBetweenRoadsideStructures(int value) { data.minDistanceBetweenRoadsideStructures = Math.max(50, value); }
    
    public static int getRoadsideStructureDistance() { return data.roadsideStructureDistance; }
    public static void setRoadsideStructureDistance(int value) { data.roadsideStructureDistance = Math.max(8, Math.min(32, value)); }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                data = GSON.fromJson(json, ConfigData.class);
                
                boolean needsSave = false;
                
                // 迁移：旧版单字符串 -> 新版多行列表
                if ((data.structuresToLocate == null || data.structuresToLocate.isEmpty()) && data.structureToLocate != null && !data.structureToLocate.isBlank()) {
                    data.structuresToLocate = tokenizeToList(data.structureToLocate);
                    data.structureToLocate = null;
                    needsSave = true;
                }
                
                // 验证并修正配置范围
                if (data.structureSearchTriggerDistance < 150 || data.structureSearchTriggerDistance > 1500) {
                    data.structureSearchTriggerDistance = 500;
                    needsSave = true;
                }
                
                if (needsSave) {
                    System.out.println("[RoadWeaver] 配置需要迁移/修正，将在服务器启动时保存");
                }
                
                System.out.println("[RoadWeaver] 配置已从文件加载");
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("[RoadWeaver] 配置加载失败，使用默认值");
            }
        } else {
            if (data.structuresToLocate == null || data.structuresToLocate.isEmpty()) {
                data.structuresToLocate = new ArrayList<>(List.of("#minecraft:village"));
            }
            if (data.roadsideStructureTags == null || data.roadsideStructureTags.isEmpty()) {
                data.roadsideStructureTags = new ArrayList<>(List.of("#minecraft:village"));
            }
            System.out.println("[RoadWeaver] 使用默认配置（配置文件不存在）");
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
            System.out.println("[RoadWeaver] 配置已保存到: " + CONFIG_PATH);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[RoadWeaver] 配置保存失败");
        }
    }

    public static void saveIfServer() {
        try {
            save();
        } catch (Exception e) {
            System.err.println("[RoadWeaver] 配置保存失败");
            e.printStackTrace();
        }
    }

    private static class ConfigData {
        // 结构配置
        String structureToLocate = "#minecraft:village"; // 旧字段：向后兼容
        List<String> structuresToLocate = new ArrayList<>(List.of("#minecraft:village"));
        int structureSearchRadius = 100;

        // 预生成配置
        int initialLocatingCount = 7;
        int maxConcurrentRoadGeneration = 3;
        int structureSearchTriggerDistance = 500;

        // 道路配置
        int averagingRadius = 1;
        boolean allowArtificial = true;
        boolean allowNatural = false;
        int structureDistanceFromRoad = 4;
        int maxHeightDifference = 5;
        int maxTerrainStability = 4;

        // 装饰配置
        boolean placeWaypoints = false;
        boolean placeRoadFences = true;
        boolean placeSwings = false;
        boolean placeBenches = false;
        boolean placeGloriettes = false;

        // 手动模式配置
        int manualMaxHeightDifference = 10;
        int manualMaxTerrainStability = 10;
        boolean manualIgnoreWater = false;

        // 道路旁结构生成配置
        boolean enableRoadsideStructures = false;
        List<String> roadsideStructureTags = new ArrayList<>(List.of("#minecraft:village"));
        float roadsideStructureSpawnChance = 0.15f;
        int minDistanceBetweenRoadsideStructures = 250;
        int roadsideStructureDistance = 12;
    }

    private static List<String> tokenizeToList(String raw) {
        List<String> list = new ArrayList<>();
        if (raw == null) return list;
        String normalized = raw.replace('\r', '\n');
        List<String> lines = Arrays.asList(normalized.split("\n"));
        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] tokens = trimmed.split("[;,\\s]+");
            for (String t : tokens) {
                if (t == null) continue;
                String token = t.trim();
                if (!token.isEmpty()) list.add(token);
            }
        }
        return list;
    }
}
