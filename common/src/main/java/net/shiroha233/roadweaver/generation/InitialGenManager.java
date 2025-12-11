package net.shiroha233.roadweaver.generation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.structures.placement.SpawnCabinPlacer;

import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.RoadGenerationConfig;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static net.shiroha233.roadweaver.planning.PlanningUtils.sameEdge;

/**
 * 初始道路生成管理器：在服务器启动后，阻塞直到初始规划范围内的道路生成完成，并提供进度统计。
 */
public final class InitialGenManager {
    private InitialGenManager() {
    }

    private static volatile boolean active;
    // 幂等性标志：确保 begin() 只执行一次（防止 Mixin 和事件钩子重复调用）
    private static volatile boolean initialized;
    
    private static final AtomicInteger total = new AtomicInteger(0);
    private static final AtomicInteger done = new AtomicInteger(0);
    private static final AtomicInteger generating = new AtomicInteger(0);
    private static final AtomicInteger failed = new AtomicInteger(0);

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
     * 此方法是幂等的，多次调用只会执行一次。
     */
    public static synchronized void begin(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension()))
            return;
        // 幂等性检查：防止 Mixin 和事件钩子重复调用
        if (initialized) {
            return;
        }
        initialized = true;
        // 清零状态
        active = true;
        total.set(0);
        done.set(0);

        generating.set(0);
        failed.set(0);

        // 确保生成线程池已初始化
        RoadGenerationService.onServerStarted();

        // 发现并缓存所有结构和标签（供结构选择 GUI 使用）
        net.shiroha233.roadweaver.config.structure.StructureDiscoveryService.discoverFromLevel(level);

        // 首开世界：按配置尝试放置出生点小屋（幂等）
        if (ConfigService.get().spawnCabinEnabled()) {
            SpawnCabinPlacer.ensurePlaced(level);
        }

        // 进行初始规划：写入结构连接（PLANNED）
        RoadPlanningService.initialPlan(level);

        // 统计总数
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> conns = provider.getStructureConnections(level);
        total.set((conns == null) ? 0 : conns.size());
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
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> list = provider.getStructureConnections(level);

        if (list != null && !list.isEmpty()) {
            // 筛选出需要生成的任务
            List<Records.StructureConnection> tasks = new ArrayList<>();
            for (Records.StructureConnection c : list) {
                if (c.status() == Records.ConnectionStatus.PLANNED) {
                    tasks.add(c);
                }
            }

            if (!tasks.isEmpty()) {
                // 在入口层获取配置快照，避免在多线程中重复读取
                ModConfig modCfg = ConfigService.get();
                RoadGenerationConfig genCfg = RoadGenerationConfig.from(modCfg);
                int maxSteps = modCfg.aStarMaxSteps();
                
                // 使用统一线程池管理器
                ExecutorService executor = ThreadPoolManager.initialGenExecutor();
                List<Future<?>> futures = new ArrayList<>();

                for (Records.StructureConnection task : tasks) {
                    futures.add(executor.submit(() -> {
                        // 更新状态为生成中
                        generating.incrementAndGet();

                        // 使用配置快照，避免在热路径中访问全局单例
                        boolean success = RoadGenerationService.generateTask(level, task, genCfg, maxSteps);

                        generating.decrementAndGet();
                        if (success) {
                            done.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                        }
                        return new AbstractMap.SimpleEntry<>(task, success);
                    }));
                }

                // 等待所有任务完成
                // 收集结果用于批量更新
                Map<Records.StructureConnection, Boolean> results = new HashMap<>();
                for (Future<?> f : futures) {
                    try {
                        @SuppressWarnings("unchecked")
                        var entry = (Map.Entry<Records.StructureConnection, Boolean>) f.get();
                        results.put(entry.getKey(), entry.getValue());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // 批量更新 WorldDataProvider
                List<Records.StructureConnection> currentList = provider.getStructureConnections(level);
                if (currentList != null) {
                    List<Records.StructureConnection> updatedList = new ArrayList<>(currentList);
                    boolean changed = false;
                    for (int i = 0; i < updatedList.size(); i++) {
                        Records.StructureConnection original = updatedList.get(i);
                        for (Map.Entry<Records.StructureConnection, Boolean> entry : results.entrySet()) {
                            Records.StructureConnection task = entry.getKey();
                            if (sameEdge(original, task)) {
                                Records.ConnectionStatus newStatus = entry.getValue()
                                        ? Records.ConnectionStatus.COMPLETED
                                        : Records.ConnectionStatus.FAILED;
                                updatedList.set(i,
                                        new Records.StructureConnection(original.from(), original.to(), newStatus));
                                changed = true;
                                break;
                            }
                        }
                    }
                    if (changed) {
                        provider.setStructureConnections(level, updatedList);
                    }
                }
            }
        }
        // 确保道路数据刷新到存储，以便树木生成时可以查询
        RoadShardStorage.flushAll(level);
        // 清除道路位置查询缓存，避免过时缓存导致树木阻止失效
        RoadPositionQuery.clearCache(level);
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
        if (conns == null) {
            total.set(0);

            generating.set(0);
            done.set(0);
            failed.set(0);
            return;
        }
        int g = 0, c = 0, f = 0;
        for (Records.StructureConnection sc : conns) {
            Records.ConnectionStatus s = sc.status();
            if (s == Records.ConnectionStatus.GENERATING)
                g++;
            else if (s == Records.ConnectionStatus.COMPLETED)
                c++;
            else if (s == Records.ConnectionStatus.FAILED)
                f++;
        }
        total.set(conns.size());

        generating.set(g);
        done.set(c);
        failed.set(f);
    }

    /**
     * 重置初始化状态（服务器停止时调用，确保下次启动可以正常工作）
     */
    public static void reset() {
        active = false;
        initialized = false;
        total.set(0);
        done.set(0);
        generating.set(0);
        failed.set(0);
    }
}
