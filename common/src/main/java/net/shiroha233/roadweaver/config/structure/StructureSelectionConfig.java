package net.shiroha233.roadweaver.config.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 结构选择配置
 * 
 * 职责：
 * - 存储用户选择的启用/禁用结构
 * - 支持按标签批量选择
 * - 提供快速查询接口
 * - 持久化到单独的配置文件
 */
public final class StructureSelectionConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/StructureSelection");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "structure_selection.json";
    
    // 单例实例
    private static StructureSelectionConfig INSTANCE = null;
    
    // 启用的结构 ID 集合
    private final Set<String> enabledStructures = new LinkedHashSet<>();
    
    // 启用的标签 ID 集合（用于 GUI 显示状态）
    private final Set<String> enabledTags = new LinkedHashSet<>();
    
    // 是否使用标签模式（true = 只存储标签，false = 存储具体结构）
    private boolean useTagMode = true;
    
    private StructureSelectionConfig() {}
    
    /**
     * 获取单例实例
     */
    public static synchronized StructureSelectionConfig get() {
        if (INSTANCE == null) {
            INSTANCE = new StructureSelectionConfig();
            INSTANCE.load();
        }
        return INSTANCE;
    }
    
    /**
     * 重新加载配置
     */
    public static synchronized void reload() {
        INSTANCE = new StructureSelectionConfig();
        INSTANCE.load();
    }
    
    // ==================== 结构启用/禁用操作 ====================
    
    /**
     * 检查结构是否启用
     */
    public boolean isStructureEnabled(String structureId) {
        if (structureId == null) return false;
        String normalized = structureId.toLowerCase(Locale.ROOT);
        
        // 首先检查直接启用的结构
        if (enabledStructures.contains(normalized)) {
            return true;
        }
        
        // 如果使用标签模式，检查结构是否属于任何启用的标签
        if (useTagMode && !enabledTags.isEmpty()) {
            StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
            if (result != null) {
                for (String tagId : enabledTags) {
                    if (result.isStructureInTag(normalized, tagId)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * 启用一个结构
     */
    public void enableStructure(String structureId) {
        if (structureId != null) {
            enabledStructures.add(structureId.toLowerCase(Locale.ROOT));
        }
    }
    
    /**
     * 禁用一个结构
     */
    public void disableStructure(String structureId) {
        if (structureId != null) {
            enabledStructures.remove(structureId.toLowerCase(Locale.ROOT));
        }
    }
    
    /**
     * 切换结构的启用状态
     */
    public void toggleStructure(String structureId) {
        if (structureId == null) return;
        String normalized = structureId.toLowerCase(Locale.ROOT);
        if (enabledStructures.contains(normalized)) {
            enabledStructures.remove(normalized);
        } else {
            enabledStructures.add(normalized);
        }
    }
    
    // ==================== 标签操作 ====================
    
    /**
     * 检查标签是否启用
     */
    public boolean isTagEnabled(String tagId) {
        if (tagId == null) return false;
        return enabledTags.contains(tagId.toLowerCase(Locale.ROOT));
    }
    
    /**
     * 启用一个标签（及其下的所有结构）
     */
    public void enableTag(String tagId) {
        if (tagId == null) return;
        String normalized = tagId.toLowerCase(Locale.ROOT);
        enabledTags.add(normalized);
        
        // 如果不使用标签模式，把标签下的结构加入启用列表
        if (!useTagMode) {
            StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
            if (result != null) {
                Set<String> structures = result.getStructuresInTag(normalized);
                enabledStructures.addAll(structures);
            }
        }
    }
    
    /**
     * 禁用一个标签（及其下的所有结构）
     */
    public void disableTag(String tagId) {
        if (tagId == null) return;
        String normalized = tagId.toLowerCase(Locale.ROOT);
        enabledTags.remove(normalized);
        
        // 如果不使用标签模式，把标签下的结构从启用列表移除
        if (!useTagMode) {
            StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
            if (result != null) {
                Set<String> structures = result.getStructuresInTag(normalized);
                enabledStructures.removeAll(structures);
            }
        }
    }
    
    /**
     * 切换标签的启用状态
     */
    public void toggleTag(String tagId) {
        if (tagId == null) return;
        String normalized = tagId.toLowerCase(Locale.ROOT);
        if (enabledTags.contains(normalized)) {
            disableTag(tagId);
        } else {
            enableTag(tagId);
        }
    }
    
    // ==================== 批量操作 ====================
    
    /**
     * 启用所有村庄类结构（默认配置）
     */
    public void enableDefaultVillages() {
        enableTag("minecraft:village");
    }
    
    /**
     * 清除所有选择
     */
    public void clearAll() {
        enabledStructures.clear();
        enabledTags.clear();
    }
    
    /**
     * 启用所有结构
     */
    public void enableAll() {
        StructureDiscoveryService.DiscoveryResult result = StructureDiscoveryService.getResult();
        if (result != null) {
            for (StructureEntry entry : result.allStructures()) {
                enabledStructures.add(entry.id().toString().toLowerCase(Locale.ROOT));
            }
            for (StructureTagEntry tag : result.tags()) {
                enabledTags.add(tag.tagId().toString().toLowerCase(Locale.ROOT));
            }
        }
    }
    
    // ==================== 配置访问 ====================
    
    /**
     * 获取所有启用的结构 ID（用于实际筛选）
     */
    public Set<String> getEnabledStructures() {
        Set<String> result = new LinkedHashSet<>(enabledStructures);
        
        // 如果使用标签模式，展开所有启用的标签
        if (useTagMode && !enabledTags.isEmpty()) {
            StructureDiscoveryService.DiscoveryResult discovery = StructureDiscoveryService.getResult();
            if (discovery != null) {
                for (String tagId : enabledTags) {
                    result.addAll(discovery.getStructuresInTag(tagId));
                }
            }
        }
        
        return Collections.unmodifiableSet(result);
    }
    
    /**
     * 获取启用的标签 ID 集合
     */
    public Set<String> getEnabledTags() {
        return Collections.unmodifiableSet(enabledTags);
    }
    
    /**
     * 转换为白名单格式（兼容旧系统）
     * 
     * 返回标签形式（#minecraft:village）或具体结构 ID
     */
    public List<String> toWhitelist() {
        List<String> result = new ArrayList<>();
        
        // 添加标签（带 # 前缀）
        for (String tagId : enabledTags) {
            result.add("#" + tagId);
        }
        
        // 添加单独启用的结构（不在任何标签中的）
        Set<String> taggedStructures = new HashSet<>();
        StructureDiscoveryService.DiscoveryResult discovery = StructureDiscoveryService.getResult();
        if (discovery != null) {
            for (String tagId : enabledTags) {
                taggedStructures.addAll(discovery.getStructuresInTag(tagId));
            }
        }
        
        for (String structId : enabledStructures) {
            if (!taggedStructures.contains(structId)) {
                result.add(structId);
            }
        }
        
        return result;
    }
    
    /**
     * 检查是否有任何选择
     */
    public boolean hasAnySelection() {
        return !enabledStructures.isEmpty() || !enabledTags.isEmpty();
    }
    
    // ==================== 持久化 ====================
    
    private Path getConfigFilePath() {
        return Platform.getConfigFolder().resolve("roadweaver").resolve(CONFIG_FILE);
    }
    
    /**
     * 保存配置到文件
     */
    public void save() {
        try {
            Path file = getConfigFilePath();
            Files.createDirectories(file.getParent());
            
            ConfigData data = new ConfigData();
            data.enabledStructures = new ArrayList<>(enabledStructures);
            data.enabledTags = new ArrayList<>(enabledTags);
            data.useTagMode = useTagMode;
            
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
            
            LOGGER.debug("Saved structure selection config");
        } catch (Exception e) {
            LOGGER.warn("Failed to save structure selection config", e);
        }
    }
    
    /**
     * 从文件加载配置
     */
    private void load() {
        Path file = getConfigFilePath();
        if (!Files.exists(file)) {
            // 默认启用村庄
            enableDefaultVillages();
            save();
            return;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data != null) {
                if (data.enabledStructures != null) {
                    enabledStructures.addAll(data.enabledStructures);
                }
                if (data.enabledTags != null) {
                    enabledTags.addAll(data.enabledTags);
                }
                useTagMode = data.useTagMode;
            }
            LOGGER.debug("Loaded structure selection: {} structures, {} tags", 
                    enabledStructures.size(), enabledTags.size());
        } catch (Exception e) {
            LOGGER.warn("Failed to load structure selection config, using defaults", e);
            enableDefaultVillages();
        }
    }
    
    /**
     * 配置数据格式
     */
    private static class ConfigData {
        List<String> enabledStructures = new ArrayList<>();
        List<String> enabledTags = new ArrayList<>();
        boolean useTagMode = true;
    }
}
