package net.shiroha233.roadweaver.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.postprocess.RoadSnapService;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 闲时道路生成服务：
 * 1) 围绕玩家持续扩张规划半径；
 * 2) 将该半径内的 PLANNED 任务交给闲时线程专属生成；
 * 3) 玩家离开该半径后，任务自动释放给常规生成线程。
 */
public final class IdleRoadGenerationService {
    private IdleRoadGenerationService() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final int MAX_IDLE_RADIUS_CHUNKS = 16_384;
    private static final int IDLE_RADIUS_EXPAND_STEP_CHUNKS = 32;
    private static final int IDLE_CENTER_FOLLOW_STEP_CHUNKS = 8;

    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<UUID, IdleWindow>> WINDOWS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, Boolean>> IDLE_OWNED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, Boolean>> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, AtomicInteger> RUNNING = new ConcurrentHashMap<>();
    private static final Set<Future<?>> ALL_RUNNING = ConcurrentHashMap.newKeySet();

    private static final AtomicReference<ExecutorService> IDLE_EXEC = new AtomicReference<>();
    private static final AtomicInteger IDLE_EXEC_THREADS = new AtomicInteger(-1);

    private static final class IdleWindow {
        volatile int centerChunkX;
        volatile int centerChunkZ;
        volatile int radiusChunks;
        final AtomicBoolean planning = new AtomicBoolean(false);

        IdleWindow(int centerChunkX, int centerChunkZ, int radiusChunks) {
            this.centerChunkX = centerChunkX;
            this.centerChunkZ = centerChunkZ;
            this.radiusChunks = radiusChunks;
        }
    }

    public static void onServerStarted() {
        WINDOWS.clear();
        IDLE_OWNED.clear();
        IN_FLIGHT.clear();
        RUNNING.clear();
        ALL_RUNNING.clear();
        ensureExecutor(Math.max(0, ConfigService.get().performance().idleGenerationThreads()));
    }

    public static void onServerStopping() {
        ALL_RUNNING.forEach(f -> {
            try {
                if (f != null) f.cancel(true);
            } catch (Throwable ignored) {}
        });
        ALL_RUNNING.clear();
        WINDOWS.clear();
        IDLE_OWNED.clear();
        IN_FLIGHT.clear();
        RUNNING.clear();
        shutdownExecutor();
    }

    public static void tickPlayer(ServerPlayer player) {
        if (player == null) return;

        ServerLevel level = player.serverLevel();
        ModConfig cfg = ConfigService.get();
        String dimId = level.dimension().location().toString();
        if (!cfg.roadsEnabledForDimension(dimId)) return;

        int threads = Math.max(0, cfg.performance().idleGenerationThreads());
        if (threads <= 0) return;

        ensureExecutor(threads);

        int baseRadius = resolveBaseRadiusChunks(cfg);
        int expandStep = resolveExpandStepChunks(cfg);
        int pcx = player.chunkPosition().x;
        int pcz = player.chunkPosition().z;

        ConcurrentHashMap<UUID, IdleWindow> perLevel = WINDOWS.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        UUID playerId = player.getUUID();
        IdleWindow win = perLevel.get(playerId);
        if (win == null) {
            IdleWindow created = new IdleWindow(pcx, pcz, baseRadius);
            IdleWindow prev = perLevel.putIfAbsent(playerId, created);
            win = prev != null ? prev : created;
            if (prev == null) {
                trySchedulePlan(level, win, baseRadius);
                return;
            }
        }

        int dist = chebyshev(pcx, pcz, win.centerChunkX, win.centerChunkZ);
        if (dist > win.radiusChunks || dist >= IDLE_CENTER_FOLLOW_STEP_CHUNKS) {
            int oldCenterX = win.centerChunkX;
            int oldCenterZ = win.centerChunkZ;
            int oldRadius = win.radiusChunks;
            int targetRadius = dist > oldRadius ? baseRadius : oldRadius;

            win.centerChunkX = pcx;
            win.centerChunkZ = pcz;
            if (trySchedulePlan(level, win, targetRadius)) {
                win.radiusChunks = targetRadius;
                return;
            }

            win.centerChunkX = oldCenterX;
            win.centerChunkZ = oldCenterZ;
        }

        if (!isWindowFinished(level, win)) {
            return;
        }

        int oldRadius = win.radiusChunks;
        int nextRadius = Math.min(MAX_IDLE_RADIUS_CHUNKS, oldRadius + expandStep);
        if (nextRadius <= oldRadius) return;
        if (trySchedulePlan(level, win, nextRadius)) {
            win.radiusChunks = nextRadius;
        }
    }

