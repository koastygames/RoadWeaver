/* 文件职责：负责世界初始道路生成的同步编排、统计与收尾。 */
package net.shiroha233.roadweaver.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.highway.planning.HighwayPlanningService;
import net.shiroha233.roadweaver.helpers.LevelCompat;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 初始生成管理器，负责同步等待首轮道路任务完成。
 */
public final class InitialGenManager {
    private static volatile boolean active;
    private static final AtomicInteger total = new AtomicInteger(0);
    private static final AtomicInteger done = new AtomicInteger(0);
    private static final AtomicInteger generating = new AtomicInteger(0);
    private static final AtomicInteger failed = new AtomicInteger(0);

    private InitialGenManager() {
    }

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

    public static void begin(ServerLevel level) {
        if (level == null) {
            return;
        }

        active = true;
        total.set(0);
        done.set(0);
        generating.set(0);
        failed.set(0);

        net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingStats.reset();
        net.shiroha233.roadweaver.pathfinding.cache.AccurateSamplingStats.reset();

        RoadGenerationService.onServerStarted();

        ModConfig config = ConfigService.get();
        if (config.roadAppearance().spawnCabinEnabled()) {
            SpawnCabinPlacer.ensurePlaced(level);
        }

        if (config.highway().enabled()) {
            HighwayPlanningService.initialPlan(level);
        } else {
            RoadPlanningService.initialPlan(level);
        }

        if (config.longDrive().enabled()) {
            net.shiroha233.roadweaver.features.longdrive.planning.LongDrivePlanningService.initialPlan(level);
        }

        WorldDataProvider provider = WorldDataProvider.getInstance();
        if (config.highway().enabled()) {
            List<StructureConnection> highways = provider.getHighwayConnections(level);
            total.set(highways == null ? 0 : highways.size());
        } else {
            List<StructureConnection> roads = provider.getStructureConnections(level);
            total.set(roads == null ? 0 : roads.size());
        }
        update(level);
    }

    public static void blockUntilDone(ServerLevel level) {
        if (!active || level == null) {
            return;
        }

        ModConfig config = ConfigService.get();
        WorldDataProvider provider = WorldDataProvider.getInstance();

        if (!config.highway().enabled()) {
            List<StructureConnection> connections = provider.getStructureConnections(level);
            if (connections == null || connections.isEmpty()) {
                active = false;
                return;
            }

            List<StructureConnection> roadTasks = filterPlanned(connections);
            total.set(roadTasks.size());
            if (!roadTasks.isEmpty()) {
                Map<Long, Boolean> results = submitAndCollect(level, roadTasks, false, "Path");
                batchUpdateConnectionStatus(provider, level, results, false);
            }
            snapInitialRoads(level, connections);
            flushAndFinish(level);
            return;
        }

        List<StructureConnection> highwayConnections = provider.getHighwayConnections(level);
        int plannedHighwayCount = 0;
        if (highwayConnections != null && !highwayConnections.isEmpty()) {
            List<StructureConnection> highwayTasks = filterPlanned(highwayConnections);
            plannedHighwayCount = highwayTasks.size();
            total.set(plannedHighwayCount);
            if (!highwayTasks.isEmpty()) {
                Map<Long, Boolean> results = submitAndCollect(level, highwayTasks, true, "Highway");
                batchUpdateConnectionStatus(provider, level, results, true);
            }
        }

        triggerCellPathPlanning(level, config);

        List<StructureConnection> pathConnections = provider.getStructureConnections(level);
        if (pathConnections != null && !pathConnections.isEmpty()) {
            List<StructureConnection> roadTasks = filterPlanned(pathConnections);
            total.set(plannedHighwayCount + roadTasks.size());
            if (!roadTasks.isEmpty()) {
                Map<Long, Boolean> results = submitAndCollect(level, roadTasks, false, "Path");
                batchUpdateConnectionStatus(provider, level, results, false);
            }
        }

        List<StructureConnection> allRoads = provider.getStructureConnections(level);
        if (allRoads == null) {
            allRoads = List.of();
        }
        List<StructureConnection> allHighways = provider.getHighwayConnections(level);
        if (allHighways == null) {
            allHighways = List.of();
        }

        List<StructureConnection> combined = new ArrayList<>(allRoads.size() + allHighways.size());
        combined.addAll(allRoads);
        combined.addAll(allHighways);
        snapInitialRoads(level, combined);
        flushAndFinish(level);
    }

    private static void snapInitialRoads(ServerLevel level, List<StructureConnection> connections) {
        if (connections == null || connections.isEmpty()) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (StructureConnection connection : connections) {
            minX = Math.min(minX, Math.min(connection.from().getX(), connection.to().getX()));
            minZ = Math.min(minZ, Math.min(connection.from().getZ(), connection.to().getZ()));
            maxX = Math.max(maxX, Math.max(connection.from().getX(), connection.to().getX()));
            maxZ = Math.max(maxZ, Math.max(connection.from().getZ(), connection.to().getZ()));
        }
        RoadSnapService.snapAllRoads(level, minX, minZ, maxX, maxZ);
    }

