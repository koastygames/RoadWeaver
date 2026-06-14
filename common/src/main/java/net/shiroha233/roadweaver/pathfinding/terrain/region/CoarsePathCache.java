package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.planning.PlanningUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 粗路径搜索结果缓存。
 * 规划阶段完成粗采样后，对该区域内所有连接执行粗路径搜索，将结果存入此缓存，
 * 然后即可释放粗采样数据（CoarseTerrainRegion）。
 * 生成阶段直接从缓存取粗路径，跳过粗路径搜索，直接进入精采样。
 */
public final class CoarsePathCache {
    private CoarsePathCache() {}

    private static ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, List<BlockPos>>> BY_EDGE = new ConcurrentHashMap<>();

    public static void put(ServerLevel level, StructureConnection connection, List<BlockPos> coarsePath) {
        if (level == null || connection == null || coarsePath == null || coarsePath.isEmpty()) return;
        ConcurrentHashMap<Long, List<BlockPos>> perLevel = BY_EDGE.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>());
        perLevel.put(PlanningUtils.edgeKey(connection.from(), connection.to()), List.copyOf(coarsePath));
    }

    public static List<BlockPos> get(ServerLevel level, StructureConnection connection) {
        if (level == null || connection == null) return null;
        ConcurrentHashMap<Long, List<BlockPos>> perLevel = BY_EDGE.get(level);
        if (perLevel == null) return null;
        return perLevel.get(PlanningUtils.edgeKey(connection.from(), connection.to()));
    }

    public static void remove(ServerLevel level, StructureConnection connection) {
        if (level == null || connection == null) return;
        ConcurrentHashMap<Long, List<BlockPos>> perLevel = BY_EDGE.get(level);
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
        BY_EDGE = new ConcurrentHashMap<>();
    }
}
