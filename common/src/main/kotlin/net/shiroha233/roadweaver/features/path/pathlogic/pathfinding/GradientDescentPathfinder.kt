package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.shiroha233.roadweaver.config.PathfindingConfig
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.runtime.ThreadPoolManager
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 基于梯度下降（流体模拟）的寻路算法。
 * 实质是限制区域的 Dijkstra 算法（无启发式 A*），模拟水流蔓延寻找绝对最小阻力路径。
 * 特点：
 * 1. 能够找到绕过高山的平缓路径，而不是翻山越岭。
 * 2. 路径极其自然，贴合地形等高线。
 * 3. 限制搜索范围以保证性能。
 */
internal object GradientDescentPathfinder {
    private const val BIOME_BASE_COST = 12
    private const val SEARCH_BUFFER = 64 // 搜索边界缓冲
    private const val WATER_COLUMN_BASE_PENALTY = 800.0
    private const val WATER_DEPTH_SQUARED_WEIGHT = 2.0
    private const val NEAR_WATER_COST_MULTIPLIER = 4.0

    /**
     * 梯度下降寻路算法
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
    fun calculatePath(
        startGround: BlockPos,
        endGround: BlockPos,
        width: Int,
        level: ServerLevel,
        maxSteps: Int,
        cache: TerrainSamplingCache,
        cfg: PathfindingConfig
    ): List<Records.RoadSegmentPlacement>? {

        // 1. 定义搜索边界 (Bounding Box)
        // 即使有了启发式，保留边界检查也是个好习惯，防止跑太远
        val manhattan = manhattan2d(startGround, endGround)
        val dynamicBuffer = min(512, max(SEARCH_BUFFER, manhattan / 4))
        val minX = min(startGround.x, endGround.x) - dynamicBuffer
        val maxX = max(startGround.x, endGround.x) + dynamicBuffer
        val minZ = min(startGround.z, endGround.z) - dynamicBuffer
        val maxZ = max(startGround.z, endGround.z) + dynamicBuffer

        // A* 需要比较 f_cost = g_cost + h_cost
        val openSet = PriorityQueue<Node>(compareBy { it.fCost })
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

        // 既然有了启发式，步数预算可以稍微收紧，或者保持不变以支持长距离绕行
        // 但为了防止无解时的死循环，还是保留限制
        var stepsBudget = max(5000, maxSteps * 3)

        val dutyCycle = cfg.threadDutyCycle()
        ThreadPoolManager.resetThrottle() // 重置节流计时器
        try {
            while (openSet.isNotEmpty() && stepsBudget-- > 0) {
                ThreadPoolManager.throttle(dutyCycle) // 根据占空比控制CPU使用率
                if (Thread.currentThread().isInterrupted) return null

                val current = openSet.poll() ?: break

                // 找到终点（或非常接近）
                if (manhattan2d(current.pos, endGround) < (d * 1.5).toInt()) {
                    return reconstructPath(current, width, level, cache, cfg.bridgeMinWaterDepth())
                }

                closed.add(current.pos)

                for (off in neighborOffsets) {
                    val nxz = current.pos.offset(off[0], 0, off[1])

                    // 边界检查
                    if (nxz.x < minX || nxz.x > maxX || nxz.z < minZ || nxz.z > maxZ) continue

                    val y = RoadPathCalculator.heightSampler(cache, nxz.x, nxz.z, level)
                    val np = BlockPos(nxz.x, y, nxz.z)

                    if (closed.contains(np)) continue

                    // --- 代价计算 ---
                    val biome = cache.getBiome(level, np.x, np.z)
                    val biomeCost = if (biome.`is`(BiomeTags.IS_RIVER) || biome.`is`(BiomeTags.IS_OCEAN)
                        || biome.`is`(BiomeTags.IS_DEEP_OCEAN)
                    ) (BIOME_BASE_COST * 4) else 0
                    val elevation = abs(y - current.pos.y)

                    val offsetSum = abs(abs(off[0])) + abs(off[1])
                    val stepCost = if (offsetSum == 2 * d) cfg.diagStepCost() else cfg.orthoStepCost()
                    val stabilityCost = RoadPathCalculator.calculateTerrainStability(cache, np, y, level, d)
                    val sea = level.seaLevel
                    val waterColumn = RoadPathCalculator.isColumnWater(cache, nxz.x, nxz.z, level)
                    val nearWater = RoadPathCalculator.isNearWaterLike(cache, nxz.x, nxz.z, level)
                    val oceanFloor = RoadPathCalculator.oceanFloorSampler(cache, nxz.x, nxz.z, level)
                    val waterDepth = max(0, sea - oceanFloor)
                    var waterDepthPenalty = 0.0
                    if (waterColumn) {
                        val w = max(0.0, cfg.waterDepthWeight())
                        waterDepthPenalty = WATER_COLUMN_BASE_PENALTY +
                                (waterDepth * waterDepth.toDouble()) * w * WATER_DEPTH_SQUARED_WEIGHT
                    }
                    val nearWaterPenalty = if (nearWater) (cfg.nearWaterCost() * NEAR_WATER_COST_MULTIPLIER) else 0.0

                    var elevationCost = elevation * elevation * cfg.elevationWeight()
                    // 坡度阻断
                    val slope = elevation.toDouble() / max(1, d)
                    if (slope > 0.5) elevationCost += 800.0 * slope
                    if (slope > 0.8) elevationCost += 8000.0

                    val gCost = current.gCost +
                            stepCost +
                            elevationCost +
                            biomeCost * cfg.biomeWeight() +
                            stabilityCost * cfg.stabilityWeight() +
                            waterDepthPenalty +
                            nearWaterPenalty

                    // 关键改动：加入启发式，但保持流体特性（无 deviation 惩罚）
                    val hCost = heuristic(np, endGround, cfg)
                    val fCost = gCost + hCost

                    val existingNode = allNodes[np]
                    if (existingNode == null || gCost < existingNode.gCost) {
                        val newNode = Node(np, current, gCost, fCost)
                        allNodes[np] = newNode
                        openSet.add(newNode)
                    }
                }
            }
        } finally {
            // 显式清理引用，帮助 GC
            openSet.clear()
            allNodes.clear()
            closed.clear()
            // 清理 ThreadLocal，防止线程池复用导致内存泄漏
            ThreadPoolManager.clearThrottle()
        }
        return null
    }

    private fun reconstructPath(
        endNode: Node,
        width: Int,
        level: ServerLevel,
        cache: TerrainSamplingCache,
        bridgeMinWaterDepth: Int
    ): List<Records.RoadSegmentPlacement> {
        val rawPath = ArrayList<BlockPos>()
        var c: Node? = endNode
        while (c !== null) {
            rawPath.add(c.pos)
            c = c.parent
        }
        rawPath.reverse()
        return PathPostProcessor.process(rawPath, width, level, cache, bridgeMinWaterDepth)
    }

    private fun manhattan2d(a: BlockPos, b: BlockPos): Int {
        return abs(a.x - b.x) + abs(a.z - b.z)
    }

    private fun heuristic(a: BlockPos, b: BlockPos, cfg: PathfindingConfig): Double {
        // 使用欧几里得距离，给予更平滑的方向指引
        val dx = (a.x - b.x).toDouble()
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dz * dz) * cfg.heuristicWeight()
    }

    private class Node(
        val pos: BlockPos,
        val parent: Node?,
        val gCost: Double, // 实际行走代价
        val fCost: Double  // gCost + heuristic
    )
}
