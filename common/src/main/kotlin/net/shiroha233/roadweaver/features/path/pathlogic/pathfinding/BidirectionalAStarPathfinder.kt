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

/**
 * 双向 A* 寻路：从起点和终点同时扩展搜索，
 * 在中间相遇后重建完整路径，以减少节点展开数量。
 */
internal object BidirectionalAStarPathfinder {
    private const val BIOME_BASE_COST = 12 // 特定生物群系基础成本（河流/海洋/深海）
    private const val HEURISTIC_EPSILON = 0.2 // 启发式 epsilon

    /**
     * 双向 A* 寻路算法
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
        // 特殊情况：起终点非常接近时无需复杂寻路
        if (startGround.equals(endGround)) {
            return emptyList()
        }

        val d = cfg.effectiveAStarStep()
        val neighborOffsets = arrayOf(
            intArrayOf(d, 0), intArrayOf(-d, 0), intArrayOf(0, d), intArrayOf(0, -d),
            intArrayOf(d, d), intArrayOf(d, -d), intArrayOf(-d, d), intArrayOf(-d, -d)
        )

        val openF = PriorityQueue<Node>(compareBy { it.f })
        val openB = PriorityQueue<Node>(compareBy { it.f })
        val nodesF = HashMap<BlockPos, Node>()
        val nodesB = HashMap<BlockPos, Node>()
        val closedF = HashSet<BlockPos>()
        val closedB = HashSet<BlockPos>()

        val startNode = Node(startGround, null, 0.0, heuristic(startGround, endGround, cfg))
        val endNode = Node(endGround, null, 0.0, heuristic(endGround, startGround, cfg))
        openF.add(startNode)
        nodesF[startGround] = startNode
        openB.add(endNode)
        nodesB[endGround] = endNode

        var stepsBudget = max(1, maxSteps)
        val dutyCycle = cfg.threadDutyCycle()
        ThreadPoolManager.resetThrottle() // 重置节流计时器
        try {
            while (openF.isNotEmpty() && openB.isNotEmpty() && stepsBudget-- > 0) {
                ThreadPoolManager.throttle(dutyCycle) // 根据占空比控制CPU使用率
                if (Thread.currentThread().isInterrupted) {
                    return null
                }

                val peekF = openF.peek()
                val peekB = openB.peek()
                val expandForward = when {
                    peekF === null -> false
                    peekB === null -> true
                    else -> peekF.f <= peekB.f
                }

                val meet = if (expandForward) {
                    expandOneSide(
                        openF, nodesF, closedF, nodesB, closedB, nodesB,
                        true, startGround, endGround, level, cache, neighborOffsets, d, cfg
                    )
                } else {
                    expandOneSide(
                        openB, nodesB, closedB, nodesF, closedF, nodesF,
                        false, endGround, startGround, level, cache, neighborOffsets, d, cfg
                    )
                }

                if (meet != null) {
                    // 会合后，将前向/反向节点链表合并为一条原始路径，交给 PathPostProcessor 统一处理
                    return reconstructPath(meet.forward, meet.backward, width, level, cache, cfg.bridgeMinWaterDepth())
                }
            }

            return null
        } finally {
            // 显式清理，帮助 GC 回收 Node 链表
            openF.clear()
            openB.clear()
            nodesF.clear()
            nodesB.clear()
            closedF.clear()
            closedB.clear()
            // 清理 ThreadLocal，防止线程池复用导致内存泄漏
            ThreadPoolManager.clearThrottle()
        }
    }

    private fun expandOneSide(
        open: PriorityQueue<Node>,
        nodesThis: MutableMap<BlockPos, Node>,
        closedThis: MutableSet<BlockPos>,
        nodesOther: Map<BlockPos, Node>,
        closedOther: Set<BlockPos>,
        closedOtherNodes: Map<BlockPos, Node>,
        isForward: Boolean,
        from: BlockPos,
        to: BlockPos,
        level: ServerLevel,
        cache: TerrainSamplingCache,
        neighborOffsets: Array<IntArray>,
        d: Int,
        cfg: PathfindingConfig
    ): Meet? {
        if (open.isEmpty()) return null
        val current = open.poll() ?: return null

        closedThis.add(current.pos)
        // 保留已关闭节点的引用，供对方搜索检测相遇
        // nodesThis.remove(current.pos) // 不再移除，保留供相遇检测

        for (off in neighborOffsets) {
            if (Thread.currentThread().isInterrupted) {
                return null
            }
            val nxz = current.pos.offset(off[0], 0, off[1])
            val y = RoadPathCalculator.heightSampler(cache, nxz.x, nxz.z, level)
            val np = BlockPos(nxz.x, y, nxz.z)
            if (closedThis.contains(np)) continue

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

            val deviation = deviation2d(np, from, to)
            val deviationCost = deviation * cfg.deviationWeight() / max(1.0, d.toDouble())

            val tentativeG = current.g +
                    stepCost +
                    elevation * cfg.elevationWeight() +
                    biomeCost * cfg.biomeWeight() +
                    stabilityCost * cfg.stabilityWeight() +
                    waterDepthCost +
                    nearWaterCost +
                    deviationCost

            val existing = nodesThis[np]
            if (existing !== null && tentativeG >= existing.g) {
                continue
            }

            val h = heuristic(np, to, cfg)
            val fWeighted = tentativeG + (1.0 + HEURISTIC_EPSILON) * h
            val next = Node(np, current, tentativeG, fWeighted)
            nodesThis[np] = next
            open.add(next)

            // 检测相遇：检查对方的 openSet 和 closedSet
            var other = nodesOther[np]
            if (other === null && closedOther.contains(np)) {
                other = closedOtherNodes[np]
            }
            if (other !== null) {
                // isForward 表示当前是否为前向搜索
                // 前向搜索时：next=前向节点，other=反向节点
                // 反向搜索时：next=反向节点，other=前向节点
                return if (isForward) {
                    Meet(next, other)
                } else {
                    Meet(other, next)
                }
            }
        }

        return null
    }

    private fun reconstructPath(
        meetForward: Node,
        meetBackward: Node?,
        width: Int,
        level: ServerLevel,
        cache: TerrainSamplingCache,
        bridgeMinWaterDepth: Int
    ): List<Records.RoadSegmentPlacement> {
        // 1. 从前向搜索链表回溯到起点，得到起点 -> 会合点 的路径
        val rawPath = ArrayList<BlockPos>()
        var cur: Node? = meetForward
        while (cur !== null) {
            rawPath.add(cur.pos)
            cur = cur.parent
        }
        rawPath.reverse()

        // 2. 从反向搜索的会合节点沿 parent 链回溯到终点
        // 注意：反向搜索的 parent 链方向是 会合点 → 终点，所以收集后直接添加即可
        val backStart = if (meetBackward !== null && meetBackward.pos.equals(meetForward.pos)) {
            meetBackward.parent
        } else {
            meetBackward
        }
        cur = backStart
        while (cur !== null) {
            rawPath.add(cur.pos)
            cur = cur.parent
        }

        // 3. 交给 PathPostProcessor 做样条平滑和宽度填充
        return PathPostProcessor.process(rawPath, width, level, cache, bridgeMinWaterDepth)
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

    private class Meet(
        val forward: Node,
        val backward: Node?
    )
}
