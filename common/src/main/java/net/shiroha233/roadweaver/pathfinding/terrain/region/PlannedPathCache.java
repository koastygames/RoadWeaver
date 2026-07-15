/* 文件职责：按道路租约共享规划阶段的精确路径与区域地形。 */
package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.planning.PlanningUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 规划路径及其共享精确区域的进程内缓存。
 */
public final class PlannedPathCache {
    private static final ConcurrentHashMap<Long, Entry> BY_EDGE = new ConcurrentHashMap<>();

    private PlannedPathCache() {}

    public static void register(ServerLevel level,
                                AccurateTerrainRegion region,
                                Map<StructureConnection, List<BlockPos>> paths) {
        if (!isOverworld(level) || region == null) {
            if (region != null) {
                region.dispose();
            }
            return;
        }
        LinkedHashMap<Long, List<BlockPos>> byKey = new LinkedHashMap<>();
        if (paths != null) {
            for (Map.Entry<StructureConnection, List<BlockPos>> entry : paths.entrySet()) {
                StructureConnection connection = entry.getKey();
                List<BlockPos> path = entry.getValue();
                if (connection != null && path != null && !path.isEmpty()) {
                    byKey.put(PlanningUtils.edgeKey(connection.from(), connection.to()), List.copyOf(path));
                }
            }
        }
        register(region, byKey);
    }

    public static Lease take(ServerLevel level, StructureConnection connection) {
        if (!isOverworld(level) || connection == null) {
            return null;
        }
        return take(PlanningUtils.edgeKey(connection.from(), connection.to()));
    }

    public static void discard(ServerLevel level, StructureConnection connection) {
        if (!isOverworld(level) || connection == null) {
            return;
        }
        discard(PlanningUtils.edgeKey(connection.from(), connection.to()));
    }

    public static void clearAll() {
        for (Map.Entry<Long, Entry> entry : BY_EDGE.entrySet()) {
            if (BY_EDGE.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().region().release();
            }
        }
    }

    static void register(AccurateTerrainRegion region, Map<Long, List<BlockPos>> paths) {
        if (region == null) {
            return;
        }
        if (paths == null || paths.isEmpty()) {
            region.dispose();
            return;
        }
        SharedRegion shared = new SharedRegion(region, paths.size());
        for (Map.Entry<Long, List<BlockPos>> path : paths.entrySet()) {
            Entry previous = BY_EDGE.put(path.getKey(), new Entry(List.copyOf(path.getValue()), shared));
            if (previous != null) {
                previous.region().release();
            }
        }
    }

    static Lease take(long edgeKey) {
        Entry entry = BY_EDGE.remove(edgeKey);
        return entry == null ? null : new Lease(entry.path(), entry.region());
    }

    static void discard(long edgeKey) {
        Lease lease = take(edgeKey);
        if (lease != null) {
            lease.close();
        }
    }

    private static boolean isOverworld(ServerLevel level) {
        return level != null && Level.OVERWORLD.equals(level.dimension());
    }

    private record Entry(List<BlockPos> path, SharedRegion region) {}

    private static final class SharedRegion {
        private final AccurateTerrainRegion terrain;
        private final AtomicInteger references;

        private SharedRegion(AccurateTerrainRegion terrain, int references) {
            this.terrain = terrain;
            this.references = new AtomicInteger(references);
        }

        private void release() {
            int remaining = references.decrementAndGet();
            if (remaining == 0) {
                terrain.dispose();
            } else if (remaining < 0) {
                throw new IllegalStateException("planned terrain region released too many times");
            }
        }
    }

    public static final class Lease implements AutoCloseable {
        private final List<BlockPos> path;
        private final SharedRegion region;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(List<BlockPos> path, SharedRegion region) {
            this.path = path;
            this.region = region;
        }

        public List<BlockPos> path() {
            return path;
        }

        public AccurateTerrainRegion terrain() {
            return region.terrain;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                region.release();
            }
        }
    }
}
