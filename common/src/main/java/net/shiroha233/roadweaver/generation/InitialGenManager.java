/* 文件职责：管理启动阶段的初始道路生成。 */
package net.shiroha233.roadweaver.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.highway.planning.HighwayPlanningService;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.HighwayCellPathPlanningService;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.postprocess.RoadSnapService;
import net.shiroha233.roadweaver.structures.placement.SpawnCabinPlacer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 初始道路生成管理器，服务器启动时阻塞生成初始规划范围内的道路并提供进度统计
 */
public final class InitialGenManager {
    private InitialGenManager() {}

    private static volatile boolean active;
    private static final AtomicInteger total = new AtomicInteger(0);
    private static final AtomicInteger done = new AtomicInteger(0);
    private static final AtomicInteger generating = new AtomicInteger(0);
    private static final AtomicInteger failed = new AtomicInteger(0);

    public static boolean isActive() { return active; }
    public static int getTotal() { return total.get(); }
    public static int getDone() { return done.get(); }
    public static int getGenerating() { return generating.get(); }
    public static int getFailed() { return failed.get(); }

    public static void begin(ServerLevel level) {
        if (level == null) return;

        active = true;
        total.set(0);
        done.set(0);
        generating.set(0);
        failed.set(0);

        net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingStats.reset();
        net.shiroha233.roadweaver.pathfinding.cache.AccurateSamplingStats.reset();

        RoadGenerationService.onServerStarted();

        ModConfig cfg = ConfigService.get();
        if (cfg.roadAppearance().spawnCabinEnabled()) {
            SpawnCabinPlacer.ensurePlaced(level);
        }

        if (cfg.highway().enabled()) {
            HighwayPlanningService.initialPlan(level);
        } else {
            RoadPlanningService.initialPlan(level);
        }

        WorldDataProvider provider = WorldDataProvider.getInstance();
        if (cfg.highway().enabled()) {
            List<StructureConnection> highways = provider.getHighwayConnections(level);
            total.set(highways == null ? 0 : highways.size());
        } else {
            List<StructureConnection> conns = provider.getStructureConnections(level);
            total.set(conns == null ? 0 : conns.size());
        }
        update(level);
    }

    public static void blockUntilDone(ServerLevel level) {
        if (!active) return;
        ModConfig cfg = ConfigService.get();
        WorldDataProvider provider = WorldDataProvider.getInstance();

        if (!cfg.highway().enabled()) {
            List<StructureConnection> list = provider.getStructureConnections(level);
            if (list == null || list.isEmpty()) {
                active = false;
                return;
            }
            List<StructureConnection> roadTasks = filterPlanned(list);
            total.set(roadTasks.size());

            if (!roadTasks.isEmpty()) {
                Map<Long, Boolean> results = submitAndCollect(level, roadTasks, false, "Path");
                batchUpdateConnectionStatus(provider, level, results, false);
            }
            snapInitialRoads(level, list);
            flushAndFinish(level);
            return;
        }

        // Highway 模式：先生成公路
        List<StructureConnection> highwayList = provider.getHighwayConnections(level);
        int plannedHighwayCount = 0;

        if (highwayList != null && !highwayList.isEmpty()) {
            List<StructureConnection> highwayTasks = filterPlanned(highwayList);
            plannedHighwayCount = highwayTasks.size();
            total.set(plannedHighwayCount);

            if (!highwayTasks.isEmpty()) {
                Map<Long, Boolean> hwResults = submitAndCollect(level, highwayTasks, true, "Highway");
                batchUpdateConnectionStatus(provider, level, hwResults, true);
            }
        }

        // 触发网格单元格内部路网规划
        triggerCellPathPlanning(level, cfg);

        // 生成格内路网
        List<StructureConnection> pathList = provider.getStructureConnections(level);
        if (pathList != null && !pathList.isEmpty()) {
            List<StructureConnection> roadTasks = filterPlanned(pathList);
            total.set(plannedHighwayCount + roadTasks.size());

            if (!roadTasks.isEmpty()) {
                Map<Long, Boolean> pathResults = submitAndCollect(level, roadTasks, false, "Path");
                batchUpdateConnectionStatus(provider, level, pathResults, false);
            }
        }

        List<StructureConnection> allConns = provider.getStructureConnections(level);
        if (allConns == null) allConns = List.of();
        List<StructureConnection> allHighways = provider.getHighwayConnections(level);
        if (allHighways == null) allHighways = List.of();
        List<StructureConnection> combined = new ArrayList<>(allConns.size() + allHighways.size());
        combined.addAll(allConns);
        combined.addAll(allHighways);
        snapInitialRoads(level, combined);

        flushAndFinish(level);
    }

    private static void snapInitialRoads(ServerLevel level, List<StructureConnection> conns) {
        if (conns == null || conns.isEmpty()) return;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (StructureConnection c : conns) {
            minX = Math.min(minX, Math.min(c.from().getX(), c.to().getX()));
            minZ = Math.min(minZ, Math.min(c.from().getZ(), c.to().getZ()));
            maxX = Math.max(maxX, Math.max(c.from().getX(), c.to().getX()));
            maxZ = Math.max(maxZ, Math.max(c.from().getZ(), c.to().getZ()));
        }
        RoadSnapService.snapAllRoads(level, minX, minZ, maxX, maxZ);
    }

