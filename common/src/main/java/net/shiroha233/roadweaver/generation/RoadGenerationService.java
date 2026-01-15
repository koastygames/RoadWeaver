package net.shiroha233.roadweaver.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.shiroha233.roadweaver.config.RoadGenerationConfig;
import net.shiroha233.roadweaver.features.highway.config.HighwayGenerationConfig;
import net.shiroha233.roadweaver.features.highway.generation.HighwayRoad;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.pathlogic.core.Road;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.planning.HighwayCellPathPlanningService;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.config.ConfigService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoadGenerationService {
    private RoadGenerationService() {
    }

    // 生命周期由中央管理器管理
    private static final ConcurrentHashMap<ServerLevel, ConcurrentLinkedQueue<Records.StructureConnection>> QUEUES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, Boolean>> PROCESSED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, ConcurrentLinkedQueue<Records.StructureConnection>> HIGHWAY_QUEUES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, Boolean>> HIGHWAY_PROCESSED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, AtomicInteger> RUNNING_COUNT = new ConcurrentHashMap<>();
    private static final Set<Future<?>> ALL_RUNNING = ConcurrentHashMap.newKeySet();

    private static final ResourceLocation ROAD_CF_ID = ResourceLocation.fromNamespaceAndPath("roadweaver", "road_feature");

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
    }

    /**
     * 执行单个生成任务（无副作用，不更新全局状态）。
     *
     * @return true if success, false if failed
     */
    public static boolean generateTask(ServerLevel level, Records.StructureConnection conn) {
        if (level == null || conn == null)
            return false;
        try {
            if (Thread.currentThread().isInterrupted())
                return false;

            // 配置
            var reg = level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE);
            ConfiguredFeature<?, ?> cf = reg.get(ROAD_CF_ID);
            PathFeatureConfig cfg = (cf != null && cf.config() instanceof PathFeatureConfig rfc) ? rfc
                    : defaultConfig();

            // 生成
            if (Thread.currentThread().isInterrupted())
                return false;
            var modCfg = ConfigService.get();
            String dimId = level.dimension().location().toString();
            // 按维度：道路系统关闭时跳过生成（视为成功，避免任务被标记为 FAILED）
            if (!modCfg.roadsEnabledForDimension(dimId)) {
                return true;
            }
            RoadGenerationConfig genCfg = RoadGenerationConfig.from(modCfg, dimId);
            new Road(level, conn, cfg, genCfg).generateRoad(modCfg.aStarMaxSteps());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void safeGenerateHighway(ServerLevel level, Records.StructureConnection conn, long epoch) {
        try {
            if (Thread.currentThread().isInterrupted())
                return;
            if (!net.shiroha233.roadweaver.runtime.ThreadPoolManager.isEpoch(epoch))
                return;

            boolean ok = generateHighwayTask(level, conn);
            Records.ConnectionStatus st = ok ? Records.ConnectionStatus.COMPLETED : Records.ConnectionStatus.FAILED;
            var server = level.getServer();
            if (server != null) {
                server.execute(() -> {
                    if (!net.shiroha233.roadweaver.runtime.ThreadPoolManager.isEpoch(epoch))
                        return;
                    updateHighwayStatus(level, conn, st);
                    // 重要：无论成功或失败，该边都算“已完成”，用于触发网格单元格（四边）完成检测。
                    HighwayCellPathPlanningService.onHighwayEdgeFinalized(level, new Records.StructureConnection(conn.from(), conn.to(), st));
                    removeHighwayProcessed(level, conn);
                });
            } else {
                updateHighwayStatus(level, conn, st);
                HighwayCellPathPlanningService.onHighwayEdgeFinalized(level, new Records.StructureConnection(conn.from(), conn.to(), st));
                removeHighwayProcessed(level, conn);
            }
        } catch (Throwable t) {
            updateHighwayStatus(level, conn, Records.ConnectionStatus.FAILED);
            HighwayCellPathPlanningService.onHighwayEdgeFinalized(level,
                    new Records.StructureConnection(conn.from(), conn.to(), Records.ConnectionStatus.FAILED));
            removeHighwayProcessed(level, conn);
        }
    }

    public static boolean generateHighwayTask(ServerLevel level, Records.StructureConnection conn) {
        if (level == null || conn == null)
            return false;
        try {
            if (Thread.currentThread().isInterrupted())
                return false;
            var cfg = ConfigService.get();
            String dimId = level.dimension().location().toString();
            // 按维度：公路系统关闭时跳过生成（视为成功，避免任务被标记为 FAILED）
            if (!cfg.highwayEnabledForDimension(dimId)) {
                return true;
            }
            HighwayGenerationConfig genCfg = HighwayGenerationConfig.from(cfg);
            return new HighwayRoad(level, conn, genCfg).generateRoad(cfg.highwayAStarMaxSteps());
        } catch (Throwable t) {
            return false;
        }
    }

    private static void updateHighwayStatus(ServerLevel level, Records.StructureConnection conn, Records.ConnectionStatus status) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> origin = provider.getHighwayConnections(level);
        List<Records.StructureConnection> all = origin != null ? new ArrayList<>(origin) : new ArrayList<>();
        boolean found = false;
        for (int i = 0; i < all.size(); i++) {
            Records.StructureConnection c = all.get(i);
            if (PlanningUtils.sameEdge(c, conn)) {
                all.set(i, new Records.StructureConnection(c.from(), c.to(), status));
                found = true;
                break;
            }
        }
        if (!found) {
            all.add(new Records.StructureConnection(conn.from(), conn.to(), status));
        }
        provider.setHighwayConnections(level, all);
    }

    private static void removeHighwayProcessed(ServerLevel level, Records.StructureConnection conn) {
        long k = PlanningUtils.edgeKey(conn.from(), conn.to());
        ConcurrentHashMap<Long, Boolean> proc = HIGHWAY_PROCESSED.get(level);
        if (proc != null)
            proc.remove(k);
    }

    /**
     * 同步生成，用于世界生成前的阻塞阶段（单线程）。
     *
     * @deprecated Use
     * {@link #generateTask(ServerLevel, Records.StructureConnection)}
     * managed by InitialGenManager instead.
     */
    @Deprecated
    public static void generateInline(ServerLevel level, Records.StructureConnection conn) {
        if (level == null || conn == null)
            return;
        WorldDataProvider provider = WorldDataProvider.getInstance();
        try {
            if (Thread.currentThread().isInterrupted())
                return;
            // 标记为 GENERATING
            List<Records.StructureConnection> origin0 = provider.getStructureConnections(level);
            List<Records.StructureConnection> all0 = origin0 != null ? new ArrayList<>(origin0) : new ArrayList<>();
            for (int i = 0; i < all0.size(); i++) {
                Records.StructureConnection c = all0.get(i);
                if (PlanningUtils.sameEdge(c, conn)) {
                    all0.set(i, new Records.StructureConnection(c.from(), c.to(), Records.ConnectionStatus.GENERATING));
                }
            }
            if (!all0.isEmpty())
                provider.setStructureConnections(level, all0);
            // 立即刷新一次统计，让加载界面能显示“生成中”数量
            InitialGenManager.update(level);
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }

            if (generateTask(level, conn)) {
                // 标记 COMPLETED
                List<Records.StructureConnection> origin = provider.getStructureConnections(level);
                List<Records.StructureConnection> all = origin != null ? new ArrayList<>(origin) : new ArrayList<>();
                for (int i = 0; i < all.size(); i++) {
                    Records.StructureConnection c = all.get(i);
                    if (PlanningUtils.sameEdge(c, conn)) {
                        all.set(i,
                                new Records.StructureConnection(c.from(), c.to(), Records.ConnectionStatus.COMPLETED));
                    }
                }
                provider.setStructureConnections(level, all);
                long k = PlanningUtils.edgeKey(conn.from(), conn.to());
                ConcurrentHashMap<Long, Boolean> proc = PROCESSED.get(level);
                if (proc != null)
                    proc.remove(k);
            } else {
                throw new RuntimeException("Generation failed");
            }
        } catch (Throwable t) {
            // 标记 FAILED
            List<Records.StructureConnection> origin = provider.getStructureConnections(level);
            List<Records.StructureConnection> all = origin != null ? new ArrayList<>(origin) : new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                Records.StructureConnection c = all.get(i);
                if (PlanningUtils.sameEdge(c, conn)) {
                    all.set(i, new Records.StructureConnection(c.from(), c.to(), Records.ConnectionStatus.FAILED));
                }
            }
            provider.setStructureConnections(level, all);
            long k = PlanningUtils.edgeKey(conn.from(), conn.to());
            ConcurrentHashMap<Long, Boolean> proc = PROCESSED.get(level);
            if (proc != null)
                proc.remove(k);
        }
    }

    public static void onServerStarted() {
        // 生命周期由中央管理器管理；这里仅清空本地状态
        ALL_RUNNING.clear();
        QUEUES.clear();
        PROCESSED.clear();
        HIGHWAY_QUEUES.clear();
        HIGHWAY_PROCESSED.clear();
        RUNNING_COUNT.clear();
    }

    public static void tick(ServerLevel level) {
        refreshQueue(level);
        refreshHighwayQueue(level);
        ALL_RUNNING.removeIf(f -> f == null || f.isDone() || f.isCancelled());
        ConcurrentLinkedQueue<Records.StructureConnection> q = QUEUES.computeIfAbsent(level,
                l -> new ConcurrentLinkedQueue<>());
        ConcurrentLinkedQueue<Records.StructureConnection> hq = HIGHWAY_QUEUES.computeIfAbsent(level,
                l -> new ConcurrentLinkedQueue<>());
        if (q.isEmpty() && hq.isEmpty())
            return;
        int limit = Math.max(1, ConfigService.get().maxConcurrentGenerations());
        AtomicInteger cnt = RUNNING_COUNT.computeIfAbsent(level, l -> new AtomicInteger(0));
        java.util.List<ServerPlayer> players = new java.util.ArrayList<>();
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p != null && p.serverLevel() == level)
                players.add(p);
        }
        boolean pickHighway = false;
        while (cnt.get() < limit) {
            if (q.isEmpty() && hq.isEmpty()) {
                break;
            }

            boolean isHighway = !hq.isEmpty() && (q.isEmpty() || pickHighway);
            pickHighway = !pickHighway;

            Records.StructureConnection conn = isHighway ? pollNearest(hq, players) : pollNearest(q, players);
            if (conn == null) {
                continue;
            }
            if (isHighway) {
                updateHighwayStatus(level, conn, Records.ConnectionStatus.GENERATING);
            } else {
                WorldDataProvider provider = WorldDataProvider.getInstance();
                List<Records.StructureConnection> origin = provider.getStructureConnections(level);
                List<Records.StructureConnection> all = origin != null ? new ArrayList<>(origin) : new ArrayList<>();
                for (int i = 0; i < all.size(); i++) {
                    Records.StructureConnection c = all.get(i);
                    if (PlanningUtils.sameEdge(c, conn)) {
                        all.set(i,
                                new Records.StructureConnection(c.from(), c.to(), Records.ConnectionStatus.GENERATING));
                    }
                }
                if (!all.isEmpty())
                    provider.setStructureConnections(level, all);
            }

            final Records.StructureConnection task = conn;
            final boolean highwayTask = isHighway;
            cnt.incrementAndGet();
            long epoch = net.shiroha233.roadweaver.runtime.ThreadPoolManager.currentEpoch();
            Future<?> fut = net.shiroha233.roadweaver.runtime.ThreadPoolManager.generationExecutor().submit(() -> {
                try {
                    if (Thread.currentThread().isInterrupted())
                        return;
                    if (!net.shiroha233.roadweaver.runtime.ThreadPoolManager.isEpoch(epoch))
                        return;
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

    private static void refreshQueue(ServerLevel level) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> list = provider.getStructureConnections(level);
        if (list == null)
            return;
        ConcurrentLinkedQueue<Records.StructureConnection> q = QUEUES.computeIfAbsent(level,
                l -> new ConcurrentLinkedQueue<>());
        ConcurrentHashMap<Long, Boolean> proc = PROCESSED.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        for (Records.StructureConnection c : list) {
            long key = PlanningUtils.edgeKey(c.from(), c.to());
            if (proc.putIfAbsent(key, Boolean.TRUE) != null)
                continue;
            if (c.status() != Records.ConnectionStatus.PLANNED && c.status() != Records.ConnectionStatus.GENERATING)
                continue;
            q.add(c);
        }
    }

    private static void refreshHighwayQueue(ServerLevel level) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> list = provider.getHighwayConnections(level);
        if (list == null)
            return;
        ConcurrentLinkedQueue<Records.StructureConnection> q = HIGHWAY_QUEUES.computeIfAbsent(level,
                l -> new ConcurrentLinkedQueue<>());
        ConcurrentHashMap<Long, Boolean> proc = HIGHWAY_PROCESSED.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        for (Records.StructureConnection c : list) {
            long key = PlanningUtils.edgeKey(c.from(), c.to());
            if (proc.putIfAbsent(key, Boolean.TRUE) != null)
                continue;
            if (c.status() != Records.ConnectionStatus.PLANNED && c.status() != Records.ConnectionStatus.GENERATING)
                continue;
            q.add(c);
        }
    }

    private static void safeGenerate(ServerLevel level, Records.StructureConnection conn, long epoch) {
        try {
            if (Thread.currentThread().isInterrupted())
                return;
            if (!net.shiroha233.roadweaver.runtime.ThreadPoolManager.isEpoch(epoch))
                return;
            WorldDataProvider provider = WorldDataProvider.getInstance();
            var reg = level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE);
            ConfiguredFeature<?, ?> cf = reg.get(ROAD_CF_ID);
            PathFeatureConfig cfg = (cf != null && cf.config() instanceof PathFeatureConfig rfc) ? rfc
                    : defaultConfig();
            var modCfg = ConfigService.get();
            RoadGenerationConfig genCfg = RoadGenerationConfig.from(modCfg);
            new Road(level, conn, cfg, genCfg).generateRoad(modCfg.aStarMaxSteps());
            var server = level.getServer();
            if (server != null) {
                server.execute(() -> {
                    if (!net.shiroha233.roadweaver.runtime.ThreadPoolManager.isEpoch(epoch))
                        return;
                    List<Records.StructureConnection> origin2 = provider.getStructureConnections(level);
                    List<Records.StructureConnection> all = origin2 != null ? new ArrayList<>(origin2)
                            : new ArrayList<>();
                    for (int i = 0; i < all.size(); i++) {
                        Records.StructureConnection c = all.get(i);
                        if (PlanningUtils.sameEdge(c, conn)) {
                            all.set(i, new Records.StructureConnection(c.from(), c.to(),
                                    Records.ConnectionStatus.COMPLETED));
                        }
                    }
                    provider.setStructureConnections(level, all);
                    long k = PlanningUtils.edgeKey(conn.from(), conn.to());
                    ConcurrentHashMap<Long, Boolean> proc = PROCESSED.get(level);
                    if (proc != null)
                        proc.remove(k);
                });
            } else {
                if (!net.shiroha233.roadweaver.runtime.ThreadPoolManager.isEpoch(epoch))
                    return;
                List<Records.StructureConnection> origin2 = provider.getStructureConnections(level);
                List<Records.StructureConnection> all = origin2 != null ? new ArrayList<>(origin2) : new ArrayList<>();
                for (int i = 0; i < all.size(); i++) {
                    Records.StructureConnection c = all.get(i);
                    if (PlanningUtils.sameEdge(c, conn)) {
                        all.set(i,
                                new Records.StructureConnection(c.from(), c.to(), Records.ConnectionStatus.COMPLETED));
                    }
                }
                provider.setStructureConnections(level, all);
                long k = PlanningUtils.edgeKey(conn.from(), conn.to());
                ConcurrentHashMap<Long, Boolean> proc = PROCESSED.get(level);
                if (proc != null)
                    proc.remove(k);
            }
        } catch (Throwable t) {
            WorldDataProvider provider = WorldDataProvider.getInstance();
            var server = level.getServer();
            if (server != null) {
                server.execute(() -> {
                    if (!net.shiroha233.roadweaver.runtime.ThreadPoolManager.isEpoch(epoch))
                        return;
                    List<Records.StructureConnection> origin2 = provider.getStructureConnections(level);
                    List<Records.StructureConnection> all = origin2 != null ? new ArrayList<>(origin2)
                            : new ArrayList<>();
                    for (int i = 0; i < all.size(); i++) {
                        Records.StructureConnection c = all.get(i);
                        if (PlanningUtils.sameEdge(c, conn)) {
                            all.set(i,
                                    new Records.StructureConnection(c.from(), c.to(), Records.ConnectionStatus.FAILED));
                        }
                    }
                    provider.setStructureConnections(level, all);
                    long k = PlanningUtils.edgeKey(conn.from(), conn.to());
                    ConcurrentHashMap<Long, Boolean> proc = PROCESSED.get(level);
                    if (proc != null)
                        proc.remove(k);
                });
            } else {
                if (!net.shiroha233.roadweaver.runtime.ThreadPoolManager.isEpoch(epoch))
                    return;
                List<Records.StructureConnection> origin2 = provider.getStructureConnections(level);
                List<Records.StructureConnection> all = origin2 != null ? new ArrayList<>(origin2) : new ArrayList<>();
                for (int i = 0; i < all.size(); i++) {
                    Records.StructureConnection c = all.get(i);
                    if (PlanningUtils.sameEdge(c, conn)) {
                        all.set(i, new Records.StructureConnection(c.from(), c.to(), Records.ConnectionStatus.FAILED));
                    }
                }
                provider.setStructureConnections(level, all);
                long k = PlanningUtils.edgeKey(conn.from(), conn.to());
                ConcurrentHashMap<Long, Boolean> proc = PROCESSED.get(level);
                if (proc != null)
                    proc.remove(k);
            }
        }
    }

    private static PathFeatureConfig defaultConfig() {
        return new PathFeatureConfig();
    }

    private static long dist2XZ(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static long playerDistance2(Records.StructureConnection c, java.util.List<ServerPlayer> players) {
        if (players == null || players.isEmpty())
            return Long.MAX_VALUE;
        long best = Long.MAX_VALUE;
        int mx = (c.from().getX() + c.to().getX()) >> 1;
        int mz = (c.from().getZ() + c.to().getZ()) >> 1;
        BlockPos mid = new BlockPos(mx, 0, mz);
        for (ServerPlayer p : players) {
            BlockPos pb = p.blockPosition();
            long d = dist2XZ(pb, c.from());
            if (d < best)
                best = d;
            d = dist2XZ(pb, c.to());
            if (d < best)
                best = d;
            d = dist2XZ(pb, mid);
            if (d < best)
                best = d;
        }
        return best;
    }

    private static Records.StructureConnection pollNearest(ConcurrentLinkedQueue<Records.StructureConnection> q,
            java.util.List<ServerPlayer> players) {
        if (q.isEmpty())
            return null;
        if (players == null || players.isEmpty())
            return q.poll();
        java.util.Iterator<Records.StructureConnection> it = q.iterator();
        Records.StructureConnection best = null;
        long bestd = Long.MAX_VALUE;
        while (it.hasNext()) {
            Records.StructureConnection e = it.next();
            long d = playerDistance2(e, players);
            if (d < bestd) {
                bestd = d;
                best = e;
            }
        }
        if (best != null && q.remove(best))
            return best;
        return q.poll();
    }

    // ...

}
