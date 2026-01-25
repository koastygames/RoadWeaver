package net.shiroha233.roadweaver.config.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
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
        private final List<ResourceLocation> dimensions;
        private final List<StructureTagEntry> tags;
        private final List<StructureEntry> allStructures;
        private final Map<String, Set<String>> tagToStructures; // tagId -> structureIds

        public DiscoveryResult(List<ResourceLocation> dimensions, List<StructureTagEntry> tags, List<StructureEntry> allStructures) {
            this.dimensions = new ArrayList<>(dimensions == null ? List.of() : dimensions);
            this.tags = new ArrayList<>(tags);
            this.allStructures = new ArrayList<>(allStructures);
            Collections.sort(this.dimensions);
            Collections.sort(this.tags);
            Collections.sort(this.allStructures);

            this.tagToStructures = new ConcurrentHashMap<>();
            for (StructureTagEntry tag : tags) {
                tagToStructures.put(tag.tagId().toString(), tag.getAllStructureIds());
            }
        }

        public List<ResourceLocation> dimensions() {
            return Collections.unmodifiableList(dimensions);
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
        RegistryAccess access = level.registryAccess();
        Registry<Structure> structureRegistry = access.registryOrThrow(Registries.STRUCTURE);
        Registry<LevelStem> levelStemRegistry = access.registryOrThrow(Registries.LEVEL_STEM);
        discoverFromRegistries(structureRegistry, levelStemRegistry);
    }

    public static void discoverFromRegistries(Registry<Structure> structureRegistry, Registry<LevelStem> levelStemRegistry) {
        if (structureRegistry == null || levelStemRegistry == null) return;

        try {
            // 收集所有维度（来自 LEVEL_STEM）
            List<ResourceLocation> discoveredDimensions = levelStemRegistry.keySet().stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .collect(Collectors.toList());

            // 预计算每个维度可能出现的生物群系集合，用于推断结构可生成维度
            Map<ResourceLocation, Set<Holder<Biome>>> possibleBiomesByDimension = levelStemRegistry.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey().location(),
                            e -> {
                                try {
                                    return e.getValue().generator().getBiomeSource().possibleBiomes();
                                } catch (Exception ignored) {
                                    return Set.of();
                                }
                            }
                    ));

            // 收集所有结构（并推断可生成维度）
            Map<ResourceLocation, StructureEntry> structureMap = new LinkedHashMap<>();
            for (var entry : structureRegistry.entrySet()) {
                ResourceLocation id = entry.getKey().location();
                Structure structure = entry.getValue();
                boolean isVanilla = "minecraft".equals(id.getNamespace());
                String displayName = formatDisplayName(id);

                Set<ResourceLocation> dimensions = new LinkedHashSet<>();
                try {
                    var structureBiomes = structure.biomes();
                    for (ResourceLocation dimId : discoveredDimensions) {
                        Set<Holder<Biome>> dimPossible = possibleBiomesByDimension.getOrDefault(dimId, Set.of());
                        if (dimPossible.isEmpty()) continue;

                        boolean matches = structureBiomes.stream().anyMatch(dimPossible::contains);
                        if (matches) dimensions.add(dimId);
                    }
                } catch (Exception ignored) {
                }

                structureMap.put(id, new StructureEntry(id, displayName, isVanilla, dimensions));
            }

            // 收集所有标签及其包含的结构
            List<StructureTagEntry> tagEntries = new ArrayList<>();
            Set<ResourceLocation> processedTags = new HashSet<>();

            for (Holder.Reference<Structure> holder : structureRegistry.holders().toList()) {
                holder.tags().forEach(tagKey -> {
                    ResourceLocation tagId = tagKey.location();
                    if (processedTags.contains(tagId)) return;
                    processedTags.add(tagId);

                    List<StructureEntry> tagStructures = new ArrayList<>();
                    for (Holder.Reference<Structure> h : structureRegistry.holders().toList()) {
                        if (h.is(tagKey)) {
                            ResourceLocation structId = h.key().location();
                            StructureEntry se = structureMap.get(structId);
                            if (se != null) tagStructures.add(se);
                        }
                    }

                    if (!tagStructures.isEmpty()) {
                        String displayName = formatTagDisplayName(tagId);
                        tagEntries.add(new StructureTagEntry(tagId, displayName, tagStructures));
                    }
                });
            }

            cachedResult = new DiscoveryResult(discoveredDimensions, tagEntries, new ArrayList<>(structureMap.values()));
            hasDiscovered = true;

            LOGGER.info("Discovered {} structures and {} tags from registries", structureMap.size(), tagEntries.size());
            saveCacheToFile();
        } catch (Exception e) {
            LOGGER.error("Failed to discover structures", e);
        }
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
            Registry<LevelStem> levelStemRegistry = null;
            try {
                levelStemRegistry = registryAccess.registryOrThrow(Registries.LEVEL_STEM);
            } catch (Exception ignored) {
            }

            if (levelStemRegistry != null) {
                discoverFromRegistries(structureRegistry, levelStemRegistry);
                return;
            }

            // 兼容降级：仅有 Structure 注册表时，仍然构建旧格式（dimensions 为空）
            Map<ResourceLocation, StructureEntry> structureMap = new LinkedHashMap<>();
            for (var entry : structureRegistry.entrySet()) {
                ResourceLocation id = entry.getKey().location();
                boolean isVanilla = "minecraft".equals(id.getNamespace());
                String displayName = formatDisplayName(id);
                structureMap.put(id, new StructureEntry(id, displayName, isVanilla));
            }

            List<StructureTagEntry> tagEntries = new ArrayList<>();
            Set<ResourceLocation> processedTags = new HashSet<>();
            for (Holder.Reference<Structure> holder : structureRegistry.holders().toList()) {
                holder.tags().forEach(tagKey -> {
                    ResourceLocation tagId = tagKey.location();
                    if (processedTags.contains(tagId)) return;
                    processedTags.add(tagId);

                    List<StructureEntry> tagStructures = new ArrayList<>();
                    for (Holder.Reference<Structure> h : structureRegistry.holders().toList()) {
                        if (h.is(tagKey)) {
                            ResourceLocation structId = h.key().location();
                            StructureEntry se = structureMap.get(structId);
                            if (se != null) tagStructures.add(se);
                        }
                    }
                    if (!tagStructures.isEmpty()) {
                        String displayName = formatTagDisplayName(tagId);
                        tagEntries.add(new StructureTagEntry(tagId, displayName, tagStructures));
                    }
                });
            }

            cachedResult = new DiscoveryResult(List.of(), tagEntries, new ArrayList<>(structureMap.values()));
            hasDiscovered = true;
            LOGGER.info("Discovered {} structures and {} tags", structureMap.size(), tagEntries.size());
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
     * 注意：客户端结构发现已移除反射实现，改为依赖：
     * 1. 服务端在玩家进入世界时主动调用 discoverFromLevel()
     * 2. 缓存文件（structure_cache.json）
     */
    public static void tryDiscoverFromCurrentContext() {
        // 客户端不再使用反射获取注册表，避免模组审核问题
        // 结构发现主要依赖服务端调用和缓存文件
        LOGGER.debug("tryDiscoverFromCurrentContext: 跳过客户端反射获取，使用缓存文件");
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
            data.dimensions = cachedResult.dimensions().stream().map(ResourceLocation::toString).collect(Collectors.toList());
            data.structures = cachedResult.allStructures().stream()
                    .map(e -> new CacheData.StructureData(
                            e.id().toString(),
                            e.displayName(),
                            e.isVanilla(),
                            e.dimensions().stream().map(ResourceLocation::toString).collect(Collectors.toList())
                    ))
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
            
            // 维度列表
            List<ResourceLocation> discoveredDimensions = new ArrayList<>();
            if (data.dimensions != null) {
                for (String dimStr : data.dimensions) {
                    ResourceLocation rl = ResourceLocation.tryParse(dimStr);
                    if (rl != null) discoveredDimensions.add(rl);
                }
            }

            // 重建结构映射
            Map<String, StructureEntry> structureMap = new LinkedHashMap<>();
            for (CacheData.StructureData sd : data.structures) {
                ResourceLocation id = ResourceLocation.tryParse(sd.id);
                if (id != null) {
                    Set<ResourceLocation> dims = new LinkedHashSet<>();
                    if (sd.dimensions != null) {
                        for (String dimStr : sd.dimensions) {
                            ResourceLocation rl = ResourceLocation.tryParse(dimStr);
                            if (rl != null) dims.add(rl);
                        }
                    }
                    structureMap.put(sd.id, new StructureEntry(id, sd.displayName, sd.isVanilla, dims));
                }
            }

            if (discoveredDimensions.isEmpty()) {
                Set<ResourceLocation> derived = new LinkedHashSet<>();
                for (StructureEntry se : structureMap.values()) {
                    derived.addAll(se.dimensions());
                }
                discoveredDimensions.addAll(derived);
                Collections.sort(discoveredDimensions);
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
            
            cachedResult = new DiscoveryResult(discoveredDimensions, tagEntries, new ArrayList<>(structureMap.values()));
            LOGGER.info("Loaded structure cache: {} structures, {} tags", structureMap.size(), tagEntries.size());
            
        } catch (Exception e) {
            LOGGER.warn("Failed to load structure cache", e);
        }
    }
    
    /**
     * 缓存数据格式（用于 JSON 序列化）
     */
    private static class CacheData {
        List<String> dimensions;
        List<StructureData> structures;
        List<TagData> tags;
        
        static class StructureData {
            String id;
            String displayName;
            boolean isVanilla;
            List<String> dimensions;
            
            StructureData(String id, String displayName, boolean isVanilla, List<String> dimensions) {
                this.id = id;
                this.displayName = displayName;
                this.isVanilla = isVanilla;
                this.dimensions = dimensions;
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
