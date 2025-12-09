package net.shiroha233.roadweaver.structures.precompute;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预计算结构存储服务
 * 
 * 存储道路规划阶段预计算的结构位置，供区块生成阶段使用。
 * 
 * 工作流程：
 * 1. 道路规划完成后，调用 addPendingStructure() 添加待放置结构
 * 2. 区块生成时（STRUCTURE_STARTS 阶段），Mixin 调用 getPendingStructures() 获取该区块的结构
 * 3. 结构被注入后，调用 markAsInjected() 标记已处理
 */
public final class PendingStructureStorage {
    private PendingStructureStorage() {}
    
    // 按维度和区块索引的待放置结构
    // Key: dimension -> chunkKey -> List<PendingRoadsideStructure>
    private static final ConcurrentHashMap<ResourceLocation, ConcurrentHashMap<Long, List<PendingRoadsideStructure>>> PENDING = new ConcurrentHashMap<>();
    
    // 已注入的区块（避免重复注入）- 使用 LRU 限制大小
    private static final int MAX_INJECTED_PER_DIM = 4096;
    private static final ConcurrentHashMap<ResourceLocation, Set<Long>> INJECTED = new ConcurrentHashMap<>();
    
    /**
     * 创建带大小限制的已注入区块集合
     */
    private static Set<Long> createLimitedSet() {
        return java.util.Collections.newSetFromMap(
            java.util.Collections.synchronizedMap(
                new java.util.LinkedHashMap<>(256, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(java.util.Map.Entry<Long, Boolean> eldest) {
                        return size() > MAX_INJECTED_PER_DIM;
                    }
                }
            )
        );
    }
    
    /**
     * 添加待放置的结构
     * 
     * @param level       世界
     * @param structureId 结构 ID
     * @param anchor      放置锚点
     * @param rotation    旋转
     * @param sizeX       结构尺寸 X
     * @param sizeY       结构尺寸 Y
     * @param sizeZ       结构尺寸 Z
     */
    public static void addPendingStructure(ServerLevel level, 
                                           ResourceLocation structureId,
                                           BlockPos anchor, 
                                           Rotation rotation,
                                           int sizeX, int sizeY, int sizeZ) {
        ResourceLocation dimKey = level.dimension().location();
        PendingRoadsideStructure pending = new PendingRoadsideStructure(
            structureId, anchor, rotation, sizeX, sizeY, sizeZ
        );
        
        long chunkKey = pending.chunkKey();
        PENDING.computeIfAbsent(dimKey, k -> new ConcurrentHashMap<>())
               .computeIfAbsent(chunkKey, k -> Collections.synchronizedList(new ArrayList<>()))
               .add(pending);
    }
    
    /**
     * 获取指定区块的待放置结构
     * 
     * @param level 世界
     * @param chunkPos 区块坐标
     * @return 该区块的待放置结构列表（可能为空）
     */
    public static List<PendingRoadsideStructure> getPendingStructures(ServerLevel level, ChunkPos chunkPos) {
        ResourceLocation dimKey = level.dimension().location();
        long chunkKey = ((long) chunkPos.x << 32) | (chunkPos.z & 0xFFFFFFFFL);
        
        // 检查是否已注入
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
    
    /**
     * 标记区块已完成结构注入
     */
    public static void markAsInjected(ServerLevel level, ChunkPos chunkPos) {
        ResourceLocation dimKey = level.dimension().location();
        long chunkKey = ((long) chunkPos.x << 32) | (chunkPos.z & 0xFFFFFFFFL);
        
        INJECTED.computeIfAbsent(dimKey, k -> createLimitedSet())
                .add(chunkKey);
        
        // 移除已处理的待放置结构（释放内存）
        var dimMap = PENDING.get(dimKey);
        if (dimMap != null) {
            dimMap.remove(chunkKey);
        }
    }
    
    /**
     * 检查区块是否有待放置结构
     */
    public static boolean hasPendingStructures(ServerLevel level, ChunkPos chunkPos) {
        ResourceLocation dimKey = level.dimension().location();
        long chunkKey = ((long) chunkPos.x << 32) | (chunkPos.z & 0xFFFFFFFFL);
        
        // 已注入则无待放置
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
    
    /**
     * 清理指定维度的数据（世界卸载时调用）
     */
    public static void clearDimension(ResourceLocation dimension) {
        PENDING.remove(dimension);
        INJECTED.remove(dimension);
    }
    
    /**
     * 清理所有数据（服务器关闭时调用）
     */
    public static void clearAll() {
        PENDING.clear();
        INJECTED.clear();
    }
    
    /**
     * 获取待放置结构总数（调试用）
     */
    public static int getPendingCount(ServerLevel level) {
        ResourceLocation dimKey = level.dimension().location();
        var dimMap = PENDING.get(dimKey);
        if (dimMap == null) {
            return 0;
        }
        return dimMap.values().stream().mapToInt(List::size).sum();
    }
}
