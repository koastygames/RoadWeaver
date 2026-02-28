package net.shiroha233.roadweaver.persistence.sharded;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.persistence.sqlite.RoadDatabaseManager;
import net.shiroha233.roadweaver.persistence.sqlite.RoadSqliteStorage;

import java.util.List;

/**
 * 道路数据存储门面，底层委托给 RoadSqliteStorage（H2 实现）
 */
public final class RoadShardStorage {
    private RoadShardStorage() {}

    public static void addRoad(ServerLevel level, RoadData rd) {
        RoadSqliteStorage.addRoad(level, rd);
    }

    public static List<RoadData> queryRect(ServerLevel level,
                                           int minBlockX, int minBlockZ,
                                           int maxBlockX, int maxBlockZ) {
        return RoadSqliteStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
    }

    public static void flushAll(ServerLevel level) {
        RoadSqliteStorage.flushAll(level);
    }

    public static void clearAll(ServerLevel level) {
        RoadSqliteStorage.clearAll(level);
    }

    public static void shutdown() {
        RoadSqliteStorage.shutdown();
    }

    public static void closeConnection(ServerLevel level) {
        RoadDatabaseManager.checkpoint(level);
        RoadDatabaseManager.closeConnection(level);
    }
}