    public static void tick(ServerLevel level) {
        if (level == null) return;

        ModConfig cfg = ConfigService.get();
        int threads = Math.max(0, cfg.performance().idleGenerationThreads());
        if (threads <= 0) {
            ensureExecutor(0);
            clearLevel(level);
            return;
        }

        ensureExecutor(threads);
        cleanupWindows(level);

        ALL_RUNNING.removeIf(f -> f == null || f.isDone() || f.isCancelled());
        reservePlannedEdges(level);

        AtomicInteger running = RUNNING.computeIfAbsent(level, l -> new AtomicInteger(0));
        List<ServerPlayer> players = collectPlayers(level);
        int duty = cfg.performance().idleThreadDutyCycle();

        while (running.get() < threads) {
            StructureConnection conn = pollNearestOwnedPlanned(level, players);
            if (conn == null) break;

            long key = PlanningUtils.edgeKey(conn.from(), conn.to());
            ConcurrentHashMap<Long, Boolean> inFlight = IN_FLIGHT.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
            if (inFlight.putIfAbsent(key, Boolean.TRUE) != null) continue;
            if (!updateStatus(level, conn, ConnectionStatus.GENERATING, ConnectionStatus.PLANNED)) {
                inFlight.remove(key);
                continue;
            }

            running.incrementAndGet();
            long epoch = ThreadPoolManager.currentEpoch();
            final StructureConnection task = conn;
            Future<?> future = executor().submit(() -> runIdleTask(level, task, key, duty, epoch));
            ALL_RUNNING.add(future);
        }
    }

    public static boolean shouldReserveForIdle(ServerLevel level, StructureConnection conn) {
        if (level == null || conn == null) return false;
        if (ConfigService.get().performance().idleGenerationThreads() <= 0) return false;

        ConcurrentHashMap<Long, Boolean> owned = IDLE_OWNED.get(level);
        if (owned == null || owned.isEmpty()) return false;

        long key = PlanningUtils.edgeKey(conn.from(), conn.to());
        if (!owned.containsKey(key)) return false;
        if (isInsideAnyWindow(level, conn)) return true;

        owned.remove(key);
        return false;
    }

    public static boolean isManagedByIdle(ServerLevel level, StructureConnection conn) {
        if (level == null || conn == null) return false;
        if (ConfigService.get().performance().idleGenerationThreads() <= 0) return false;
        long key = PlanningUtils.edgeKey(conn.from(), conn.to());
        ConcurrentHashMap<Long, Boolean> inFlight = IN_FLIGHT.get(level);
        if (inFlight != null && inFlight.containsKey(key)) return true;
        ConcurrentHashMap<Long, Boolean> owned = IDLE_OWNED.get(level);
        return owned != null && owned.containsKey(key);
    }

