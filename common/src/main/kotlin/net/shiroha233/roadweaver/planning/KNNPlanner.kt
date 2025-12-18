package net.shiroha233.roadweaver.planning

import net.minecraft.core.BlockPos
import net.shiroha233.roadweaver.helpers.Records
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.function.IntUnaryOperator
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object KNNPlanner {
    @JvmStatic
    fun planKNN(points: List<BlockPos>?, k: Int, maxEdgeLenBlocks: Int): List<Records.StructureConnection> {
        return planKNN(points, k, maxEdgeLenBlocks, 2.5, 25.0, 3)
    }

    @JvmStatic
    fun planKNN(
        points: List<BlockPos>?,
        k: Int,
        maxEdgeLenBlocks: Int,
        alpha: Double,
        minAngleDeg: Double
    ): List<Records.StructureConnection> {
        return planKNN(points, k, maxEdgeLenBlocks, alpha, minAngleDeg, 3)
    }

    @JvmStatic
    fun planKNN(
        points: List<BlockPos>?,
        k: Int,
        maxEdgeLenBlocks: Int,
        alpha: Double,
        minAngleDeg: Double,
        degreeCap: Int
    ): List<Records.StructureConnection> {
        if (points === null || points.size < 2 || k <= 0) return listOf()

        val n = points.size
        val maxDist2 = if (maxEdgeLenBlocks > 0) {
            maxEdgeLenBlocks.toLong() * maxEdgeLenBlocks.toLong()
        } else {
            Long.MAX_VALUE
        }

        val minCos = cos(Math.toRadians(max(0.0, min(89.0, minAngleDeg))))
        val degCap = max(1, degreeCap)

        val edgeKeys = HashSet<Long>()
        val edges = ArrayList<Records.StructureConnection>()

        val nn2 = LongArray(n)
        for (i in 0 until n) {
            var best = Long.MAX_VALUE
            val a = points[i]
            for (j in 0 until n) {
                if (i == j) continue
                val d2 = dist2(a, points[j])
                if (d2 < best) best = d2
            }
            nn2[i] = best
        }

        val adj = ArrayList<MutableList<Int>>(n)
        for (i in 0 until n) adj.add(ArrayList())

        for (i in 0 until n) {
            val cand = ArrayList<Neighbor>()
            val a = points[i]
            for (j in 0 until n) {
                if (i == j) continue
                val b = points[j]
                val d2 = dist2(a, b)
                if (d2 > maxDist2) continue
                if (alpha <= 0.0 || d2.toDouble() <= (alpha * alpha) * max(1L, max(nn2[i], nn2[j])).toDouble()) {
                    cand.add(Neighbor(j, d2))
                }
            }
            cand.sortWith(compareBy { it.d2 })

            val limit = min(k, cand.size)
            for (t in 0 until limit) {
                val j = cand[t].idx
                if (!gabrielOk(points, i, j, cand)) continue
                if (adj[i].size >= degCap || adj[j].size >= degCap) continue
                if (!angleOk(points, adj[i], i, j, minCos)) continue
                if (!angleOk(points, adj[j], j, i, minCos)) {
                    // 允许只在一侧通过角度检查即可，减少过密锐角
                }

                val aIdx = min(i, j)
                val bIdx = max(i, j)
                val key = (aIdx.toLong() shl 32) xor bIdx.toLong()

                if (edgeKeys.add(key)) {
                    edges.add(Records.StructureConnection(points[aIdx], points[bIdx]))
                    adj[i].add(j)
                    adj[j].add(i)
                }
            }
        }

        return edges
    }

    @JvmStatic
    fun connectComponents(
        points: List<BlockPos>?,
        base: List<Records.StructureConnection>?,
        maxJoinLenBlocks: Int,
        minAngleDeg: Double,
        degreeCap: Int
    ): List<Records.StructureConnection> {
        val pts = points ?: return listOf()
        val n = pts.size
        if (n < 2) return listOf()

        val maxD2 = if (maxJoinLenBlocks > 0) {
            maxJoinLenBlocks.toLong() * maxJoinLenBlocks.toLong()
        } else {
            Long.MAX_VALUE
        }

        val minCos = cos(Math.toRadians(max(0.0, min(89.0, minAngleDeg))))

        val adj = ArrayList<MutableList<Int>>(n)
        for (i in 0 until n) adj.add(ArrayList())

        val parent = IntArray(n) { it }

        val index = HashMap<BlockPos, Int>(n * 2)
        for (i in 0 until n) index[pts[i]] = i

        val find = object : IntUnaryOperator {
            override fun applyAsInt(x0: Int): Int {
                var x = x0
                while (parent[x] != x) {
                    parent[x] = parent[parent[x]]
                    x = parent[x]
                }
                return x
            }
        }

        fun unite(a: Int, b: Int) {
            val ra = find.applyAsInt(a)
            val rb = find.applyAsInt(b)
            if (ra != rb) parent[rb] = ra
        }

        val keys = HashSet<Long>()
        if (base != null) {
            for (c in base) {
                val ia = index[c.from]
                val ib = index[c.to]
                if (ia === null || ib === null) continue

                val a = min(ia, ib)
                val b = max(ia, ib)
                val key = (a.toLong() shl 32) xor b.toLong()
                keys.add(key)

                unite(ia, ib)
                adj[ia].add(ib)
                adj[ib].add(ia)
            }
        }

        data class Pair(val a: Int, val b: Int, val d2: Long)

        val cand = ArrayList<Pair>()
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (find.applyAsInt(i) == find.applyAsInt(j)) continue
                val d2 = dist2(pts[i], pts[j])
                if (d2 <= maxD2) cand.add(Pair(i, j, d2))
            }
        }
        cand.sortWith(compareBy { it.d2 })

        val added = ArrayList<Records.StructureConnection>()
        for (p in cand) {
            val ra = find.applyAsInt(p.a)
            val rb = find.applyAsInt(p.b)
            if (ra == rb) continue
            if (adj[p.a].size >= degreeCap || adj[p.b].size >= degreeCap) continue
            if (!angleOk(pts, adj[p.a], p.a, p.b, minCos)) continue
            if (!angleOk(pts, adj[p.b], p.b, p.a, minCos)) {
                // 允许只在一侧通过角度检查即可
            }

            val a = min(p.a, p.b)
            val b = max(p.a, p.b)
            val key = (a.toLong() shl 32) xor b.toLong()
            if (keys.add(key)) {
                added.add(Records.StructureConnection(pts[a], pts[b]))
                adj[p.a].add(p.b)
                adj[p.b].add(p.a)
                unite(p.a, p.b)
            }
        }

        return added
    }

    private fun dist2(a: BlockPos, b: BlockPos): Long {
        val dx = a.x.toLong() - b.x.toLong()
        val dz = a.z.toLong() - b.z.toLong()
        return dx * dx + dz * dz
    }

    private fun angleOk(pts: List<BlockPos>, neighbors: List<Int>, i: Int, j: Int, minCos: Double): Boolean {
        if (neighbors.isEmpty()) return true
        val a = pts[i]
        val b = pts[j]
        val abx = (b.x - a.x).toLong()
        val abz = (b.z - a.z).toLong()
        val abLen = hypot(abx.toDouble(), abz.toDouble())
        if (abLen == 0.0) return false

        val abxN = abx / abLen
        val abzN = abz / abLen

        for (nb in neighbors) {
            val c = pts[nb]
            val acx = (c.x - a.x).toLong()
            val acz = (c.z - a.z).toLong()
            val acLen = hypot(acx.toDouble(), acz.toDouble())
            if (acLen == 0.0) continue
            val acxN = acx / acLen
            val aczN = acz / acLen
            val cosv = abxN * acxN + abzN * aczN
            if (cosv > minCos) return false
        }
        return true
    }

    private fun gabrielOk(pts: List<BlockPos>, i: Int, j: Int, local: List<Neighbor>): Boolean {
        val a = pts[i]
        val b = pts[j]
        val ab2 = dist2(a, b)
        for (nb in local) {
            val k = nb.idx
            if (k == i || k == j) continue
            val c = pts[k]
            val s = dist2(a, c) + dist2(b, c)
            if (s <= ab2) return false
        }
        return true
    }

    private data class Neighbor(val idx: Int, val d2: Long)
}
