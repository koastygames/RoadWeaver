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
 */
public final class RoadsideStructureRegistry {
    private RoadsideStructureRegistry() {}
    
    private static final Map<ResourceKey<?>, List<RoadsideStructureEntry>> CACHE = new ConcurrentHashMap<>();
    
    public record RoadsideStructureEntry(
        ResourceLocation id,
        Holder<Structure> holder,
        RoadsideStructure structure
    ) {}
    
    public static List<RoadsideStructureEntry> getAll(ServerLevel level) {
        ResourceKey<?> dimensionKey = level.dimension();
        return CACHE.computeIfAbsent(dimensionKey, k -> loadFromRegistry(level.registryAccess()));
    }
    
    public static RoadsideStructureEntry choose(ServerLevel level,
                                                 BiomeCategory biome,
                                                 int roadLength,
                                                 RandomSource random) {
        List<RoadsideStructureEntry> all = getAll(level);
        List<RoadsideStructureEntry> candidates = new ArrayList<>();
        int totalWeight = 0;
        
        for (RoadsideStructureEntry entry : all) {
            RoadsideStructure structure = entry.structure();
            
            if (!structure.placementRule().isBiomeAllowed(biome)) {
                continue;
            }
            
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
    
    private static List<RoadsideStructureEntry> loadFromRegistry(RegistryAccess registryAccess) {
        List<RoadsideStructureEntry> result = new ArrayList<>();
        
        Registry<Structure> structureRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE);
        
        for (var entry : structureRegistry.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            Structure structure = entry.getValue();
            
            if (structure instanceof RoadsideStructure roadsideStructure) {
                Holder<Structure> holder = structureRegistry.getHolderOrThrow(entry.getKey());
                result.add(new RoadsideStructureEntry(id, holder, roadsideStructure));
            }
        }
        
        return result;
    }
    
    public static void clearCache() {
        CACHE.clear();
    }
    
    public static void clearCache(ResourceKey<?> dimensionKey) {
        CACHE.remove(dimensionKey);
    }
}