    private static void runIdleTask(ServerLevel level, StructureConnection task, long key, int duty, long epoch) {
        AtomicInteger running = RUNNING.computeIfAbsent(level, l -> new AtomicInteger(0));
        ThreadPoolManager.resetThrottle();
        try {
            if (Thread.currentThread().isInterrupted()) return;
            if (!ThreadPoolManager.isEpoch(epoch)) return;

            boolean ok = RoadGenerationService.generateTask(level, task);
            ConnectionStatus st = ok ? ConnectionStatus.COMPLETED : ConnectionStatus.FAILED;
            executeOnMain(level, epoch, () -> {
                updateStatus(level, task, st, ConnectionStatus.GENERATING, ConnectionStatus.PLANNED);
                removeOwnership(level, key);
                if (st == ConnectionStatus.COMPLETED) {
                    RoadSnapService.snapAroundConnectionAsync(level, task.from(), task.to());
                }
            });
        } catch (Throwable t) {
            LOGGER.warn("IdleRoad: generate failed {} -> {}", task.from(), task.to(), t);
            executeOnMain(level, epoch, () -> {
                updateStatus(level, task, ConnectionStatus.FAILED, ConnectionStatus.GENERATING, ConnectionStatus.PLANNED);
                removeOwnership(level, key);
            });
        } finally {
            try {
                ThreadPoolManager.throttle(duty);
            } catch (Throwable ignored) {}
            ThreadPoolManager.clearThrottle();
            running.decrementAndGet();
        }
    }

    private static void removeOwnership(ServerLevel level, long key) {
        ConcurrentHashMap<Long, Boolean> owned = IDLE_OWNED.get(level);
        if (owned != null) owned.remove(key);
        ConcurrentHashMap<Long, Boolean> inFlight = IN_FLIGHT.get(level);
        if (inFlight != null) inFlight.remove(key);
    }

    private static void reservePlannedEdges(ServerLevel level) {
        List<StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
        if (conns == null || conns.isEmpty()) return;
        if (!hasWindows(level)) return;

        ConcurrentHashMap<Long, Boolean> owned = IDLE_OWNED.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        for (StructureConnection c : conns) {
            if (c == null || c.status() != ConnectionStatus.PLANNED) continue;
            if (!isInsideAnyWindow(level, c)) continue;
            owned.putIfAbsent(PlanningUtils.edgeKey(c.from(), c.to()), Boolean.TRUE);
        }
    }

    private static StructureConnection pollNearestOwnedPlanned(ServerLevel level, List<ServerPlayer> players) {
        List<StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
        if (conns == null || conns.isEmpty()) return null;

        ConcurrentHashMap<Long, Boolean> owned = IDLE_OWNED.get(level);
        if (owned == null || owned.isEmpty()) return null;
        ConcurrentHashMap<Long, Boolean> inFlight = IN_FLIGHT.computeIfAbsent(level, l -> new ConcurrentHashMap<>());

        ConcurrentHashMap<UUID, IdleWindow> perLevel = WINDOWS.get(level);
        if (perLevel == null || perLevel.isEmpty()) return null;

        StructureConnection best = null;
        long bestDist = Long.MAX_VALUE;

        for (Map.Entry<UUID, IdleWindow> windowEntry : perLevel.entrySet()) {
            IdleWindow w = windowEntry.getValue();
            if (w == null) continue;
            int wcCx = w.centerChunkX;
            int wcCz = w.centerChunkZ;
            int wRadius = w.radiusChunks;

            for (StructureConnection c : conns) {
                if (c == null || c.status() != ConnectionStatus.PLANNED) continue;
                long key = PlanningUtils.edgeKey(c.from(), c.to());
                if (!owned.containsKey(key)) continue;
                int mx = (c.from().getX() + c.to().getX()) >> 1;
                int mz = (c.from().getZ() + c.to().getZ()) >> 1;
                int mcx = mx >> 4;
                int mcz = mz >> 4;
                if (Math.abs(mcx - wcCx) > wRadius || Math.abs(mcz - wcCz) > wRadius) {
                    owned.remove(key);
                    continue;
                }
                if (inFlight.containsKey(key)) continue;
                long d = playerDistance2(c, players);
                if (d < bestDist) {
                    bestDist = d;
                    best = c;
                }
            }
        }
        return best;
    }

    private static boolean updateStatus(ServerLevel level, StructureConnection conn, ConnectionStatus to, ConnectionStatus... allowedFrom) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> origin = provider.getStructureConnections(level);
        if (origin == null || origin.isEmpty()) return false;

