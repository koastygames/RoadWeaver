package net.shiroha233.roadweaver.generation

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.config.RoadGenerationConfig
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.RoadPositionQuery
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage
import net.shiroha233.roadweaver.planning.PlanningUtils
import net.shiroha233.roadweaver.planning.RoadPlanningService
import net.shiroha233.roadweaver.runtime.ThreadPoolManager
import net.shiroha233.roadweaver.structures.placement.SpawnCabinPlacer
import java.util.AbstractMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger

/**
 * 初始道路生成管理器：在服务器启动后，阻塞直到初始规划范围内的道路生成完成，并提供进度统计。
 */
object InitialGenManager {
    @Volatile
    private var active = false

    // 幂等性标志：确保 begin() 只执行一次（防止 Mixin 和事件钩子重复调用）
    @Volatile
    private var initialized = false

    private val total = AtomicInteger(0)
    private val done = AtomicInteger(0)
    private val generating = AtomicInteger(0)
    private val failed = AtomicInteger(0)

    @JvmStatic
    fun isActive(): Boolean = active

    @JvmStatic
    fun getTotal(): Int = total.get()

    @JvmStatic
    fun getDone(): Int = done.get()

    @JvmStatic
    fun getGenerating(): Int = generating.get()

    @JvmStatic
    fun getFailed(): Int = failed.get()

    /**
     * 在服务器启动时调用：执行初始规划并计算总任务数。
     * 此方法是幂等的，多次调用只会执行一次。
     */
    @JvmStatic
    @Synchronized
    fun begin(level: ServerLevel?) {
        if (level == null || Level.OVERWORLD != level.dimension()) return

        if (initialized) return
        initialized = true

        // 清零状态
        active = true
        total.set(0)
        done.set(0)
        generating.set(0)
        failed.set(0)

        // 重置地形采样统计（用于 GUI 显示缓存命中率和每秒采样数）
        net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.TerrainSamplingStats.reset()

        // 发现并缓存所有结构和标签（供结构选择 GUI 使用）
        net.shiroha233.roadweaver.config.structure.StructureDiscoveryService.discoverFromLevel(level)

        // 首开世界：按配置尝试放置出生点小屋（幂等）
        if (ConfigService.get().spawnCabinEnabled()) {
            SpawnCabinPlacer.ensurePlaced(level)
        }

        // 进行初始规划：写入结构连接（PLANNED）
        RoadPlanningService.initialPlan(level)

        // 统计总数
        val provider = WorldDataProvider.getInstance()
        val conns = provider.getStructureConnections(level)
        total.set(conns?.size ?: 0)

        // 初始化一次完成度
        update(level)
    }

    /**
     * 循环推进生成并阻塞直到全部完成或总数为0。
     * 注意：在服务器启动线程中调用，期间不会触发常规 tick。
     * 改为多线程并行生成以提高速度。
     */
    @JvmStatic
    fun blockUntilDone(level: ServerLevel) {
        if (!active) return

        val provider = WorldDataProvider.getInstance()
        val list = provider.getStructureConnections(level)

        if (!list.isNullOrEmpty()) {
            val tasks = ArrayList<Records.StructureConnection>()
            for (c in list) {
                if (c.status == Records.ConnectionStatus.PLANNED) {
                    tasks.add(c)
                }
            }

            if (tasks.isNotEmpty()) {
                // 在入口层获取配置快照，避免在多线程中重复读取
                val modCfg: ModConfig = ConfigService.get()
                val genCfg = RoadGenerationConfig.from(modCfg)
                val maxSteps = modCfg.aStarMaxSteps()

                val executor: ExecutorService = ThreadPoolManager.initialGenExecutor()
                val futures = ArrayList<Future<*>>()

                for (task in tasks) {
                    futures.add(
                        executor.submit(Callable {
                            generating.incrementAndGet()

                            val success = RoadGenerationService.generateTask(level, task, genCfg, maxSteps)

                            generating.decrementAndGet()
                            if (success) done.incrementAndGet() else failed.incrementAndGet()

                            AbstractMap.SimpleEntry(task, success)
                        })
                    )
                }

                // 等待所有任务完成
                val results = HashMap<Records.StructureConnection, Boolean>()
                for (f in futures) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val entry = f.get() as? Map.Entry<Records.StructureConnection, Boolean>
                        if (entry != null) {
                            results[entry.key] = entry.value
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 批量更新 WorldDataProvider
                val currentList = provider.getStructureConnections(level)
                if (currentList != null) {
                    val updatedList = ArrayList(currentList)
                    var changed = false

                    for (i in updatedList.indices) {
                        val original = updatedList[i]
                        for ((task, ok) in results.entries) {
                            if (PlanningUtils.sameEdge(original, task)) {
                                val newStatus = if (ok) Records.ConnectionStatus.COMPLETED else Records.ConnectionStatus.FAILED
                                updatedList[i] = Records.StructureConnection(original.from, original.to, newStatus)
                                changed = true
                                break
                            }
                        }
                    }

                    if (changed) {
                        provider.setStructureConnections(level, updatedList)
                    }
                }
            }
        }

        // 确保道路数据刷新到存储，以便树木生成时可以查询
        RoadShardStorage.flushAll(level)
        // 清除道路位置查询缓存，避免过时缓存导致树木阻止失效
        RoadPositionQuery.clearCache(level)
        active = false
    }

    /**
     * 读取世界数据统计完成数量。
     * 注意：在多线程生成期间，此方法可能不会反映实时进度（因为我们只更新了 AtomicInteger，没有更新 WorldData），
     * 但 UI 读取的是 AtomicInteger，所以 UI 是实时的。
     * 生成结束后，再次调用此方法会从 WorldData 同步最终状态。
     */
    @JvmStatic
    fun update(level: ServerLevel) {
        if (active) return

        val provider = WorldDataProvider.getInstance()
        val conns = provider.getStructureConnections(level)
        if (conns.isNullOrEmpty()) {
            total.set(0)
            generating.set(0)
            done.set(0)
            failed.set(0)
            return
        }

        var g = 0
        var c = 0
        var f = 0
        for (sc in conns) {
            when (sc.status) {
                Records.ConnectionStatus.GENERATING -> g++
                Records.ConnectionStatus.COMPLETED -> c++
                Records.ConnectionStatus.FAILED -> f++
                else -> Unit
            }
        }

        total.set(conns.size)
        generating.set(g)
        done.set(c)
        failed.set(f)
    }

    /**
     * 重置初始化状态（服务器停止时调用，确保下次启动可以正常工作）
     */
    @JvmStatic
    fun reset() {
        active = false
        initialized = false
        total.set(0)
        done.set(0)
        generating.set(0)
        failed.set(0)
    }
}
