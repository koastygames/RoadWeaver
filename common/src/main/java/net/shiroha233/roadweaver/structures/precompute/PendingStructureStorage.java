package net.shiroha233.roadweaver.structures.precompute;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预计算结构存储服务
 */
public final class PendingStructureStorage {
    private PendingStructureStorage() {}
    
    private static final ConcurrentHashMap<Identifier, ConcurrentHashMap<Long, List<PendingRoadsideStructure>>> PENDING = new ConcurrentHashMap<>();
    
    private static final int MAX_INJECTED_PER_DIM = 4096;
    private static final ConcurrentHashMap<Identifier, Set<Long>> INJECTED = new ConcurrentHashMap<>();
    
    private static Set<Long> createLimitedSet() {
        return java.util.Collections.newSetFromMap(
            java.util.Collections.synchronizedMap(
                new java.util.LinkedHashMap<Long, Boolean>(256, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(java.util.Map.Entry<Long, Boolean> eldest) {
                        return size() > MAX_INJECTED_PER_DIM;
                    }
                }
            )
        );
    }
    
    public static void addPendingStructure(ServerLevel level, 
                                           Identifier structureId,
                                           BlockPos anchor, 
                                           Rotation rotation,
                                           int sizeX, int sizeY, int sizeZ) {
        Identifier dimKey = level.dimension().identifier();
        PendingRoadsideStructure pending = new PendingRoadsideStructure(
            structureId, anchor, rotation, sizeX, sizeY, sizeZ
        );
        
        long chunkKey = pending.chunkKey();
        PENDING.computeIfAbsent(dimKey, k -> new ConcurrentHashMap<>())
               .computeIfAbsent(chunkKey, k -> Collections.synchronizedList(new ArrayList<>()))
               .add(pending);
    }
    
    public static List<PendingRoadsideStructure> getPendingStructures(ServerLevel level, ChunkPos chunkPos) {
        Identifier dimKey = level.dimension().identifier();
        long chunkKey = ((long) chunkPos.x << 32) | (chunkPos.z & 0xFFFFFFFFL);
        
        Set<Long> injected = INJECTED.get(dimKey);
        if (injected != null && injected.contains(chunkKey)) {
            return Collections.emptyList();
        }
        
        var dimMap = PENDING.get(dimKey);
        if (dimMap == null) {
            return Collections.emptyList();
        }
        
        List<PendingRoadsideStructure> structures = dimMap.get(chunkKey);
        return structures != null ? new ArrayList<>(structures) : Collections.emptyList();
    }
    
    public static void markAsInjected(ServerLevel level, ChunkPos chunkPos) {
        Identifier dimKey = level.dimension().identifier();
        long chunkKey = ((long) chunkPos.x << 32) | (chunkPos.z & 0xFFFFFFFFL);
        
        INJECTED.computeIfAbsent(dimKey, k -> createLimitedSet())
                .add(chunkKey);
        
        var dimMap = PENDING.get(dimKey);
        if (dimMap != null) {
            dimMap.remove(chunkKey);
        }
    }
    
    public static boolean hasPendingStructures(ServerLevel level, ChunkPos chunkPos) {
        Identifier dimKey = level.dimension().identifier();
        long chunkKey = ((long) chunkPos.x << 32) | (chunkPos.z & 0xFFFFFFFFL);
        
        Set<Long> injected = INJECTED.get(dimKey);
        if (injected != null && injected.contains(chunkKey)) {
            return false;
        }
        
        var dimMap = PENDING.get(dimKey);
        if (dimMap == null) {
            return false;
        }
        
        List<PendingRoadsideStructure> structures = dimMap.get(chunkKey);
        return structures != null && !structures.isEmpty();
    }
    
    public static void clearDimension(Identifier dimension) {
        PENDING.remove(dimension);
        INJECTED.remove(dimension);
    }
    
    public static void clearAll() {
        PENDING.clear();
        INJECTED.clear();
    }
    
    public static int getPendingCount(ServerLevel level) {
        Identifier dimKey = level.dimension().identifier();
        var dimMap = PENDING.get(dimKey);
        if (dimMap == null) {
            return 0;
        }
        return dimMap.values().stream().mapToInt(List::size).sum();
    }
}
