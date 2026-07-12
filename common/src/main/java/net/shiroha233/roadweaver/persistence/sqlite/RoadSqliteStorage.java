package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.persistence.files.RoadFileStorage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 道路存储门面，底层已切换到文件型存储。
 */
public final class RoadSqliteStorage {
    private RoadSqliteStorage() {}

    public static void addRoad(ServerLevel level, RoadData rd) {
        RoadFileStorage.addRoad(level, rd);
    }

    public static List<RoadData> queryRect(ServerLevel level,
                                           int minBlockX, int minBlockZ,
                                           int maxBlockX, int maxBlockZ) {
        return RoadFileStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
    }

    public static CompletableFuture<List<RoadData>> queryRectAsync(ServerLevel level,
                                                                   int minBlockX, int minBlockZ,
                                                                   int maxBlockX, int maxBlockZ) {
        return CompletableFuture.supplyAsync(() -> queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ));
    }

    public static long computeFingerprint(RoadData rd) {
        return fingerprint(rd);
    }

    public static void deleteRoad(ServerLevel level, long fp) {
        RoadFileStorage.deleteRoad(level, fp);
    }

    public static void replaceRoad(ServerLevel level, long oldFp, RoadData newRd) {
        if (newRd == null || newRd.roadSegmentList() == null || newRd.roadSegmentList().isEmpty()) return;
        RoadFileStorage.replaceRoad(level, oldFp, newRd);
    }

    public static void flushAll(ServerLevel level) {
        // 文件型存储写入即落盘。
    }

    public static void clearAll(ServerLevel level) {
        RoadFileStorage.clearAll(level);
    }

    public static void shutdown() {
        // 文件型存储无后台写执行器。
    }

    private static long fingerprint(RoadData rd) {
        if (rd == null || rd.roadSegmentList() == null || rd.roadSegmentList().isEmpty()) return 0L;
        BlockPos a = firstPos(rd);
        BlockPos b = lastPos(rd);
        if (a == null || b == null) return 0L;
        long ka = (((long) a.getX()) << 32) ^ (a.getZ() & 0xffffffffL);
        long kb = (((long) b.getX()) << 32) ^ (b.getZ() & 0xffffffffL);
        long lo = Math.min(ka, kb), hi = Math.max(ka, kb);
        long f = (hi << 1) ^ lo;
        f ^= ((long) rd.width() & 0xffffffffL);
        f ^= ((long) rd.roadType() & 0xffffffffL) << 33;
        return f;
    }

    private static BlockPos firstPos(RoadData rd) {
        for (var segment : rd.roadSegmentList()) {
            if (segment != null && segment.middlePos() != null) return segment.middlePos();
        }
        return null;
    }

    private static BlockPos lastPos(RoadData rd) {
        List<?> segments = rd.roadSegmentList();
        for (int i = segments.size() - 1; i >= 0; i--) {
            Object segment = segments.get(i);
            if (segment instanceof net.shiroha233.roadweaver.core.model.RoadSegmentPlacement placement && placement.middlePos() != null) {
                return placement.middlePos();
            }
        }
        return null;
    }
}
