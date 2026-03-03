package net.shiroha233.roadweaver.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.highway.config.HighwayGenerationConfig;
import net.shiroha233.roadweaver.features.highway.generation.HighwayRoad;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.pathlogic.core.Road;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.HighwayCellPathPlanningService;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.postprocess.RoadSnapService;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 道路生成调度器，管理 Path/Highway 双队列的 tick 驱动异步生成
 */
public final class RoadGenerationService {
    private RoadGenerationService() {}

    private static final ConcurrentHashMap<ServerLevel, ConcurrentLinkedQueue<StructureConnection>> QUEUES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, Boolean>> PROCESSED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, ConcurrentLinkedQueue<StructureConnection>> HIGHWAY_QUEUES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, Boolean>> HIGHWAY_PROCESSED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, AtomicInteger> RUNNING_COUNT = new ConcurrentHashMap<>();
    private static final Set<Future<?>> ALL_RUNNING = ConcurrentHashMap.newKeySet();

    private static final ResourceLocation ROAD_CF_ID = new ResourceLocation("roadweaver", "road_feature");

    public static void onServerStarted() {
        ALL_RUNNING.clear();
        QUEUES.clear();
        PROCESSED.clear();
        HIGHWAY_QUEUES.clear();
        HIGHWAY_PROCESSED.clear();
        RUNNING_COUNT.clear();
    }

    public static void onServerStopping() {
        ALL_RUNNING.forEach(f -> f.cancel(true));
        ALL_RUNNING.clear();
        QUEUES.clear();
        PROCESSED.clear();
        HIGHWAY_QUEUES.clear();
        HIGHWAY_PROCESSED.clear();
        RUNNING_COUNT.clear();
        RoadPlanningService.resetAll();
        HighwayCellPathPlanningService.resetAll();
        net.shiroha233.roadweaver.features.longdrive.planning.LongDrivePlanningService.resetAll();
        IdleRoadGenerationService.onServerStopping();
    }

    // ==================== tick 调度 ====================

