package net.shiroha233.roadweaver.features.path.pathlogic.bridge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.structures.registry.BridgeTemplateStructureRegistry;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 桥梁跨区块上下文缓存
 * 
 * 核心问题：
 * - 同一座桥梁跨越多个区块，但区块生成顺序不确定
 * - 每个区块独立选择模板会导致材质不一致
 * - 需要确保同一座桥梁在所有区块使用相同的模板和参数
 * 
 * 解决方案：
 * - 使用道路ID + 桥梁区间索引生成稳定的桥梁ID
 * - 首次处理桥梁时缓存选中的模板和参数
 * - 后续区块查询缓存获取一致的上下文
 */
public final class BridgeContextCache {
    private BridgeContextCache() {}
    
    /**
     * 桥梁上下文 - 包含桥梁生成所需的所有一致性信息
     */
    public static final class BridgeContext {
        private final long bridgeId;
        private final ResourceLocation templateId;
        private final int deckY;
        
        public BridgeContext(long bridgeId, ResourceLocation templateId, int deckY) {
            this.bridgeId = bridgeId;
            this.templateId = templateId;
            this.deckY = deckY;
        }
        
        public long bridgeId() { return bridgeId; }
        public ResourceLocation templateId() { return templateId; }
        public int deckY() { return deckY; }
    }
    
    // 缓存：维度 -> 桥梁ID -> 桥梁上下文
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, BridgeContext>> 
            dimensionCaches = new ConcurrentHashMap<>();
    
    /**
     * 生成稳定的桥梁ID
     * 基于道路的第一个和最后一个路段坐标 + 桥梁区间索引
     * 确保同一条道路上的同一座桥在不同区块获得相同ID
     */
    public static long generateBridgeId(long roadFingerprint, int bridgeRangeIndex) {
        return roadFingerprint ^ ((long) bridgeRangeIndex * 0x9E3779B97F4A7C15L);
    }
    
    /**
     * 从道路数据生成道路指纹
     */
    public static long generateRoadFingerprint(int firstX, int firstZ, int lastX, int lastZ) {
        long first = ((long) firstX << 32) | (firstZ & 0xFFFFFFFFL);
        long last = ((long) lastX << 32) | (lastZ & 0xFFFFFFFFL);
        return first ^ (last * 31);
    }
    
    /**
     * 获取或创建桥梁上下文
     */
    public static BridgeContext getOrCreate(ServerLevel level, long bridgeId, 
                                            double bridgeLength, int deckY) {
        String dimKey = level.dimension().location().toString();
        var cache = dimensionCaches.computeIfAbsent(dimKey, k -> new ConcurrentHashMap<>());
        
        // 先检查是否已缓存
        BridgeContext existing = cache.get(bridgeId);
        if (existing != null) {
            return existing;
        }
        
        // 创建新上下文
        var template = BridgeTemplateStructureRegistry.chooseByBridgeId(level, bridgeId, (int) bridgeLength);
        ResourceLocation templateId = template != null ? template.getId() : null;
        
        BridgeContext newCtx = new BridgeContext(bridgeId, templateId, deckY);
        cache.put(bridgeId, newCtx);
        return newCtx;
    }
    
    /**
     * 查询已缓存的桥梁上下文
     */
    public static BridgeContext get(ServerLevel level, long bridgeId) {
        String dimKey = level.dimension().location().toString();
        var cache = dimensionCaches.get(dimKey);
        if (cache == null) return null;
        return cache.get(bridgeId);
    }
    
    /**
     * 清除指定维度的缓存
     */
    public static void clearDimension(ServerLevel level) {
        String dimKey = level.dimension().location().toString();
        var cache = dimensionCaches.remove(dimKey);
        if (cache != null) cache.clear();
    }
    
    /**
     * 清除所有缓存
     */
    public static void clearAll() {
        dimensionCaches.clear();
    }
}