    // ==================== 提取的通用辅助方法 ====================

    private static List<StructureConnection> filterPlanned(List<StructureConnection> list) {
        List<StructureConnection> out = new ArrayList<>();
        for (StructureConnection c : list) {
            if (c.status() == ConnectionStatus.PLANNED) out.add(c);
        }
        return out;
    }

    private record GenResult(long key, boolean success) {}

    private static Map<Long, Boolean> submitAndCollect(ServerLevel level,
                                                       List<StructureConnection> tasks,
                                                       boolean highway,
                                                       String threadPrefix) {
        int nThreads = ConfigService.get().performance().initialGenerationThreads();
        ExecutorService executor = Executors.newFixedThreadPool(nThreads, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "RoadWeaver-InitialGen-" + threadPrefix + "-" + count.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        List<Future<GenResult>> futures = new ArrayList<>();
        for (StructureConnection task : tasks) {
            futures.add(executor.submit(() -> {
                generating.incrementAndGet();
                boolean success = highway
                        ? RoadGenerationService.generateHighwayTask(level, task)
                        : RoadGenerationService.generateTask(level, task);
                generating.decrementAndGet();
                if (success) done.incrementAndGet(); else failed.incrementAndGet();
                return new GenResult(PlanningUtils.edgeKey(task.from(), task.to()), success);
            }));
        }

        Map<Long, Boolean> results = new HashMap<>();
        for (Future<GenResult> f : futures) {
            try {
                GenResult r = f.get();
                if (r != null) results.put(r.key(), r.success());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        return results;
    }

    private static void batchUpdateConnectionStatus(WorldDataProvider provider,
                                                    ServerLevel level,
                                                    Map<Long, Boolean> results,
                                                    boolean highway) {
        if (results.isEmpty()) return;

        List<StructureConnection> current = highway
                ? provider.getHighwayConnections(level)
                : provider.getStructureConnections(level);
        if (current == null) return;

        List<StructureConnection> updated = new ArrayList<>(current);
        boolean changed = false;
        for (int i = 0; i < updated.size(); i++) {
            StructureConnection original = updated.get(i);
            long k = PlanningUtils.edgeKey(original.from(), original.to());
            Boolean ok = results.get(k);
            if (ok == null) continue;
            ConnectionStatus newStatus = ok ? ConnectionStatus.COMPLETED : ConnectionStatus.FAILED;
            updated.set(i, new StructureConnection(original.from(), original.to(), newStatus));
            changed = true;
        }
        if (changed) {
            if (highway) provider.setHighwayConnections(level, updated);
            else provider.setStructureConnections(level, updated);
        }
    }

    private static void triggerCellPathPlanning(ServerLevel level, ModConfig cfg) {
        int gridBlocks = Math.max(1, cfg.highway().gridBlocks());
        BlockPos centerPos = level.getSharedSpawnPos();
        var server = level.getServer();
        if (server != null) {
            var p = server.getPlayerList().getPlayers().stream()
                    .filter(sp -> sp != null && sp.serverLevel() == level)
                    .findFirst().orElse(null);
            if (p != null) centerPos = p.blockPosition();
        }
        int cellGx = Math.floorDiv(centerPos.getX(), gridBlocks);
        int cellGz = Math.floorDiv(centerPos.getZ(), gridBlocks);
        HighwayCellPathPlanningService.planCompletedCellsInRect(level,
                cellGx * gridBlocks, cellGz * gridBlocks,
                (cellGx + 1) * gridBlocks, (cellGz + 1) * gridBlocks);
    }

    private static void flushAndFinish(ServerLevel level) {
        RoadShardStorage.flushAll(level);
        RoadPositionQuery.clearCache(level);
        active = false;
    }

    public static void update(ServerLevel level) {
        if (active) return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> conns = provider.getStructureConnections(level);
        List<StructureConnection> highways = provider.getHighwayConnections(level);

        if ((conns == null || conns.isEmpty()) && (highways == null || highways.isEmpty())) {
            total.set(0);
            generating.set(0);
            done.set(0);
            failed.set(0);
            return;
        }

        int g = 0, c = 0, f = 0, t = 0;
        if (conns != null) {
            t += conns.size();
            for (StructureConnection sc : conns) {
                ConnectionStatus s = sc.status();
                if (s == ConnectionStatus.GENERATING) g++;
                else if (s == ConnectionStatus.COMPLETED) c++;
                else if (s == ConnectionStatus.FAILED) f++;
            }
        }
        if (highways != null) {
            t += highways.size();
            for (StructureConnection sc : highways) {
                ConnectionStatus s = sc.status();
                if (s == ConnectionStatus.GENERATING) g++;
                else if (s == ConnectionStatus.COMPLETED) c++;
                else if (s == ConnectionStatus.FAILED) f++;
            }
        }
        total.set(t);
        generating.set(g);
        done.set(c);
        failed.set(f);
    }
}
