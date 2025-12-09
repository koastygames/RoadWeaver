package net.shiroha233.roadweaver.structures.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.shiroha233.roadweaver.structures.data.BiomeCategory;
import net.shiroha233.roadweaver.structures.types.RoadsideStructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路边结构注册中心
 * 
 * 从世界的 Structure 注册表中读取所有 RoadsideStructure，
 * 提供根据条件筛选和选择结构的方法。
 * 
 * 数据来源：datapack 中的 worldgen/structure/*.json
 */
public final class RoadsideStructureRegistry {
    private RoadsideStructureRegistry() {}
    
    // 缓存：每个世界的路边结构列表
    private static final Map<ResourceKey<?>, List<RoadsideStructureEntry>> CACHE = new ConcurrentHashMap<>();
    
    /**
     * 结构条目，包含 Holder 引用和解析后的结构
     */
    public record RoadsideStructureEntry(
        ResourceLocation id,
        Holder<Structure> holder,
        RoadsideStructure structure
    ) {}
    
    /**
     * 获取所有已注册的路边结构
     * 
     * @param level 服务端世界（用于获取注册表）
     * @return 路边结构列表
     */
    public static List<RoadsideStructureEntry> getAll(ServerLevel level) {
        ResourceKey<?> dimensionKey = level.dimension();
        return CACHE.computeIfAbsent(dimensionKey, k -> loadFromRegistry(level.registryAccess()));
    }
    
    /**
     * 根据条件选择一个路边结构
     * 
     * @param level      服务端世界
     * @param biome      群系分类
     * @param roadLength 道路长度
     * @param random     随机源
     * @return 选中的结构，如果没有符合条件的返回 null
     */
    public static RoadsideStructureEntry choose(ServerLevel level,
                                                 BiomeCategory biome,
                                                 int roadLength,
                                                 RandomSource random) {
        List<RoadsideStructureEntry> all = getAll(level);
        List<RoadsideStructureEntry> candidates = new ArrayList<>();
        int totalWeight = 0;
        
        for (RoadsideStructureEntry entry : all) {
            RoadsideStructure structure = entry.structure();
            
            // 群系过滤
            if (!structure.placementRule().isBiomeAllowed(biome)) {
                continue;
            }
            
            // 道路长度过滤
            if (!structure.placementRule().isRoadLongEnough(roadLength)) {
                continue;
            }
            
            int weight = structure.weight();
            if (weight <= 0) {
                continue;
            }
            
            candidates.add(entry);
            totalWeight += weight;
        }
        
        if (candidates.isEmpty() || totalWeight <= 0) {
            return null;
        }
        
        // 权重随机选择
        int roll = random.nextInt(totalWeight);
        int sum = 0;
        for (RoadsideStructureEntry entry : candidates) {
            sum += entry.structure().weight();
            if (roll < sum) {
                return entry;
            }
        }
        
        return candidates.get(0);
    }
    
    /**
     * 从注册表加载所有路边结构
     */
    private static List<RoadsideStructureEntry> loadFromRegistry(RegistryAccess registryAccess) {
        List<RoadsideStructureEntry> result = new ArrayList<>();
        
        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        
        for (var entry : structureRegistry.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            Structure structure = entry.getValue();
            
            // 只收集 RoadsideStructure 类型
            if (structure instanceof RoadsideStructure roadsideStructure) {
                Holder<Structure> holder = structureRegistry.getHolderOrThrow(entry.getKey());
                result.add(new RoadsideStructureEntry(id, holder, roadsideStructure));
            }
        }
        
        return result;
    }
    
    /**
     * 清除缓存（在世界卸载或重载时调用）
     */
    public static void clearCache() {
        CACHE.clear();
    }
    
    /**
     * 清除指定维度的缓存
     */
    public static void clearCache(ResourceKey<?> dimensionKey) {
        CACHE.remove(dimensionKey);
    }
}
