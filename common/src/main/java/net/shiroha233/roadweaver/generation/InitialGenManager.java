package net.shiroha233.roadweaver.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.features.highway.planning.HighwayPlanningService;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.HighwayCellPathPlanningService;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.structures.placement.SpawnCabinPlacer;

import java.util.List;

/**
 * 初始道路生成管理器：在服务器启动后，阻塞直到初始规划范围内的道路生成完成，并提供进度统计。
 */
public final class InitialGenManager {
    private InitialGenManager() {
    }

    private static volatile boolean active;
    private static final java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger(
            0);
    private static final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger(
            0);

    private static final java.util.concurrent.atomic.AtomicInteger generating = new java.util.concurrent.atomic.AtomicInteger(
            0);
    private static final java.util.concurrent.atomic.AtomicInteger failed = new java.util.concurrent.atomic.AtomicInteger(
            0);

    public static boolean isActive() {
        return active;
    }

    public static int getTotal() {
        return total.get();
    }

    public static int getDone() {
        return done.get();
    }

    public static int getGenerating() {
        return generating.get();
    }

    public static int getFailed() {
        return failed.get();
    }

    /**
     * 在服务器启动时调用：执行初始规划并计算总任务数。
     */
    public static void begin(ServerLevel level) {
        if (level == null)
            return;
        // 清零状态
        active = true;
        total.set(0);
        done.set(0);

        generating.set(0);
        failed.set(0);

        // 重置地形采样统计（用于 GUI 显示缓存命中率和每秒采样数）
        net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.TerrainSamplingStats.reset();

        // 确保生成线程池已初始化
        RoadGenerationService.onServerStarted();

        // 首开世界：按配置尝试放置出生点小屋（幂等）
        if (ConfigService.get().spawnCabinEnabled()) {
            SpawnCabinPlacer.ensurePlaced(level);
        }

        // 进行初始规划：
        // - highwayEnabled=true：规划 Highway 网格边；Path 规划将延后到 Highway 网格单元格四边终态后再做
        // - highwayEnabled=false：保持旧行为，直接使用 RoadPlanningService 规划结构点路网
        if (ConfigService.get().highwayEnabled()) {
            HighwayPlanningService.initialPlan(level);
        } else {
            RoadPlanningService.initialPlan(level);
        }

        // 统计总数
        WorldDataProvider provider = WorldDataProvider.getInstance();
        if (ConfigService.get().highwayEnabled()) {
            List<Records.StructureConnection> highways = provider.getHighwayConnections(level);
            total.set((highways == null ? 0 : highways.size()));
        } else {
            List<Records.StructureConnection> conns = provider.getStructureConnections(level);
            total.set((conns == null ? 0 : conns.size()));
        }
        // 初始化一次完成度
        update(level);
    }