    private static List<StructureConnection> filterPlanned(List<StructureConnection> connections) {
        List<StructureConnection> planned = new ArrayList<>();
        for (StructureConnection connection : connections) {
            if (connection.status() == ConnectionStatus.PLANNED) {
                planned.add(connection);
            }
        }
        return planned;
    }

    private record GenResult(long key, boolean success) {
    }

    private static Map<Long, Boolean> submitAndCollect(ServerLevel level,
                                                       List<StructureConnection> tasks,
                                                       boolean highway,
                                                       String threadPrefix) {
        int threads = ConfigService.get().performance().initialGenerationThreads();
        ExecutorService executor = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "RoadWeaver-InitialGen-" + threadPrefix + "-" + count.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });

        List<Future<GenResult>> futures = new ArrayList<>();
        for (StructureConnection task : tasks) {
            futures.add(executor.submit(() -> {
                generating.incrementAndGet();
                boolean success;
                try {
                    success = highway
                            ? RoadGenerationService.generateHighwayTask(level, task)
                            : RoadGenerationService.generateTask(level, task);
                } finally {
                    generating.decrementAndGet();
                }

                if (success) {
                    done.incrementAndGet();
                } else {
                    failed.incrementAndGet();
                }
                return new GenResult(PlanningUtils.edgeKey(task.from(), task.to()), success);
            }));
        }

        Map<Long, Boolean> results = new HashMap<>();
        for (Future<GenResult> future : futures) {
            try {
                GenResult result = future.get();
                if (result != null) {
                    results.put(result.key(), result.success());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
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
        if (results.isEmpty()) {
            return;
        }

        List<StructureConnection> current = highway
                ? provider.getHighwayConnections(level)
                : provider.getStructureConnections(level);
        if (current == null) {
            return;
        }

        List<StructureConnection> updated = new ArrayList<>(current);
        boolean changed = false;
        for (int i = 0; i < updated.size(); i++) {
            StructureConnection original = updated.get(i);
            Boolean success = results.get(PlanningUtils.edgeKey(original.from(), original.to()));
            if (success == null) {
                continue;
            }
            updated.set(i, new StructureConnection(
                    original.from(),
                    original.to(),
                    success ? ConnectionStatus.COMPLETED : ConnectionStatus.FAILED));
            changed = true;
        }

        if (!changed) {
            return;
        }
        if (highway) {
            provider.setHighwayConnections(level, updated);
        } else {
            provider.setStructureConnections(level, updated);
        }
    }

    private static void triggerCellPathPlanning(ServerLevel level, ModConfig config) {
        int gridBlocks = Math.max(1, config.highway().gridBlocks());
        BlockPos centerPos = LevelCompat.getWorldSpawnPos(level);
        var server = level.getServer();
        if (server != null) {
            var player = server.getPlayerList().getPlayers().stream()
                    .filter(candidate -> candidate != null && candidate.level() == level)
                    .findFirst()
                    .orElse(null);
            if (player != null) {
                centerPos = player.blockPosition();
            }
        }

        int cellGx = Math.floorDiv(centerPos.getX(), gridBlocks);
        int cellGz = Math.floorDiv(centerPos.getZ(), gridBlocks);
        HighwayCellPathPlanningService.planCompletedCellsInRect(
                level,
                cellGx * gridBlocks,
                cellGz * gridBlocks,
                (cellGx + 1) * gridBlocks,
                (cellGz + 1) * gridBlocks);
    }

    private static void flushAndFinish(ServerLevel level) {
        RoadShardStorage.flushAll(level);
        RoadPositionQuery.clearCache(level);
        active = false;
    }

    public static void update(ServerLevel level) {
        if (active || level == null) {
            return;
        }

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> connections = provider.getStructureConnections(level);
        List<StructureConnection> highways = provider.getHighwayConnections(level);

        if ((connections == null || connections.isEmpty()) && (highways == null || highways.isEmpty())) {
            total.set(0);
            generating.set(0);
            done.set(0);
            failed.set(0);
            return;
        }

        int totalCount = 0;
        int generatingCount = 0;
        int doneCount = 0;
        int failedCount = 0;

        if (connections != null) {
            totalCount += connections.size();
            for (StructureConnection connection : connections) {
                switch (connection.status()) {
                    case GENERATING -> generatingCount++;
                    case COMPLETED -> doneCount++;
                    case FAILED -> failedCount++;
                    default -> {
                    }
                }
            }
        }

        if (highways != null) {
            totalCount += highways.size();
            for (StructureConnection connection : highways) {
                switch (connection.status()) {
                    case GENERATING -> generatingCount++;
                    case COMPLETED -> doneCount++;
                    case FAILED -> failedCount++;
                    default -> {
                    }
                }
            }
        }

        total.set(totalCount);
        generating.set(generatingCount);
        done.set(doneCount);
        failed.set(failedCount);
    }
}
