package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.shiroha233.roadweaver.config.PathfindingConfig
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.runtime.ThreadPoolManager
import java.util.*
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal object BasicAStarPathfinder {
    private const val BIOME_BASE_COST = 12 // 特定生物群系基础成本（河流/海洋/深海）
    private const val HEURISTIC_EPSILON = 0.2 // 启发式 epsilon

    /**
     * 基础 A* 寻路算法
     *
     * @param startGround 起点
     * @param endGround   终点
     * @param width       道路宽度
     * @param level       服务端世界
     * @param maxSteps    最大步数
     * @param cache       地形采样缓存
     * @param cfg         寻路配置快照（不可变）
     */
    @JvmStatic
    fun calculateLandPath(
        startGround: BlockPos,
        endGround: BlockPos,
        width: Int,
        level: ServerLevel,
        maxSteps: Int,
        cache: TerrainSamplingCache,
        cfg: PathfindingConfig
    ): List<Records.RoadSegmentPlacement>? {
        val openSet = PriorityQueue<Node>(compareBy { it.f })
        val allNodes = HashMap<BlockPos, Node>()
        val closed = HashSet<BlockPos>()

        val startNode = Node(startGround, null, 0.0, heuristic(startGround, endGround, cfg))
        openSet.add(startNode)
        allNodes[startGround] = startNode

        val d = cfg.effectiveAStarStep()
        val neighborOffsets = arrayOf(
            intArrayOf(d, 0), intArrayOf(-d, 0), intArrayOf(0, d), intArrayOf(0, -d),
            intArrayOf(d, d), intArrayOf(d, -d), intArrayOf(-d, d), intArrayOf(-d, -d)
        )

        var stepsBudget = max(1, maxSteps)
        val dutyCycle = cfg.threadDutyCycle()
        ThreadPoolManager.resetThrottle()
        try {
            while (openSet.isNotEmpty() && stepsBudget-- > 0) {
                ThreadPoolManager.throttle(dutyCycle)
                if (Thread.currentThread().isInterrupted) {
                    return null
                }
                val current = openSet.poll() ?: break

                if (manhattan2d(current.pos, endGround) < d * 2) {
                    val rawPath = ArrayList<BlockPos>()
                    var c: Node? = current
                    while (c != null) {
                        rawPath.add(c.pos)
                        c = c.parent
                    }
                    rawPath.reverse()
                    return PathPostProcessor.process(rawPath, width, level, cache, cfg.bridgeMinWaterDepth())
                }

                closed.add(current.pos)
                allNodes.remove(current.pos)

                for (off in neighborOffsets) {
                    if (Thread.currentThread().isInterrupted) {
                        return null
                    }
                    val nxz = current.pos.offset(off[0], 0, off[1])
                    val y = RoadPathCalculator.heightSampler(cache, nxz.x, nxz.z, level)
                    val np = BlockPos(nxz.x, y, nxz.z)
                    if (closed.contains(np)) continue

                    val biome = cache.getBiome(level, np.x, np.z)
                    val biomeCost = if (biome.`is`(BiomeTags.IS_RIVER) || biome.`is`(BiomeTags.IS_OCEAN)
                        || biome.`is`(BiomeTags.IS_DEEP_OCEAN)
                    ) BIOME_BASE_COST else 0
                    val elevation = abs(y - current.pos.y)

                    val offsetSum = abs(abs(off[0])) + abs(off[1])
                    val stepCost = if (offsetSum == 2 * d) cfg.diagStepCost() else cfg.orthoStepCost()
                    val stabilityCost = RoadPathCalculator.calculateTerrainStability(cache, np, y, level, d)
                    val sea = level.seaLevel
                    val waterColumn = RoadPathCalculator.isColumnWater(cache, nxz.x, nxz.z, level)
                    val nearWater = RoadPathCalculator.isNearWaterLike(cache, nxz.x, nxz.z, level)
                    val oceanFloor = RoadPathCalculator.oceanFloorSampler(cache, nxz.x, nxz.z, level)
                    val waterDepth = max(0, sea - oceanFloor)
                    val waterDepthCost = if (waterColumn) (waterDepth * cfg.waterDepthWeight()).toInt() else 0
                    val nearWaterCost = if (nearWater) cfg.nearWaterCost().toInt() else 0

                    val deviation = deviation2d(np, startGround, endGround)
                    val deviationCost = deviation * cfg.deviationWeight() / max(1.0, d.toDouble())

                    val tentativeG = current.g +
                            stepCost +
                            elevation * cfg.elevationWeight() +
                            biomeCost * cfg.biomeWeight() +
                            stabilityCost * cfg.stabilityWeight() +
                            waterDepthCost +
                            nearWaterCost +
                            deviationCost

                    val existingNode = allNodes[np]
                    if (existingNode == null || tentativeG < existingNode.g) {
                        val h = heuristic(np, endGround, cfg)
                        val fWeighted = tentativeG + (1.0 + HEURISTIC_EPSILON) * h
                        val newNode = Node(np, current, tentativeG, fWeighted)
                        allNodes[np] = newNode
                        openSet.add(newNode)
                    }
                }
            }
            return null
        } finally {
            // 显式清理，帮助 GC 回收 Node 链表
            openSet.clear()
            allNodes.clear()
            closed.clear()
            // 清理 ThreadLocal，防止线程池复用导致内存泄漏
            ThreadPoolManager.clearThrottle()
        }
    }

    private fun manhattan2d(a: BlockPos, b: BlockPos): Int {
        return abs(a.x - b.x) + abs(a.z - b.z)
    }

    private fun heuristic(a: BlockPos, b: BlockPos, cfg: PathfindingConfig): Double {
        val dx = a.x - b.x
        val dz = a.z - b.z
        val dxzApprox = abs(dx) + abs(dz) - 0.6 * min(abs(dx), abs(dz))
        return dxzApprox * cfg.heuristicWeight()
    }

    private fun deviation2d(p: BlockPos, a: BlockPos, b: BlockPos): Double {
        val ax = a.x.toDouble()
        val az = a.z.toDouble()
        val bx = b.x.toDouble()
        val bz = b.z.toDouble()
        val px = p.x.toDouble()
        val pz = p.z.toDouble()
        val num = abs((bz - az) * px - (bx - ax) * pz + bx * az - bz * ax)
        val den = hypot(bx - ax, bz - az)
        if (den <= 0.0) return 0.0
        return num / den
    }

    private class Node(
        val pos: BlockPos,
        val parent: Node?,
        val g: Double,
        val f: Double
    )
}
