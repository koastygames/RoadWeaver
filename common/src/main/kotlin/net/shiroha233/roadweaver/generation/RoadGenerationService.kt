package net.shiroha233.roadweaver.generation

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.config.RoadGenerationConfig
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig
import net.shiroha233.roadweaver.features.path.pathlogic.core.Road
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.planning.PlanningUtils
import net.shiroha233.roadweaver.planning.RoadPlanningService
import net.shiroha233.roadweaver.runtime.ThreadPoolManager
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

object RoadGenerationService {
    // 生命周期由中央管理器管理
    private val QUEUES = ConcurrentHashMap<ServerLevel, ConcurrentLinkedQueue<Records.StructureConnection>>()
    private val PROCESSED = ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, Boolean>>()
    private val RUNNING_COUNT = ConcurrentHashMap<ServerLevel, AtomicInteger>()
    private val ALL_RUNNING: MutableSet<Future<*>> = ConcurrentHashMap.newKeySet()

    private val ROAD_CF_ID = ResourceLocation("roadweaver", "road_feature")

    @JvmStatic
    fun onServerStarted() {
        // 兼容旧调用点：初始化生成服务的内部状态。
        // 具体每个维度的任务装载由 tick(level)->refreshQueue(level) 在维度首次 tick 时完成，
        // 避免在 common 层依赖“枚举所有已加载世界”的平台 API。
        QUEUES.clear()
        PROCESSED.clear()
        RUNNING_COUNT.clear()
        ALL_RUNNING.clear()
    }

    @JvmStatic
    fun onServerStopping() {
        ALL_RUNNING.forEach { it.cancel(true) }
        ALL_RUNNING.clear()
        QUEUES.clear()
        PROCESSED.clear()
        RUNNING_COUNT.clear()
        RoadPlanningService.resetAll()
        InitialGenManager.reset() // 重置初始化状态，确保下次启动正常
    }

    private fun ensureLevelState(level: ServerLevel) {
        QUEUES.computeIfAbsent(level) { ConcurrentLinkedQueue() }
        PROCESSED.computeIfAbsent(level) { ConcurrentHashMap() }
        RUNNING_COUNT.computeIfAbsent(level) { AtomicInteger(0) }
    }

    /**
     * 执行单个生成任务（无副作用，不更新全局状态）。
     *
     * @param level 服务端世界
     * @param conn  结构连接
     * @return true if success, false if failed
     */
    @JvmStatic
    fun generateTask(level: ServerLevel?, conn: Records.StructureConnection?): Boolean {
        if (level === null || conn === null) return false

        // 在入口层获取配置快照
        val modCfg: ModConfig = ConfigService.get()
        val genCfg = RoadGenerationConfig.from(modCfg)
        return generateTask(level, conn, genCfg, modCfg.aStarMaxSteps())
    }

