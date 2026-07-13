package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.planning.PlanningUtils;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 规划区域粗采样结果到道路连接的短生命周期索引。
 * 使用引用计数：同一 region 被多条连接共享，当所有连接 release 后自动 dispose region 释放内存。
 */
public final class CoarseTerrainRegionRegistry {
    private CoarseTerrainRegionRegistry() {}

    private static final ConcurrentHashMap<Long, RegionRef> BY_EDGE = new ConcurrentHashMap<>();

    /**
     * 将 region 注册给多条连接。每条连接持有一个引用，引用计数初始为连接数。
     */
    public static void register(ServerLevel level, List<StructureConnection> connections, CoarseTerrainRegion region) {
        if (!isOverworld(level) || connections == null || connections.isEmpty() || region == null) return;
        RegionRef ref = new RegionRef(region);
        int count = 0;
        for (StructureConnection connection : connections) {
            if (connection == null) continue;
            if (!region.contains(connection.from().getX(), connection.from().getZ())) continue;
            if (!region.contains(connection.to().getX(), connection.to().getZ())) continue;
            BY_EDGE.put(PlanningUtils.edgeKey(connection.from(), connection.to()), ref);
            count++;
        }
        // 设置引用计数为实际注册的连接数
        if (count > 0) {
            ref.refCount.set(count);
        }
    }

    public static CoarseTerrainRegion acquire(ServerLevel level, StructureConnection connection) {
        if (!isOverworld(level) || connection == null) return null;
        RegionRef ref = BY_EDGE.get(PlanningUtils.edgeKey(connection.from(), connection.to()));
        return ref != null ? ref.region : null;
    }

    /**
     * 释放一条连接对 region 的引用。当引用计数归零时自动 dispose region 释放内存。
     */
    public static void release(ServerLevel level, StructureConnection connection) {
        if (!isOverworld(level) || connection == null) return;
        RegionRef ref = BY_EDGE.remove(PlanningUtils.edgeKey(connection.from(), connection.to()));
        if (ref != null && ref.refCount.decrementAndGet() <= 0) {
            // 最后一个引用释放，dispose region 释放大数组
            ref.region.dispose();
        }
    }

    public static void clear(ServerLevel level) {
        if (isOverworld(level)) clearAll();
    }

    public static void clearAll() {
        disposeAllInMap(BY_EDGE);
        BY_EDGE.clear();
    }

    private static void disposeAllInMap(ConcurrentHashMap<Long, RegionRef> map) {
        if (map == null) return;
        for (RegionRef ref : new HashSet<>(map.values())) {
            ref.region.dispose();
        }
    }

    private static boolean isOverworld(ServerLevel level) {
        return level != null && Level.OVERWORLD.equals(level.dimension());
    }

    private static final class RegionRef {
        final CoarseTerrainRegion region;
        final AtomicInteger refCount = new AtomicInteger(1);

        RegionRef(CoarseTerrainRegion region) {
            this.region = region;
        }
    }
}
