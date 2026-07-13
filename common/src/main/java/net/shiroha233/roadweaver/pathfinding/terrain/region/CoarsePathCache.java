package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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

    private static final ConcurrentHashMap<Long, List<BlockPos>> BY_EDGE = new ConcurrentHashMap<>();

    public static void put(ServerLevel level, StructureConnection connection, List<BlockPos> coarsePath) {
        if (!isOverworld(level) || connection == null || coarsePath == null || coarsePath.isEmpty()) return;
        BY_EDGE.put(PlanningUtils.edgeKey(connection.from(), connection.to()), List.copyOf(coarsePath));
    }

    public static List<BlockPos> get(ServerLevel level, StructureConnection connection) {
        if (!isOverworld(level) || connection == null) return null;
        return BY_EDGE.get(PlanningUtils.edgeKey(connection.from(), connection.to()));
    }

    public static void remove(ServerLevel level, StructureConnection connection) {
        if (!isOverworld(level) || connection == null) return;
        BY_EDGE.remove(PlanningUtils.edgeKey(connection.from(), connection.to()));
    }

    public static void clear(ServerLevel level) {
        if (isOverworld(level)) BY_EDGE.clear();
    }

    public static void clearAll() {
        BY_EDGE.clear();
    }

    private static boolean isOverworld(ServerLevel level) {
        return level != null && Level.OVERWORLD.equals(level.dimension());
    }
}