    /**
     * 执行单个生成任务（带配置快照）。
     *
     * @param level    服务端世界
     * @param conn     结构连接
     * @param genCfg   道路生成配置快照
     * @param maxSteps 最大寻路步数
     * @return true if success, false if failed
     */
    @JvmStatic
    fun generateTask(
        level: ServerLevel?,
        conn: Records.StructureConnection?,
        genCfg: RoadGenerationConfig,
        maxSteps: Int
    ): Boolean {
        if (level === null || conn === null) return false

        return try {
            if (Thread.currentThread().isInterrupted) return false

            // Feature 配置
            val reg = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
            val cf: ConfiguredFeature<*, *>? = reg[ROAD_CF_ID]
            val featureCfg: PathFeatureConfig = if (cf !== null && cf.config() is PathFeatureConfig) {
                cf.config() as PathFeatureConfig
            } else {
                PathFeatureConfig()
            }

            // 生成
            if (Thread.currentThread().isInterrupted) return false

            Road(level, conn, featureCfg, genCfg).generateRoad(maxSteps)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * 同步生成，用于世界生成前的阻塞阶段（单线程）。
     *
     * @deprecated Use
     *             {@link #generateTask(ServerLevel, Records.StructureConnection)}
     *             managed by InitialGenManager instead.
     */
    @Deprecated("Use generateTask managed by InitialGenManager instead")
    @JvmStatic
    fun generateInline(level: ServerLevel?, conn: Records.StructureConnection?) {
        if (level === null || conn === null) return

        val provider = WorldDataProvider.getInstance()
        try {
            if (Thread.currentThread().isInterrupted) return

            // 标记为 GENERATING
            val origin0 = provider.getStructureConnections(level)
            val all0 = if (origin0 !== null) ArrayList(origin0) else ArrayList()
            for (i in all0.indices) {
                val c = all0[i]
                if (PlanningUtils.sameEdge(c, conn)) {
                    all0[i] = Records.StructureConnection(c.from, c.to, Records.ConnectionStatus.GENERATING)
                }
            }
            if (all0.isNotEmpty()) {
                provider.setStructureConnections(level, all0)
            }

            // 立即刷新一次统计，让加载界面能显示"生成中"数量
            InitialGenManager.update(level)

            try {
                Thread.sleep(10)
            } catch (ignored: InterruptedException) {
            }

            if (generateTask(level, conn)) {
                // 标记 COMPLETED
                val origin = provider.getStructureConnections(level)
                val all = if (origin != null) ArrayList(origin) else ArrayList()
                for (i in all.indices) {
                    val c = all[i]
                    if (PlanningUtils.sameEdge(c, conn)) {
                        all[i] = Records.StructureConnection(c.from, c.to, Records.ConnectionStatus.COMPLETED)
                    }
                }
                provider.setStructureConnections(level, all)
                val k = PlanningUtils.edgeKey(conn.from, conn.to)
                val proc = PROCESSED[level]
                proc?.remove(k)
            } else {
                // 标记 FAILED
                val origin = provider.getStructureConnections(level)
                val all = if (origin != null) ArrayList(origin) else ArrayList()
                for (i in all.indices) {
                    val c = all[i]
                    if (PlanningUtils.sameEdge(c, conn)) {
                        all[i] = Records.StructureConnection(c.from, c.to, Records.ConnectionStatus.FAILED)
                    }
                }
                provider.setStructureConnections(level, all)
                val k = PlanningUtils.edgeKey(conn.from, conn.to)
                val proc = PROCESSED[level]
                proc?.remove(k)
            }
        } catch (t: Throwable) {
            // 标记 FAILED
            val origin = provider.getStructureConnections(level)
            val all = if (origin != null) ArrayList(origin) else ArrayList()
            for (i in all.indices) {
                val c = all[i]
                if (PlanningUtils.sameEdge(c, conn)) {
                    all[i] = Records.StructureConnection(c.from, c.to, Records.ConnectionStatus.FAILED)
                }
            }
            provider.setStructureConnections(level, all)
            val k = PlanningUtils.edgeKey(conn.from, conn.to)
            val proc = PROCESSED[level]
            proc?.remove(k)
        }
    }

    @JvmStatic
    fun tick(level: ServerLevel) {
        refreshQueue(level)
        ALL_RUNNING.removeIf { f -> f === null || f.isDone || f.isCancelled }

        val q = QUEUES.computeIfAbsent(level) { ConcurrentLinkedQueue() }
        if (q.isEmpty()) return

        val limit = maxOf(1, ConfigService.get().maxConcurrentGenerations())
        val cnt = RUNNING_COUNT.computeIfAbsent(level) { AtomicInteger(0) }

        val players = ArrayList<ServerPlayer>()
        for (p in level.server.playerList.players) {
            if (p !== null && p.serverLevel() == level) {
                players.add(p)
            }
        }

        val sample = maxOf(64, limit * 8)

        while (cnt.get() < limit) {
            val conn = pollNearest(q, players, sample) ?: break

            // 标记为 GENERATING
            run {
                val provider = WorldDataProvider.getInstance()
                val origin = provider.getStructureConnections(level)
                val all = if (origin != null) ArrayList(origin) else ArrayList()
                for (i in all.indices) {
                    val c = all[i]
                    if (PlanningUtils.sameEdge(c, conn)) {
                        all[i] = Records.StructureConnection(c.from, c.to, Records.ConnectionStatus.GENERATING)
                    }
                }
                if (all.isNotEmpty()) {
                    provider.setStructureConnections(level, all)
                }
            }

            val task = conn
            cnt.incrementAndGet()
            val epoch = ThreadPoolManager.currentEpoch()

            val fut = ThreadPoolManager.generationExecutor().submit {
                try {
                    if (Thread.currentThread().isInterrupted) return@submit
                    if (!ThreadPoolManager.isEpoch(epoch)) return@submit

                    safeGenerate(level, task, epoch)
                } finally {
                    cnt.decrementAndGet()
                }
            }
            ALL_RUNNING.add(fut)
        }
    }

    private fun refreshQueue(level: ServerLevel) {
        val provider = WorldDataProvider.getInstance()
        val list = provider.getStructureConnections(level) ?: return

        val q = QUEUES.computeIfAbsent(level) { ConcurrentLinkedQueue() }
        val proc = PROCESSED.computeIfAbsent(level) { ConcurrentHashMap() }

        for (c in list) {
            val key = PlanningUtils.edgeKey(c.from, c.to)
            if (proc.putIfAbsent(key, true) != null) continue

            if (c.status != Records.ConnectionStatus.PLANNED && c.status != Records.ConnectionStatus.GENERATING) {
                continue
            }
            q.add(c)
        }
    }

    private fun safeGenerate(level: ServerLevel, conn: Records.StructureConnection, epoch: Long) {
        try {
            if (Thread.currentThread().isInterrupted) return
            if (!ThreadPoolManager.isEpoch(epoch)) return

            val success = generateTask(level, conn)

            // 更新状态
            val provider = WorldDataProvider.getInstance()
            val origin = provider.getStructureConnections(level)
            val all = if (origin != null) ArrayList(origin) else ArrayList()

            for (i in all.indices) {
                val c = all[i]
                if (PlanningUtils.sameEdge(c, conn)) {
                    val newStatus = if (success) {
                        Records.ConnectionStatus.COMPLETED
                    } else {
                        Records.ConnectionStatus.FAILED
                    }
                    all[i] = Records.StructureConnection(c.from, c.to, newStatus)
                }
            }

            if (all.isNotEmpty()) {
                provider.setStructureConnections(level, all)
            }

            val k = PlanningUtils.edgeKey(conn.from, conn.to)
            val proc = PROCESSED[level]
            proc?.remove(k)
        } catch (t: Throwable) {
            // 异常情况下标记为 FAILED
            val provider = WorldDataProvider.getInstance()
            val origin = provider.getStructureConnections(level)
            val all = if (origin != null) ArrayList(origin) else ArrayList()

            for (i in all.indices) {
                val c = all[i]
                if (PlanningUtils.sameEdge(c, conn)) {
                    all[i] = Records.StructureConnection(c.from, c.to, Records.ConnectionStatus.FAILED)
                }
            }

            if (all.isNotEmpty()) {
                provider.setStructureConnections(level, all)
            }

            val k = PlanningUtils.edgeKey(conn.from, conn.to)
            val proc = PROCESSED[level]
            proc?.remove(k)
        }
    }

    private fun pollNearest(
        queue: ConcurrentLinkedQueue<Records.StructureConnection>,
        players: List<ServerPlayer>,
        sample: Int
    ): Records.StructureConnection? {
        if (players.isEmpty()) return queue.poll()

        val candidates = ArrayList<Records.StructureConnection>()
        var count = 0

        // 采样最多 sample 个连接
        while (count < sample) {
            val conn = queue.poll() ?: break
            candidates.add(conn)
            count++
        }

        if (candidates.isEmpty()) return null

        // 找到离玩家最近的连接
        var best: Records.StructureConnection? = null
        var minDistSq = Double.MAX_VALUE

        for (conn in candidates) {
            val distSq = calculateMinDistanceToPlayers(conn, players)
            if (distSq < minDistSq) {
                minDistSq = distSq
                best = conn
            }
        }

        // 将未选中的连接放回队列
        for (conn in candidates) {
            if (conn !== best) {
                queue.offer(conn)
            }
        }

        return best
    }

    private fun calculateMinDistanceToPlayers(
        conn: Records.StructureConnection,
        players: List<ServerPlayer>
    ): Double {
        var minDistSq = Double.MAX_VALUE

        for (player in players) {
            val playerPos = player.blockPosition()
            val dist1 = playerPos.distSqr(conn.from)
            val dist2 = playerPos.distSqr(conn.to)
            val minDist = minOf(dist1, dist2)

            if (minDist < minDistSq) {
                minDistSq = minDist
            }
        }

        return minDistSq
    }

    
}