    public static void tick(ServerLevel level) {
        refreshQueue(level);
        refreshHighwayQueue(level);
        ALL_RUNNING.removeIf(f -> f == null || f.isDone() || f.isCancelled());

        ConcurrentLinkedQueue<StructureConnection> q = QUEUES.computeIfAbsent(level, l -> new ConcurrentLinkedQueue<>());
        ConcurrentLinkedQueue<StructureConnection> hq = HIGHWAY_QUEUES.computeIfAbsent(level, l -> new ConcurrentLinkedQueue<>());
        if (q.isEmpty() && hq.isEmpty()) return;

        int limit = Math.max(1, ConfigService.get().performance().maxConcurrentGenerations());
        AtomicInteger cnt = RUNNING_COUNT.computeIfAbsent(level, l -> new AtomicInteger(0));

        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p != null && p.serverLevel() == level) players.add(p);
        }

        boolean pickHighway = false;
        while (cnt.get() < limit) {
            if (q.isEmpty() && hq.isEmpty()) break;

            boolean isHighway = !hq.isEmpty() && (q.isEmpty() || pickHighway);
            pickHighway = !pickHighway;

            StructureConnection conn = isHighway ? pollNearest(hq, players) : pollNearest(q, players);
            if (conn == null) continue;

            if (isHighway) {
                updateHighwayStatus(level, conn, ConnectionStatus.GENERATING);
            } else {
                updateConnectionStatus(level, conn, ConnectionStatus.GENERATING);
            }

            final StructureConnection task = conn;
            final boolean highwayTask = isHighway;
            cnt.incrementAndGet();
            long epoch = ThreadPoolManager.currentEpoch();

            Future<?> fut = ThreadPoolManager.generationExecutor().submit(() -> {
                try {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (!ThreadPoolManager.isEpoch(epoch)) return;
                    if (highwayTask) {
                        safeGenerateHighway(level, task, epoch);
                    } else {
                        safeGenerate(level, task, epoch);
                    }
                } finally {
                    cnt.decrementAndGet();
                }
            });
            ALL_RUNNING.add(fut);
        }
    }

    // ==================== 纯生成逻辑（无副作用） ====================

    public static boolean generateTask(ServerLevel level, StructureConnection conn) {
        if (level == null || conn == null) return false;
        try {
            if (Thread.currentThread().isInterrupted()) return false;

            var reg = level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE);
            ConfiguredFeature<?, ?> cf = reg.get(ROAD_CF_ID);
            PathFeatureConfig cfg = (cf != null && cf.config() instanceof PathFeatureConfig rfc) ? rfc : new PathFeatureConfig();

            if (Thread.currentThread().isInterrupted()) return false;
            ModConfig modCfg = ConfigService.get();
            String dimId = level.dimension().location().toString();
            if (!modCfg.roadsEnabledForDimension(dimId)) return true;

            RoadGenerationConfig genCfg = RoadGenerationConfig.from(modCfg);
            new Road(level, conn, cfg, genCfg).generateRoad(modCfg.pathfindingCost().aStarMaxSteps());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean generateHighwayTask(ServerLevel level, StructureConnection conn) {
        if (level == null || conn == null) return false;
        try {
            if (Thread.currentThread().isInterrupted()) return false;
            ModConfig cfg = ConfigService.get();
            String dimId = level.dimension().location().toString();
            if (!cfg.highwayEnabledForDimension(dimId)) return true;

            HighwayGenerationConfig genCfg = HighwayGenerationConfig.from(cfg);
            return new HighwayRoad(level, conn, genCfg).generateRoad(cfg.highway().aStarMaxSteps());
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== 安全生成包装 ====================

    private static void safeGenerate(ServerLevel level, StructureConnection conn, long epoch) {
        try {
            if (Thread.currentThread().isInterrupted()) return;
            if (!ThreadPoolManager.isEpoch(epoch)) return;

            boolean ok = generateTask(level, conn);
            ConnectionStatus st = ok ? ConnectionStatus.COMPLETED : ConnectionStatus.FAILED;

            executeOnMainThread(level, epoch, () -> {
                updateConnectionStatus(level, conn, st);
                removeProcessed(level, conn);
                if (st == ConnectionStatus.COMPLETED) {
                    RoadSnapService.snapAroundConnection(level, conn.from(), conn.to());
                }
            });
        } catch (Throwable t) {
            executeOnMainThread(level, epoch, () -> {
                updateConnectionStatus(level, conn, ConnectionStatus.FAILED);
                removeProcessed(level, conn);
            });
        }
    }

    private static void safeGenerateHighway(ServerLevel level, StructureConnection conn, long epoch) {
        ConnectionStatus st;
        try {
            if (Thread.currentThread().isInterrupted()) return;
            if (!ThreadPoolManager.isEpoch(epoch)) return;

            boolean ok = generateHighwayTask(level, conn);
            st = ok ? ConnectionStatus.COMPLETED : ConnectionStatus.FAILED;
        } catch (Throwable t) {
            st = ConnectionStatus.FAILED;
        }

        final ConnectionStatus finalSt = st;
        StructureConnection finalized = new StructureConnection(conn.from(), conn.to(), finalSt);

        executeOnMainThread(level, epoch, () -> {
            updateHighwayStatus(level, conn, finalSt);
            HighwayCellPathPlanningService.onHighwayEdgeFinalized(level, finalized);
            removeHighwayProcessed(level, conn);
        });
    }

    private static void executeOnMainThread(ServerLevel level, long epoch, Runnable action) {
        var server = level.getServer();
        if (server != null) {
            server.execute(() -> {
                if (!ThreadPoolManager.isEpoch(epoch)) return;
                action.run();
            });
        } else {
            if (!ThreadPoolManager.isEpoch(epoch)) return;
            action.run();
        }
    }

    // ==================== 状态更新辅助 ====================

    private static void updateConnectionStatus(ServerLevel level, StructureConnection conn, ConnectionStatus status) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> origin = provider.getStructureConnections(level);
        List<StructureConnection> all = origin != null ? new ArrayList<>(origin) : new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            StructureConnection c = all.get(i);
            if (PlanningUtils.sameEdge(c, conn)) {
                all.set(i, new StructureConnection(c.from(), c.to(), status));
                break;
            }
        }
        provider.setStructureConnections(level, all);
    }

    private static void updateHighwayStatus(ServerLevel level, StructureConnection conn, ConnectionStatus status) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> origin = provider.getHighwayConnections(level);
        List<StructureConnection> all = origin != null ? new ArrayList<>(origin) : new ArrayList<>();
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            StructureConnection c = all.get(i);
            if (PlanningUtils.sameEdge(c, conn)) {
                all.set(i, new StructureConnection(c.from(), c.to(), status));
                found = true;
                break;
            }
        }
        if (!found) {
            all.add(new StructureConnection(conn.from(), conn.to(), status));
        }
        provider.setHighwayConnections(level, all);
    }

    private static void removeProcessed(ServerLevel level, StructureConnection conn) {
        long k = PlanningUtils.edgeKey(conn.from(), conn.to());
        ConcurrentHashMap<Long, Boolean> proc = PROCESSED.get(level);
        if (proc != null) proc.remove(k);
    }

    private static void removeHighwayProcessed(ServerLevel level, StructureConnection conn) {
        long k = PlanningUtils.edgeKey(conn.from(), conn.to());
        ConcurrentHashMap<Long, Boolean> proc = HIGHWAY_PROCESSED.get(level);
        if (proc != null) proc.remove(k);
    }

    // ==================== 队列刷新 ====================

    private static void refreshQueue(ServerLevel level) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> list = provider.getStructureConnections(level);
        if (list == null) return;

        ConcurrentLinkedQueue<StructureConnection> q = QUEUES.computeIfAbsent(level, l -> new ConcurrentLinkedQueue<>());
        ConcurrentHashMap<Long, Boolean> proc = PROCESSED.computeIfAbsent(level, l -> new ConcurrentHashMap<>());

        for (StructureConnection c : list) {
            if (c.status() != ConnectionStatus.PLANNED && c.status() != ConnectionStatus.GENERATING) continue;
            if (c.status() == ConnectionStatus.PLANNED
                    && IdleRoadGenerationService.shouldReserveForIdle(level, c)) {
                continue;
            }
            if (c.status() == ConnectionStatus.GENERATING
                    && IdleRoadGenerationService.isManagedByIdle(level, c)) {
                continue;
            }
            long key = PlanningUtils.edgeKey(c.from(), c.to());
            if (proc.putIfAbsent(key, Boolean.TRUE) == null) {
                q.add(c);
            }
        }
    }

    private static void refreshHighwayQueue(ServerLevel level) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> list = provider.getHighwayConnections(level);
        if (list == null) return;

        ConcurrentLinkedQueue<StructureConnection> q = HIGHWAY_QUEUES.computeIfAbsent(level, l -> new ConcurrentLinkedQueue<>());
        ConcurrentHashMap<Long, Boolean> proc = HIGHWAY_PROCESSED.computeIfAbsent(level, l -> new ConcurrentHashMap<>());

        for (StructureConnection c : list) {
            if (c.status() != ConnectionStatus.PLANNED && c.status() != ConnectionStatus.GENERATING) continue;
            long key = PlanningUtils.edgeKey(c.from(), c.to());
            if (proc.putIfAbsent(key, Boolean.TRUE) == null) {
                q.add(c);
            }
        }
    }

    // ==================== 优先级轮询 ====================

    private static StructureConnection pollNearest(ConcurrentLinkedQueue<StructureConnection> q, List<ServerPlayer> players) {
        if (q.isEmpty()) return null;
        if (players == null || players.isEmpty()) return q.poll();

        StructureConnection best = null;
        long bestDist = Long.MAX_VALUE;
        for (StructureConnection e : q) {
            long d = playerDistance2(e, players);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        if (best != null && q.remove(best)) return best;
        return q.poll();
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
}
