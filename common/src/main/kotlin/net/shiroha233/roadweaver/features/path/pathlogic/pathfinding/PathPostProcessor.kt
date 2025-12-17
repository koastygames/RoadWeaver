package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.shiroha233.roadweaver.helpers.Records
import kotlin.math.*

object PathPostProcessor {
    /**
     * 将原始寻路节点列表转换为平滑的、具有宽度的路段列表。
     * 使用 Catmull-Rom 样条曲线生成平滑路径，并通过距离场光栅化生成路面。
     */
    @JvmStatic
    fun process(
        rawPath: List<BlockPos>?,
        width: Int,
        level: ServerLevel,
        cache: TerrainSamplingCache,
        bridgeMinWaterDepth: Int
    ): List<Records.RoadSegmentPlacement> {
        if (rawPath == null || rawPath.size < 2) return ArrayList()

        // 1. 路径简化
        val simplified = simplifyPath(rawPath)
        val bridgeMask = detectBridgeMask(simplified, level, cache, bridgeMinWaterDepth)
        // 桥梁段不做曲线松弛/样条平滑，否则会出现弯桥
        val controlPoints = relaxPathSkippingBridge(simplified, bridgeMask)
        if (controlPoints.size < 2) return ArrayList()

        // 2. 生成高密度样条曲线点集
        val extendedPoints = ArrayList<BlockPos>()
        extendedPoints.add(controlPoints[0])
        extendedPoints.addAll(controlPoints)
        extendedPoints.add(controlPoints[controlPoints.size - 1])

        val splinePoints = ArrayList<Vec2d>()

        for (i in 0 until controlPoints.size - 1) {
            val p0 = extendedPoints[i]
            val p1 = extendedPoints[i + 1]
            val p2 = extendedPoints[i + 2]
            val p3 = extendedPoints[i + 3]

            val dist = sqrt(p1.distSqr(p2).toDouble())
            var steps = ceil(dist * 4.0).toInt() // 极高密度采样
            if (steps < 1) steps = 1

            val bridgeSeg = (i >= 0 && i < bridgeMask.size - 1) && (bridgeMask[i] || bridgeMask[i + 1])

            for (s in 0 until steps) {
                val t = s.toDouble() / steps
                val pt = if (bridgeSeg) lerp2d(p1, p2, t) else catmullRomSplineDouble(p0, p1, p2, p3, t)
                splinePoints.add(pt)
            }
        }
        // 添加最后一点
        val lastBP = controlPoints[controlPoints.size - 1]
        val lastPt = Vec2d(lastBP.x.toDouble(), lastBP.z.toDouble())
        splinePoints.add(lastPt)

        // 3. 提取骨架中心点 (Centers)
        val centers = ArrayList<BlockPos>()
        val centerDists = ArrayList<Double>()

        var currentDist = 0.0
        var nextCenterDist = 0.0

        for (i in splinePoints.indices) {
            val p = splinePoints[i]
            if (i > 0) {
                currentDist += p.dist(splinePoints[i - 1])
            }

            if (currentDist >= nextCenterDist || i == splinePoints.size - 1) {
                val y = RoadPathCalculator.heightSampler(cache, p.x.roundToInt(), p.z.roundToInt(), level)
                val centerPos = BlockPos(p.x.roundToInt(), y, p.z.roundToInt())

                if (centers.isEmpty() || centers[centers.size - 1] != centerPos) {
                    centers.add(centerPos)
                    centerDists.add(currentDist)
                    nextCenterDist = currentDist + 1.0
                }
            }
        }

        // 4. 距离场光栅化 & 归仓
        // 固定使用「到线段的投影距离」归仓（与高度插值一致）

        val segmentedBlocks = HashMap<Int, MutableSet<BlockPos>>()
        for (i in centers.indices) segmentedBlocks[i] = HashSet()

        val halfWidth = width / 2.0
        val halfWidthSq = halfWidth * halfWidth

        var pathDist = 0.0
        var currentCenterIdx = 0

        for (i in 0 until splinePoints.size - 1) {
            val pStart = splinePoints[i]
            val pEnd = splinePoints[i + 1]
            val segLen = pStart.dist(pEnd)
            pathDist += segLen

            while (currentCenterIdx < centerDists.size - 1 && centerDists[currentCenterIdx + 1] < pathDist) {
                currentCenterIdx++
            }

            val minX = floor(min(pStart.x, pEnd.x) - halfWidth - 1).toInt()
            val maxX = ceil(max(pStart.x, pEnd.x) + halfWidth + 1).toInt()
            val minZ = floor(min(pStart.z, pEnd.z) - halfWidth - 1).toInt()
            val maxZ = ceil(max(pStart.z, pEnd.z) + halfWidth + 1).toInt()

            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    val dSq = distToSegmentSq(x.toDouble(), z.toDouble(), pStart, pEnd)

                    if (dSq <= halfWidthSq) {
                        val blockPos = BlockPos(x, 0, z)

                        var bestIdx = currentCenterIdx
                        var bestDistSq = Double.MAX_VALUE

                        // 扩展到 ±20 以覆盖宽道路在弯道处的情况
                        val extendedRadius = 20
                        val searchStart = max(0, currentCenterIdx - extendedRadius)
                        val searchEnd = min(centers.size - 2, currentCenterIdx + extendedRadius)

                        // 使用到线段的投影距离（与 RoadHeightInterpolator 一致）
                        for (seg in searchStart..searchEnd) {
                            val a = centers[seg]
                            val b = centers[seg + 1]

                            val ax = a.x.toDouble()
                            val az = a.z.toDouble()
                            val bx = b.x.toDouble()
                            val bz = b.z.toDouble()
                            val dx = bx - ax
                            val dz = bz - az
                            val lenSq = dx * dx + dz * dz

                            val t = if (lenSq < 1e-9) 0.0
                            else max(0.0, min(1.0, ((x - ax) * dx + (z - az) * dz) / lenSq))

                            val projX = ax + t * dx
                            val projZ = az + t * dz
                            val projDistSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ)

                            if (projDistSq < bestDistSq) {
                                bestDistSq = projDistSq
                                // 归仓到插值位置更近的那个 center
                                // 如果 t < 0.5，归到 seg；否则归到 seg+1
                                bestIdx = if (t < 0.5) seg else min(seg + 1, centers.size - 1)
                            }
                        }

                        segmentedBlocks[bestIdx]?.add(blockPos)
                    }
                }
            }
        }

        val out = ArrayList<Records.RoadSegmentPlacement>()
        for (i in centers.indices) {
            val center = centers[i]
            var blocks = segmentedBlocks[i] ?: HashSet()
            if (blocks.isEmpty()) blocks = mutableSetOf(BlockPos(center.x, 0, center.z))
            out.add(Records.RoadSegmentPlacement(center, ArrayList(blocks)))
        }
        return out
    }

    private data class Vec2d(val x: Double, val z: Double) {
        fun dist(o: Vec2d): Double = sqrt(distSqr(o))
        fun distSqr(o: Vec2d): Double = (x - o.x) * (x - o.x) + (z - o.z) * (z - o.z)
    }

    private fun lerp2d(a: BlockPos, b: BlockPos, t: Double): Vec2d {
        val x = a.x + (b.x - a.x) * t
        val z = a.z + (b.z - a.z) * t
        return Vec2d(x, z)
    }

    private fun detectBridgeMask(
        nodes: List<BlockPos>,
        level: ServerLevel,
        cache: TerrainSamplingCache,
        bridgeMinWaterDepth: Int
    ): BooleanArray {
        val n = nodes.size
        val mask = BooleanArray(n)
        val minDepth = max(1, bridgeMinWaterDepth)
        val sea = level.seaLevel
        for (i in 0 until n) {
            val p = nodes[i]
            val isWater = RoadPathCalculator.isColumnWater(cache, p.x, p.z, level)
            if (!isWater) {
                mask[i] = false
                continue
            }
            val oceanFloor = RoadPathCalculator.oceanFloorSampler(cache, p.x, p.z, level)
            val waterDepth = max(0, sea - oceanFloor)
            mask[i] = waterDepth >= minDepth
        }
        return mask
    }

    private fun distToSegmentSq(px: Double, pz: Double, v: Vec2d, w: Vec2d): Double {
        val l2 = v.distSqr(w)
        if (l2 == 0.0) return (px - v.x) * (px - v.x) + (pz - v.z) * (pz - v.z)
        var t = ((px - v.x) * (w.x - v.x) + (pz - v.z) * (w.z - v.z)) / l2
        t = max(0.0, min(1.0, t))
        val projX = v.x + t * (w.x - v.x)
        val projZ = v.z + t * (w.z - v.z)
        return (px - projX) * (px - projX) + (pz - projZ) * (pz - projZ)
    }

    private fun catmullRomSplineDouble(p0: BlockPos, p1: BlockPos, p2: BlockPos, p3: BlockPos, t: Double): Vec2d {
        val t2 = t * t
        val t3 = t2 * t

        val f0 = -0.5 * t3 + t2 - 0.5 * t
        val f1 = 1.5 * t3 - 2.5 * t2 + 1.0
        val f2 = -1.5 * t3 + 2.0 * t2 + 0.5 * t
        val f3 = 0.5 * t3 - 0.5 * t2

        val x = p0.x * f0 + p1.x * f1 + p2.x * f2 + p3.x * f3
        val z = p0.z * f0 + p1.z * f1 + p2.z * f2 + p3.z * f3

        return Vec2d(x, z)
    }

    private fun simplifyPath(nodes: List<BlockPos>): List<BlockPos> {
        if (nodes.size < 3) return ArrayList(nodes)

        val simplified = ArrayList<BlockPos>()
        simplified.add(nodes[0])

        for (i in 1 until nodes.size - 1) {
            val prev = simplified[simplified.size - 1]
            val curr = nodes[i]
            val next = nodes[i + 1]

            val dx1 = (curr.x - prev.x).toLong()
            val dz1 = (curr.z - prev.z).toLong()
            val dx2 = (next.x - curr.x).toLong()
            val dz2 = (next.z - curr.z).toLong()

            val crossProduct = dx1 * dz2 - dz1 * dx2

            if (abs(crossProduct) > 16) {
                simplified.add(curr)
            }
        }

        simplified.add(nodes[nodes.size - 1])
        return simplified
    }

    /**
     * 对路径控制点进行松弛操作，消除尖锐的折角。
     * 解决 Z 字形路径在样条插值后产生扭曲的问题。
     */
    private fun relaxPath(nodes: List<BlockPos>): List<BlockPos> {
        if (nodes.size < 3) return ArrayList(nodes)

        val relaxed = ArrayList<BlockPos>()
        relaxed.add(nodes[0]) // 起点不动

        for (i in 1 until nodes.size - 1) {
            val prev = nodes[i - 1]
            val curr = nodes[i]
            val next = nodes[i + 1]

            // 加权平均: (Prev + 2*Curr + Next) / 4
            // 这样可以保留大部分原始位置，但会把尖角稍微"磨圆"
            val nx = (prev.x + curr.x * 2 + next.x) / 4
            val nz = (prev.z + curr.z * 2 + next.z) / 4

            relaxed.add(BlockPos(nx, curr.y, nz))
        }

        relaxed.add(nodes[nodes.size - 1]) // 终点不动
        return relaxed
    }

    private fun relaxPathSkippingBridge(nodes: List<BlockPos>, bridgeMask: BooleanArray): List<BlockPos> {
        if (nodes.size < 3) return ArrayList(nodes)

        var hasBridge = false
        for (b in bridgeMask) {
            if (b) {
                hasBridge = true
                break
            }
        }
        if (!hasBridge) {
            return relaxPath(nodes)
        }
        val relaxed = ArrayList<BlockPos>()
        relaxed.add(nodes[0])

        for (i in 1 until nodes.size - 1) {
            val isBridge = i < bridgeMask.size && bridgeMask[i]
            val nearBridge = (i - 1 >= 0 && i - 1 < bridgeMask.size && bridgeMask[i - 1]) ||
                    (i + 1 >= 0 && i + 1 < bridgeMask.size && bridgeMask[i + 1])
            if (isBridge || nearBridge) {
                relaxed.add(nodes[i])
                continue
            }

            val prev = nodes[i - 1]
            val curr = nodes[i]
            val next = nodes[i + 1]
            val nx = (prev.x + curr.x * 2 + next.x) / 4
            val nz = (prev.z + curr.z * 2 + next.z) / 4
            relaxed.add(BlockPos(nx, curr.y, nz))
        }

        relaxed.add(nodes[nodes.size - 1])
        return relaxed
    }
}
