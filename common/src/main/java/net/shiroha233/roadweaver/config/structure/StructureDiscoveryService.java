package net.shiroha233.roadweaver.config.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 结构发现服务
 * 
 * 职责：
 * - 从服务端注册表收集所有结构和标签
 * - 缓存结果供客户端 GUI 使用
 * - 支持保存/加载到本地文件（供离线使用）
 */
public final class StructureDiscoveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/StructureDiscovery");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CACHE_FILE = "structure_cache.json";
    
    // 缓存的结构数据
    private static volatile DiscoveryResult cachedResult = null;
    
    // 线程安全的标记，表示是否已从服务端收集过
    private static volatile boolean hasDiscovered = false;
    
    private StructureDiscoveryService() {}
    
    /**
     * 发现结果
     */
    public static final class DiscoveryResult {
        private final List<StructureTagEntry> tags;
        private final List<StructureEntry> allStructures;
        private final Map<String, Set<String>> tagToStructures; // tagId -> structureIds
        
        public DiscoveryResult(List<StructureTagEntry> tags, List<StructureEntry> allStructures) {
            this.tags = new ArrayList<>(tags);
            this.allStructures = new ArrayList<>(allStructures);
            Collections.sort(this.tags);
            Collections.sort(this.allStructures);
            
            this.tagToStructures = new ConcurrentHashMap<>();
            for (StructureTagEntry tag : tags) {
                tagToStructures.put(tag.tagId().toString(), tag.getAllStructureIds());
            }
        }
        
        public List<StructureTagEntry> tags() {
            return Collections.unmodifiableList(tags);
        }
        
        public List<StructureEntry> allStructures() {
            return Collections.unmodifiableList(allStructures);
        }
        
        /**
         * 获取指定标签下的所有结构 ID
         */
        public Set<String> getStructuresInTag(String tagId) {
            return tagToStructures.getOrDefault(tagId, Set.of());
        }
        
        /**
         * 检查结构是否属于指定标签
         */
        public boolean isStructureInTag(String structureId, String tagId) {
            Set<String> structures = tagToStructures.get(tagId);
            return structures != null && structures.contains(structureId);
        }
    }
    
    /**
     * 从服务端世界收集所有结构和标签信息
     * 
     * 应该在进入世界后调用（服务端）
     */
    public static void discoverFromLevel(ServerLevel level) {
        if (level == null) {
            LOGGER.warn("Cannot discover structures: level is null");
            return;
        }
        discoverFromRegistryAccess(level.registryAccess());
    }
    
    /**
     * 从 RegistryAccess 收集所有结构和标签信息
     * 
     * 可以在客户端创建世界界面或服务端调用
     */
    public static void discoverFromRegistryAccess(RegistryAccess registryAccess) {
        if (registryAccess == null) {
            LOGGER.warn("Cannot discover structures: registryAccess is null");
            return;
        }
        
        try {
            Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
            
            // 收集所有结构
            Map<ResourceLocation, StructureEntry> structureMap = new LinkedHashMap<>();
            for (var entry : structureRegistry.entrySet()) {
                ResourceLocation id = entry.getKey().location();
                boolean isVanilla = "minecraft".equals(id.getNamespace());
                String displayName = formatDisplayName(id);
                structureMap.put(id, new StructureEntry(id, displayName, isVanilla));
            }
            
            // 收集所有标签及其包含的结构
            List<StructureTagEntry> tagEntries = new ArrayList<>();
            Set<ResourceLocation> processedTags = new HashSet<>();
            
            // 遍历所有结构的标签
            for (Holder.Reference<Structure> holder : structureRegistry.holders().toList()) {
                holder.tags().forEach(tagKey -> {
                    ResourceLocation tagId = tagKey.location();
                    if (processedTags.contains(tagId)) return;
                    processedTags.add(tagId);
                    
                    // 收集此标签下的所有结构
                    List<StructureEntry> tagStructures = new ArrayList<>();
                    for (Holder.Reference<Structure> h : structureRegistry.holders().toList()) {
                        if (h.is(tagKey)) {
                            ResourceLocation structId = h.key().location();
                            StructureEntry se = structureMap.get(structId);
                            if (se != null) {
                                tagStructures.add(se);
                            }
                        }
                    }
                    
                    if (!tagStructures.isEmpty()) {
                        String displayName = formatTagDisplayName(tagId);
                        tagEntries.add(new StructureTagEntry(tagId, displayName, tagStructures));
                    }
                });
            }
            
            cachedResult = new DiscoveryResult(tagEntries, new ArrayList<>(structureMap.values()));
            hasDiscovered = true;
            
            LOGGER.info("Discovered {} structures and {} tags", structureMap.size(), tagEntries.size());
            
            // 保存到缓存文件
            saveCacheToFile();
            
        } catch (Exception e) {
            LOGGER.error("Failed to discover structures", e);
        }
    }
    
    /**
     * 获取缓存的发现结果
     * 
     * 如果尚未发现，尝试从当前上下文或文件加载
     */
    public static DiscoveryResult getResult() {
        if (cachedResult == null && !hasDiscovered) {
            // 尝试从当前上下文获取
            tryDiscoverFromCurrentContext();
        }
        if (cachedResult == null && !hasDiscovered) {
            // 从缓存文件加载
            loadCacheFromFile();
        }
        return cachedResult;
    }
    
    /**
     * 尝试从当前上下文获取结构注册表
     * 
     * 支持：
     * - 客户端已连接服务器（ClientLevel）
     * - 客户端创建世界界面（WorldCreationContext）
     */
    public static void tryDiscoverFromCurrentContext() {
        // 只在客户端执行
        if (Platform.getEnvironment() != Env.CLIENT) {
            return;
        }
        
        try {
            // 尝试从客户端获取（使用反射避免直接引用客户端类）
            RegistryAccess access = ClientRegistryAccessHelper.tryGetClientRegistryAccess();
            if (access != null) {
                discoverFromRegistryAccess(access);
            }
        } catch (Exception e) {
            // 静默失败，避免在某些加载阶段刷屏日志
        }
    }
    
    /**
     * 客户端注册表访问帮助类（内部类，避免外层类加载客户端依赖）
     */
    private static class ClientRegistryAccessHelper {
        static RegistryAccess tryGetClientRegistryAccess() {
            try {
                // 使用反射加载 Minecraft 类，避免在服务端加载时失败
                Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
                Object mc = minecraftClass.getMethod("getInstance").invoke(null);
                if (mc == null) return null;
                
                // 获取 level 字段
                var levelField = minecraftClass.getDeclaredField("level");
                levelField.setAccessible(true);
                Object level = levelField.get(mc);
                
                if (level != null) {
                    // 调用 level.registryAccess()
                    var registryAccessMethod = level.getClass().getMethod("registryAccess");
                    RegistryAccess access = (RegistryAccess) registryAccessMethod.invoke(level);
                    return access;
                }
                
                // 尝试从 CreateWorldScreen 获取
                var screenField = minecraftClass.getDeclaredField("screen");
                screenField.setAccessible(true);
                Object screen = screenField.get(mc);
                
                if (screen != null) {
                    Class<?> createWorldScreenClass = Class.forName("net.minecraft.client.gui.screens.worldselection.CreateWorldScreen");
                    if (createWorldScreenClass.isInstance(screen)) {
                        var uiStateField = createWorldScreenClass.getDeclaredField("uiState");
                        uiStateField.setAccessible(true);
                        var uiState = uiStateField.get(screen);
                        
                        var getSettingsMethod = uiState.getClass().getMethod("getSettings");
                        Object context = getSettingsMethod.invoke(uiState);
                        
                        if (context != null) {
                            var worldgenLoadContextMethod = context.getClass().getMethod("worldgenLoadContext");
                            return (RegistryAccess) worldgenLoadContextMethod.invoke(context);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to get client registry access: {}", e.getMessage(), e);
            }
            return null;
        }
    }
    
    /**
     * 检查是否有可用的发现结果
     */
    public static boolean hasResult() {
        return getResult() != null;
    }
    
    /**
     * 清除缓存
     */
    public static void clearCache() {
        cachedResult = null;
        hasDiscovered = false;
    }
    
    /**
     * 格式化结构显示名称
     */
    private static String formatDisplayName(ResourceLocation id) {
        String path = id.getPath();
        // 将下划线替换为空格，首字母大写
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }
    
    /**
     * 格式化标签显示名称
     */
    private static String formatTagDisplayName(ResourceLocation tagId) {
        return "#" + formatDisplayName(tagId);
    }
    
    // ==================== 缓存文件操作 ====================
    
    private static Path getCacheFilePath() {
        return Platform.getConfigFolder().resolve("roadweaver").resolve(CACHE_FILE);
    }
    
    private static void saveCacheToFile() {
        if (cachedResult == null) return;
        
        try {
            Path file = getCacheFilePath();
            Files.createDirectories(file.getParent());
            
            // 转换为可序列化的格式
            CacheData data = new CacheData();
            data.structures = cachedResult.allStructures().stream()
                    .map(e -> new CacheData.StructureData(e.id().toString(), e.displayName(), e.isVanilla()))
                    .collect(Collectors.toList());
            data.tags = cachedResult.tags().stream()
                    .map(t -> new CacheData.TagData(
                            t.tagId().toString(),
                            t.displayName(),
                            t.structures().stream().map(s -> s.id().toString()).collect(Collectors.toList())
                    ))
                    .collect(Collectors.toList());
            
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
            
            LOGGER.debug("Saved structure cache to {}", file);
        } catch (Exception e) {
            LOGGER.warn("Failed to save structure cache", e);
        }
    }
    
    private static void loadCacheFromFile() {
        Path file = getCacheFilePath();
        if (!Files.exists(file)) {
            LOGGER.debug("No structure cache file found");
            return;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            CacheData data = GSON.fromJson(reader, CacheData.class);
            if (data == null || data.structures == null || data.tags == null) {
                LOGGER.warn("Invalid structure cache file");
                return;
            }
            
            // 重建结构映射
            Map<String, StructureEntry> structureMap = new LinkedHashMap<>();
            for (CacheData.StructureData sd : data.structures) {
                ResourceLocation id = ResourceLocation.tryParse(sd.id);
                if (id != null) {
                    structureMap.put(sd.id, new StructureEntry(id, sd.displayName, sd.isVanilla));
                }
            }
            
            // 重建标签列表
            List<StructureTagEntry> tagEntries = new ArrayList<>();
            for (CacheData.TagData td : data.tags) {
                ResourceLocation tagId = ResourceLocation.tryParse(td.tagId);
                if (tagId == null) continue;
                
                List<StructureEntry> tagStructures = new ArrayList<>();
                for (String structId : td.structureIds) {
                    StructureEntry se = structureMap.get(structId);
                    if (se != null) {
                        tagStructures.add(se);
                    }
                }
                
                if (!tagStructures.isEmpty()) {
                    tagEntries.add(new StructureTagEntry(tagId, td.displayName, tagStructures));
                }
            }
            
            cachedResult = new DiscoveryResult(tagEntries, new ArrayList<>(structureMap.values()));
            LOGGER.info("Loaded structure cache: {} structures, {} tags", structureMap.size(), tagEntries.size());
            
        } catch (Exception e) {
            LOGGER.warn("Failed to load structure cache", e);
        }
    }
    
    /**
     * 缓存数据格式（用于 JSON 序列化）
     */
    private static class CacheData {
        List<StructureData> structures;
        List<TagData> tags;
        
        static class StructureData {
            String id;
            String displayName;
            boolean isVanilla;
            
            StructureData(String id, String displayName, boolean isVanilla) {
                this.id = id;
                this.displayName = displayName;
                this.isVanilla = isVanilla;
            }
        }
        
        static class TagData {
            String tagId;
            String displayName;
            List<String> structureIds;
            
            TagData(String tagId, String displayName, List<String> structureIds) {
                this.tagId = tagId;
                this.displayName = displayName;
                this.structureIds = structureIds;
            }
        }
    }
}