        Set<ConnectionStatus> allowed = new HashSet<>();
        if (allowedFrom != null) {
            for (ConnectionStatus st : allowedFrom) {
                if (st != null) allowed.add(st);
            }
        }

        List<StructureConnection> all = new ArrayList<>(origin);
        for (int i = 0; i < all.size(); i++) {
            StructureConnection c = all.get(i);
            if (!PlanningUtils.sameEdge(c, conn)) continue;
            if (!allowed.isEmpty() && !allowed.contains(c.status())) return false;
            all.set(i, new StructureConnection(c.from(), c.to(), to));
            provider.setStructureConnections(level, all);
            return true;
        }
        return false;
    }

    private static void cleanupWindows(ServerLevel level) {
        ConcurrentHashMap<UUID, IdleWindow> perLevel = WINDOWS.get(level);
        if (perLevel == null || perLevel.isEmpty()) return;

        Set<UUID> alive = new HashSet<>();
        for (ServerPlayer p : collectPlayers(level)) {
            alive.add(p.getUUID());
        }
        perLevel.keySet().removeIf(id -> !alive.contains(id));

        if (perLevel.isEmpty()) {
            WINDOWS.remove(level);
            IDLE_OWNED.remove(level);
            IN_FLIGHT.remove(level);
            RUNNING.remove(level);
        }
    }

    private static void clearLevel(ServerLevel level) {
        WINDOWS.remove(level);
        IDLE_OWNED.remove(level);
        IN_FLIGHT.remove(level);
        RUNNING.remove(level);
    }

    private static boolean hasWindows(ServerLevel level) {
        ConcurrentHashMap<UUID, IdleWindow> perLevel = WINDOWS.get(level);
        return perLevel != null && !perLevel.isEmpty();
    }

    private static boolean isInsideAnyWindow(ServerLevel level, StructureConnection conn) {
        ConcurrentHashMap<UUID, IdleWindow> perLevel = WINDOWS.get(level);
        if (perLevel == null || perLevel.isEmpty() || conn == null) return false;

        int mx = (conn.from().getX() + conn.to().getX()) >> 1;
        int mz = (conn.from().getZ() + conn.to().getZ()) >> 1;
        int mcx = mx >> 4;
        int mcz = mz >> 4;

        for (IdleWindow w : perLevel.values()) {
            if (w == null) continue;
            if (Math.abs(mcx - w.centerChunkX) <= w.radiusChunks
                    && Math.abs(mcz - w.centerChunkZ) <= w.radiusChunks) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInsideWindow(IdleWindow window, StructureConnection conn) {
        if (window == null || conn == null) return false;
        int mx = (conn.from().getX() + conn.to().getX()) >> 1;
        int mz = (conn.from().getZ() + conn.to().getZ()) >> 1;
        int mcx = mx >> 4;
        int mcz = mz >> 4;
        return Math.abs(mcx - window.centerChunkX) <= window.radiusChunks
                && Math.abs(mcz - window.centerChunkZ) <= window.radiusChunks;
    }

    private static boolean isWindowFinished(ServerLevel level, IdleWindow window) {
        if (level == null || window == null) return false;
        if (window.planning.get()) return false;

        List<StructureConnection> conns = WorldDataProvider.getInstance().getStructureConnections(level);
        if (conns == null || conns.isEmpty()) return true;

        for (StructureConnection c : conns) {
            if (c == null) continue;
            if (!isInsideWindow(window, c)) continue;
            if (c.status() == ConnectionStatus.PLANNED || c.status() == ConnectionStatus.GENERATING) {
                return false;
            }
        }
        return true;
    }

    private static boolean trySchedulePlan(ServerLevel level, IdleWindow window, int radiusChunks) {
        if (level == null || window == null) return false;
        if (!window.planning.compareAndSet(false, true)) return false;

        int cx = window.centerChunkX;
        int cz = window.centerChunkZ;
        int minX = (cx - radiusChunks) * 16;
        int maxX = (cx + radiusChunks) * 16;
        int minZ = (cz - radiusChunks) * 16;
        int maxZ = (cz + radiusChunks) * 16;

        CompletableFuture<Void> future = RoadPlanningService.planRectAsync(level, minX, minZ, maxX, maxZ);
        future.whenComplete((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.warn("IdleRoad: plan rect failed center=({},{}), radius={}", cx, cz, radiusChunks, throwable);
            }
            window.planning.set(false);
        });
        return true;
    }

    private static int resolveBaseRadiusChunks(ModConfig cfg) {
        if (cfg == null) return 1;
        if (cfg.planning().dynamicPlanEnabled()) {
            return Math.max(1, cfg.planning().dynamicPlanRadiusChunks());
        }
        return Math.max(1, cfg.planning().initialPlanRadiusChunks());
    }

    private static int resolveExpandStepChunks(ModConfig cfg) {
        return IDLE_RADIUS_EXPAND_STEP_CHUNKS;
    }

    private static int chebyshev(int x0, int z0, int x1, int z1) {
        return Math.max(Math.abs(x0 - x1), Math.abs(z0 - z1));
    }

    private static List<ServerPlayer> collectPlayers(ServerLevel level) {
        List<ServerPlayer> out = new ArrayList<>();
        var server = level.getServer();
        if (server == null) return out;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != null && p.serverLevel() == level) out.add(p);
        }
        return out;
    }

    private static long playerDistance2(StructureConnection c, List<ServerPlayer> players) {
        if (players == null || players.isEmpty()) return Long.MAX_VALUE;
        long best = Long.MAX_VALUE;
        int mx = (c.from().getX() + c.to().getX()) >> 1;
        int mz = (c.from().getZ() + c.to().getZ()) >> 1;
        BlockPos mid = new BlockPos(mx, 0, mz);
        for (ServerPlayer p : players) {
            BlockPos pb = p.blockPosition();
            best = Math.min(best, dist2XZ(pb, c.from()));
            best = Math.min(best, dist2XZ(pb, c.to()));
            best = Math.min(best, dist2XZ(pb, mid));
        }
        return best;
    }

    private static long dist2XZ(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static void executeOnMain(ServerLevel level, long epoch, Runnable action) {
        var server = level.getServer();
        if (server == null) {
            if (!ThreadPoolManager.isEpoch(epoch)) return;
            action.run();
            return;
        }
        server.execute(() -> {
            if (!ThreadPoolManager.isEpoch(epoch)) return;
            action.run();
        });
    }

    private static ExecutorService executor() {
        ExecutorService e = IDLE_EXEC.get();
        if (e != null && !e.isShutdown()) return e;
        synchronized (IdleRoadGenerationService.class) {
            e = IDLE_EXEC.get();
            if (e == null || e.isShutdown()) {
                int threads = Math.max(0, ConfigService.get().performance().idleGenerationThreads());
                ensureExecutor(threads);
                e = IDLE_EXEC.get();
            }
        }
        return e;
    }

    private static synchronized void ensureExecutor(int threads) {
        int resolved = Math.max(0, threads);
        ExecutorService old = IDLE_EXEC.get();
        if (resolved <= 0) {
            if (old != null && !old.isShutdown()) old.shutdownNow();
            IDLE_EXEC.set(null);
            IDLE_EXEC_THREADS.set(0);
            return;
        }
        if (old != null && !old.isShutdown() && IDLE_EXEC_THREADS.get() == resolved) return;
        if (old != null && !old.isShutdown()) old.shutdownNow();
        IDLE_EXEC.set(Executors.newFixedThreadPool(resolved, new NamedFactory("RW-Idle")));
        IDLE_EXEC_THREADS.set(resolved);
    }

    private static synchronized void shutdownExecutor() {
        ExecutorService exec = IDLE_EXEC.getAndSet(null);
        if (exec != null && !exec.isShutdown()) {
            try {
                exec.shutdownNow();
            } catch (Throwable ignored) {}
        }
        IDLE_EXEC_THREADS.set(-1);
    }

    private static final class NamedFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger next = new AtomicInteger(1);

        private NamedFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + next.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
