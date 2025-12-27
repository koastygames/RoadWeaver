package net.shiroha233.roadweaver.runtime;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.generation.ChunkGenTracker;
import net.shiroha233.roadweaver.persistence.RoadSpatialIndex;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.structures.precompute.PendingStructureStorage;
import net.shiroha233.roadweaver.structures.registry.BridgeTemplateStructureRegistry;
import net.shiroha233.roadweaver.structures.registry.RoadsideStructureRegistry;
import net.shiroha233.roadweaver.persistence.sqlite.LegacyShardMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一缓存管理器
 * 
 * 集中管理 RoadWeaver 模组中所有缓存的生命周期，
 * 避免缓存分散导致的内存泄漏和清理遗漏问题。
 * 
 * 缓存清单：
 * 1. RoadSpatialIndex - 道路空间索引（用于树木阻拦，仅区块生成阶段使用）
 * 2. RoadShardStorage - 道路数据分片缓存（磁盘 + 内存 LRU）
 * 3. PendingStructureStorage - 待放置结构缓存
 * 4. RoadsideStructureRegistry - 路边结构注册表缓存
 * 5. RoadPlanningService - 规划状态缓存
 * 6. ChunkGenTracker - 区块生成阶段追踪（轻量级）
 * 
 * 重要优化：树木阻拦只在区块生成阶段（WorldGenRegion）生效，
 * 生成完成后玩家种植的树木不受影响。
 */
public final class CacheManager {
    private CacheManager() {}
    
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    
    /**
     * 服务器启动时初始化缓存
     */
    public static void onServerStarted() {
        // 清理可能残留的旧缓存
        RoadsideStructureRegistry.clearCache();
        BridgeTemplateStructureRegistry.clearCache();
        RoadSpatialIndex.clearAllCache();
        LOGGER.debug("CacheManager: 缓存已初始化");
    }
    
    /**
     * 服务器停止时清理所有缓存
     * 
     * @param levels 所有需要处理的世界
     */
    public static void onServerStopping(Iterable<ServerLevel> levels) {
        // 1. 先刷新磁盘缓存
        for (ServerLevel level : levels) {
            try {
                RoadShardStorage.flushAll(level);
            } catch (Exception e) {
                LOGGER.warn("清理 RoadShardStorage 缓存失败: {}", e.getMessage());
            }
        }
        
        // 1.5 关闭 SQLite 数据库连接，确保 WAL 检查点完成
        RoadShardStorage.shutdown();
        
        // 2. 清理内存缓存
        RoadsideStructureRegistry.clearCache();
        PendingStructureStorage.clearAll();
        RoadSpatialIndex.clearAllCache();
        RoadPlanningService.resetAll();
        ChunkGenTracker.clearAll();
        
        // 3. 重置迁移状态（确保下次启动可以正常检查）
        LegacyShardMigration.reset();
        
        LOGGER.debug("CacheManager: 所有缓存已清理");
    }
    
    /**
     * 清理指定维度的缓存（维度卸载时调用）
     */
    public static void onDimensionUnload(ServerLevel level) {
        if (level == null) return;
        
        try {
            RoadShardStorage.closeConnection(level);
        } catch (Exception e) {
            LOGGER.warn("清理维度 {} 的 RoadShardStorage 缓存失败: {}", 
                level.dimension().location(), e.getMessage());
        }
        
        RoadSpatialIndex.clearCache(level);
        PendingStructureStorage.clearDimension(level.dimension().location());
        RoadsideStructureRegistry.clearCache(level.dimension());
        BridgeTemplateStructureRegistry.clearCache(level.dimension());
        
        LOGGER.debug("CacheManager: 维度 {} 的缓存已清理", level.dimension().location());
    }
    
    /**
     * 道路数据更新后使相关缓存失效
     * 
     * @param level 世界
     * @param chunkX 区块 X
     * @param chunkZ 区块 Z
     */
    public static void invalidateRoadCache(ServerLevel level, int chunkX, int chunkZ) {
        RoadSpatialIndex.invalidateChunk(level, chunkX, chunkZ);
    }
    
    /**
     * 获取缓存统计信息（调试用）
     */
    public static String getStats() {
        // 未来可以添加各缓存的大小统计
        return "CacheManager: 统计功能待实现";
    }
}
