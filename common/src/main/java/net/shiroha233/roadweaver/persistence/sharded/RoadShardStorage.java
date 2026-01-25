package net.shiroha233.roadweaver.persistence.sharded;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.sqlite.RoadDatabaseManager;
import net.shiroha233.roadweaver.persistence.sqlite.RoadSqliteStorage;

import java.util.List;

/**
 * 道路数据存储（H2 实现）
 * 
 * 此类现在是 {@link RoadSqliteStorage} 的门面（Facade），
 * 保持向后兼容的 API，同时底层使用 H2 数据库。
 * 
 * 优势：
 * - 纯 Java 实现，无 native 依赖，避免平台审核问题
 * - 无需 LRU 缓存，数据直接存取数据库
 * - 支持并发读写，不阻塞区块生成
 * - 空间索引加速矩形范围查询
 * - 自动持久化，无需手动 flush
 */
public final class RoadShardStorage {
    private RoadShardStorage() {}

    /**
     * 添加道路数据
     */
    public static void addRoad(ServerLevel level, Records.RoadData rd) {
        RoadSqliteStorage.addRoad(level, rd);
    }

    /**
     * 查询矩形范围内的道路数据
     */
    public static List<Records.RoadData> queryRect(ServerLevel level, 
                                                    int minBlockX, int minBlockZ, 
                                                    int maxBlockX, int maxBlockZ) {
        return RoadSqliteStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
    }

    /**
     * 刷新数据（H2 自动持久化，此方法执行检查点）
     */
    public static void flushAll(ServerLevel level) {
        RoadSqliteStorage.flushAll(level);
    }

    /**
     * 清除维度的所有道路数据（谨慎使用）
     */
    public static void clearAll(ServerLevel level) {
        RoadSqliteStorage.clearAll(level);
    }

    /**
     * 关闭数据库连接（服务器停止时调用）
     */
    public static void shutdown() {
        RoadSqliteStorage.shutdown();
    }
    
    /**
     * 关闭指定维度的数据库连接（维度卸载时调用）
     */
    public static void closeConnection(ServerLevel level) {
        RoadDatabaseManager.checkpoint(level);
        RoadDatabaseManager.closeConnection(level);
    }
}
