package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.planning.PlanningUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规划区域粗采样结果到道路连接的短生命周期索引。
 */
public final class CoarseTerrainRegionRegistry {
    private CoarseTerrainRegionRegistry() {}

    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, CoarseTerrainRegion>> BY_EDGE = new ConcurrentHashMap<>();

    public static void register(ServerLevel level, List<StructureConnection> connections, CoarseTerrainRegion region) {
        if (level == null || connections == null || connections.isEmpty() || region == null) return;
        ConcurrentHashMap<Long, CoarseTerrainRegion> perLevel = BY_EDGE.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>());
        for (StructureConnection connection : connections) {
            if (connection == null) continue;
            if (!region.contains(connection.from().getX(), connection.from().getZ())) continue;
            if (!region.contains(connection.to().getX(), connection.to().getZ())) continue;
            perLevel.put(PlanningUtils.edgeKey(connection.from(), connection.to()), region);
        }
    }

    public static CoarseTerrainRegion acquire(ServerLevel level, StructureConnection connection) {
        if (level == null || connection == null) return null;
        ConcurrentHashMap<Long, CoarseTerrainRegion> perLevel = BY_EDGE.get(level);
        if (perLevel == null) return null;
        return perLevel.get(PlanningUtils.edgeKey(connection.from(), connection.to()));
    }

    public static void release(ServerLevel level, StructureConnection connection) {
        if (level == null || connection == null) return;
        ConcurrentHashMap<Long, CoarseTerrainRegion> perLevel = BY_EDGE.get(level);
        if (perLevel == null) return;
        perLevel.remove(PlanningUtils.edgeKey(connection.from(), connection.to()));
        if (perLevel.isEmpty()) {
            BY_EDGE.remove(level, perLevel);
        }
    }

    public static void clear(ServerLevel level) {
        if (level != null) {
            BY_EDGE.remove(level);
        }
    }

    public static void clearAll() {
        BY_EDGE.clear();
    }
}