    /**
     * 循环推进生成并阻塞直到全部完成或总数为0。
     * 注意：在服务器启动线程中调用，期间不会触发常规 tick。
     * 改为多线程并行生成以提高速度。
     */
    public static void blockUntilDone(ServerLevel level) {
        if (!active)
            return;

        if (!ConfigService.get().highwayEnabled()) {
            // 旧模式：仅生成 structureConnections（path）
            WorldDataProvider provider2 = WorldDataProvider.getInstance();
            List<Records.StructureConnection> list2 = provider2.getStructureConnections(level);
            if (list2 == null || list2.isEmpty()) {
                active = false;
                return;
            }

            List<Records.StructureConnection> roadTasks = new java.util.ArrayList<>();
            for (Records.StructureConnection c : list2) {
                if (c.status() == Records.ConnectionStatus.PLANNED) {
                    roadTasks.add(c);
                }
            }

            total.set(roadTasks.size());

            if (!roadTasks.isEmpty()) {
                int nThreads = net.shiroha233.roadweaver.config.ConfigService.get().initialGenerationThreads();
                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(
                        nThreads,
                        new java.util.concurrent.ThreadFactory() {
                            private final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(
                                    1);

                            @Override
                            public Thread newThread(Runnable r) {
                                Thread t = new Thread(r, "RoadWeaver-InitialGen-Path-" + count.getAndIncrement());
                                t.setDaemon(true);
                                return t;
                            }
                        });

                record PathGenResult(long key, boolean success) {
                }
                List<java.util.concurrent.Future<PathGenResult>> futures = new java.util.ArrayList<>();
                for (Records.StructureConnection task : roadTasks) {
                    futures.add(executor.submit(() -> {
                        generating.incrementAndGet();
                        boolean success = RoadGenerationService.generateTask(level, task);
                        generating.decrementAndGet();
                        if (success)
                            done.incrementAndGet();
                        else
                            failed.incrementAndGet();
                        return new PathGenResult(PlanningUtils.edgeKey(task.from(), task.to()), success);
                    }));
                }

                java.util.Map<Long, Boolean> roadResults = new java.util.HashMap<>();
                for (java.util.concurrent.Future<PathGenResult> f : futures) {
                    try {
                        PathGenResult r = f.get();
                        if (r != null)
                            roadResults.put(r.key(), r.success());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                executor.shutdown();
                try {
                    if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }

                List<Records.StructureConnection> currentList = provider2.getStructureConnections(level);
                if (currentList != null && !roadResults.isEmpty()) {
                    List<Records.StructureConnection> updatedList = new java.util.ArrayList<>(currentList);
                    boolean changed = false;
                    for (int i = 0; i < updatedList.size(); i++) {
                        Records.StructureConnection original = updatedList.get(i);
                        long k = PlanningUtils.edgeKey(original.from(), original.to());
                        Boolean ok = roadResults.get(k);
                        if (ok == null)
                            continue;
                        Records.ConnectionStatus newStatus = ok ? Records.ConnectionStatus.COMPLETED
                                : Records.ConnectionStatus.FAILED;
                        updatedList.set(i, new Records.StructureConnection(original.from(), original.to(), newStatus));
                        changed = true;
                    }
                    if (changed) {
                        provider2.setStructureConnections(level, updatedList);
                    }
                }
            }

            active = false;
            return;
        }

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> highwayList = provider.getHighwayConnections(level);

        int plannedHighwayTasks = 0;

        if (highwayList != null && !highwayList.isEmpty()) {
            // 1) 先生成 Highway（公路），保证每段公路是一个整体
            List<Records.StructureConnection> highwayTasks = new java.util.ArrayList<>();
            for (Records.StructureConnection c : highwayList) {
                if (c.status() == Records.ConnectionStatus.PLANNED) {
                    highwayTasks.add(c);
                }
            }

            plannedHighwayTasks = highwayTasks.size();
            total.set(plannedHighwayTasks);

            if (!highwayTasks.isEmpty()) {
                // 提交任务到线程池 - 使用配置的初始生成线程数创建专用线程池
                int nThreads = net.shiroha233.roadweaver.config.ConfigService.get().initialGenerationThreads();
                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(
                        nThreads,
                        new java.util.concurrent.ThreadFactory() {
                            private final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(
                                    1);

                            @Override
                            public Thread newThread(Runnable r) {
                                Thread t = new Thread(r, "RoadWeaver-InitialGen-" + count.getAndIncrement());
                                t.setDaemon(true);
                                return t;
                            }
                        });
                record HighwayGenResult(long key, boolean success) {
                }
                List<java.util.concurrent.Future<HighwayGenResult>> futures = new java.util.ArrayList<>();

                for (Records.StructureConnection task : highwayTasks) {
                    futures.add(executor.submit(() -> {
                        generating.incrementAndGet();

                        boolean success = RoadGenerationService.generateHighwayTask(level, task);

                        generating.decrementAndGet();
                        if (success) {
                            done.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                        }
                        return new HighwayGenResult(PlanningUtils.edgeKey(task.from(), task.to()), success);
                    }));
                }

                // 等待所有任务完成
                // 收集结果用于批量更新
                java.util.Map<Long, Boolean> highwayResults = new java.util.HashMap<>();
                for (java.util.concurrent.Future<HighwayGenResult> f : futures) {
                    try {
                        HighwayGenResult r = f.get();
                        if (r != null)
                            highwayResults.put(r.key(), r.success());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // 关闭专用线程池
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }

                List<Records.StructureConnection> currentHighways = provider.getHighwayConnections(level);
                if (currentHighways != null && !highwayResults.isEmpty()) {
                    List<Records.StructureConnection> updatedList = new java.util.ArrayList<>(currentHighways);
                    boolean changed = false;
                    for (int i = 0; i < updatedList.size(); i++) {
                        Records.StructureConnection original = updatedList.get(i);
                        long k = PlanningUtils.edgeKey(original.from(), original.to());
                        Boolean ok = highwayResults.get(k);
                        if (ok == null)
                            continue;
                        Records.ConnectionStatus newStatus = ok ? Records.ConnectionStatus.COMPLETED
                                : Records.ConnectionStatus.FAILED;
                        updatedList.set(i, new Records.StructureConnection(original.from(), original.to(), newStatus));
                        changed = true;
                    }
                    if (changed) {
                        provider.setHighwayConnections(level, updatedList);
                    }
                }
            }
        }

        // 2) Highway 网格单元格四边进入终态后，再触发该单元格内部的结构点路网规划，并添加单入口接入 Highway。
        {
            // 初始生成阶段与“初次加载”保持一致：只触发玩家（或出生点）所在的 1x1 cell。
            // 原理：避免在大半径矩形内遍历大量 cell（绝大多数还未规划/生成）。
            var cfg = ConfigService.get();
            int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
            BlockPos centerPos = level.getSharedSpawnPos();
            var server = level.getServer();
            if (server != null) {
                var p = server.getPlayerList().getPlayers().stream()
                        .filter(sp -> sp != null && sp.serverLevel() == level)
                        .findFirst()
                        .orElse(null);
                if (p != null) {
                    centerPos = p.blockPosition();
                }
            }
            int cellGx = Math.floorDiv(centerPos.getX(), gridBlocks);
            int cellGz = Math.floorDiv(centerPos.getZ(), gridBlocks);
            int minX = cellGx * gridBlocks;
            int maxX = (cellGx + 1) * gridBlocks;
            int minZ = cellGz * gridBlocks;
            int maxZ = (cellGz + 1) * gridBlocks;
            HighwayCellPathPlanningService.planCompletedCellsInRect(level, minX, minZ, maxX, maxZ);
        }

        // 3) 生成格内路网（由上一步写入 WorldDataProvider.structureConnections 的 PLANNED 任务）
        WorldDataProvider provider2 = WorldDataProvider.getInstance();
        List<Records.StructureConnection> list2 = provider2.getStructureConnections(level);
        if (list2 != null && !list2.isEmpty()) {
            // 筛选出需要生成的任务
            List<Records.StructureConnection> roadTasks = new java.util.ArrayList<>();
            for (Records.StructureConnection c : list2) {
                if (c.status() == Records.ConnectionStatus.PLANNED) {
                    roadTasks.add(c);
                }
            }

            // 将总任务数更新为 Highway + Path 两阶段总和（避免进度条在 Path 阶段超过 100%）
            total.set(plannedHighwayTasks + roadTasks.size());

            if (!roadTasks.isEmpty()) {
                int nThreads = net.shiroha233.roadweaver.config.ConfigService.get().initialGenerationThreads();
                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(
                        nThreads,
                        new java.util.concurrent.ThreadFactory() {
                            private final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(
                                    1);

                            @Override
                            public Thread newThread(Runnable r) {
                                Thread t = new Thread(r, "RoadWeaver-InitialGen-Path-" + count.getAndIncrement());
                                t.setDaemon(true);
                                return t;
                            }
                        });

                record PathGenResult(long key, boolean success) {
                }
                List<java.util.concurrent.Future<PathGenResult>> futures = new java.util.ArrayList<>();
                for (Records.StructureConnection task : roadTasks) {
                    futures.add(executor.submit(() -> {
                        generating.incrementAndGet();
                        boolean success = RoadGenerationService.generateTask(level, task);
                        generating.decrementAndGet();
                        if (success)
                            done.incrementAndGet();
                        else
                            failed.incrementAndGet();
                        return new PathGenResult(PlanningUtils.edgeKey(task.from(), task.to()), success);
                    }));
                }

                java.util.Map<Long, Boolean> roadResults = new java.util.HashMap<>();
                for (java.util.concurrent.Future<PathGenResult> f : futures) {
                    try {
                        PathGenResult r = f.get();
                        if (r != null)
                            roadResults.put(r.key(), r.success());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                executor.shutdown();
                try {
                    if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }

                // 批量更新 Path 连接状态
                List<Records.StructureConnection> currentList = provider2.getStructureConnections(level);
                if (currentList != null && !roadResults.isEmpty()) {
                    List<Records.StructureConnection> updatedList = new java.util.ArrayList<>(currentList);
                    boolean changed = false;
                    for (int i = 0; i < updatedList.size(); i++) {
                        Records.StructureConnection original = updatedList.get(i);
                        long k = PlanningUtils.edgeKey(original.from(), original.to());
                        Boolean ok = roadResults.get(k);
                        if (ok == null)
                            continue;
                        Records.ConnectionStatus newStatus = ok ? Records.ConnectionStatus.COMPLETED
                                : Records.ConnectionStatus.FAILED;
                        updatedList.set(i, new Records.StructureConnection(original.from(), original.to(), newStatus));
                        changed = true;
                    }
                    if (changed) {
                        provider2.setStructureConnections(level, updatedList);
                    }
                }
            }
        }

        // 确保道路数据刷新到存储，以便树木生成时可以查询
        net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage.flushAll(level);
        // 清除道路位置查询缓存，避免过时缓存导致树木阻止失效
        net.shiroha233.roadweaver.persistence.RoadPositionQuery.clearCache(level);
        active = false;
    }

    /**
     * 读取世界数据统计完成数量。
     * 注意：在多线程生成期间，此方法可能不会反映实时进度（因为我们只更新了 AtomicInteger，没有更新 WorldData），
     * 但 UI 读取的是 AtomicInteger，所以 UI 是实时的。
     * 生成结束后，再次调用此方法会从 WorldData 同步最终状态。
     */
    public static void update(ServerLevel level) {
        // 如果处于活跃状态（生成中），不要从 WorldData 重置计数器，因为 WorldData 还没更新
        if (active)
            return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> conns = provider.getStructureConnections(level);
        List<Records.StructureConnection> highways = provider.getHighwayConnections(level);
        if ((conns == null || conns.isEmpty()) && (highways == null || highways.isEmpty())) {
            total.set(0);

            generating.set(0);
            done.set(0);
            failed.set(0);
            return;
        }
        int g = 0, c = 0, f = 0;
        int t = 0;
        if (conns != null) {
            t += conns.size();
            for (Records.StructureConnection sc : conns) {
                Records.ConnectionStatus s = sc.status();
                if (s == Records.ConnectionStatus.GENERATING)
                    g++;
                else if (s == Records.ConnectionStatus.COMPLETED)
                    c++;
                else if (s == Records.ConnectionStatus.FAILED)
                    f++;
            }
        }
        if (highways != null) {
            t += highways.size();
            for (Records.StructureConnection sc : highways) {
                Records.ConnectionStatus s = sc.status();
                if (s == Records.ConnectionStatus.GENERATING)
                    g++;
                else if (s == Records.ConnectionStatus.COMPLETED)
                    c++;
                else if (s == Records.ConnectionStatus.FAILED)
                    f++;
            }
        }
        total.set(t);

        generating.set(g);
        done.set(c);
        failed.set(f);
    }
}
