package net.shiroha233.roadweaver.persistence.sharded;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.persistence.files.RoadFileStorage;

import java.util.List;

/**
 * 道路数据存储门面，底层委托给文件型存储。
 */
public final class RoadShardStorage {
    private RoadShardStorage() {}

    public static void addRoad(ServerLevel level, RoadData rd) {
        RoadFileStorage.addRoad(level, rd);
    }

    public static List<RoadData> queryRect(ServerLevel level,
                                           int minBlockX, int minBlockZ,
                                           int maxBlockX, int maxBlockZ) {
        return RoadFileStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
    }

    public static List<RoadData> loadAll(ServerLevel level) {
        return RoadFileStorage.loadAll(level);
    }

    public static boolean hasAnyRoad(ServerLevel level) {
        return RoadFileStorage.hasAnyRoad(level);
    }

    public static boolean hasRoadInRect(ServerLevel level,
                                        int minBlockX, int minBlockZ,
                                        int maxBlockX, int maxBlockZ) {
        return RoadFileStorage.hasRoadInRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
    }

    public static void flushAll(ServerLevel level) {
        // 文件型存储写入即落盘，这里保留门面以兼容调用方。
    }

    public static void clearAll(ServerLevel level) {
        RoadFileStorage.clearAll(level);
    }

    public static void shutdown() {
        // 文件型存储无后台写执行器。
    }

    public static void closeConnection(ServerLevel level) {
        // 兼容旧调用点，文件型存储不需要连接关闭。
    }
}
