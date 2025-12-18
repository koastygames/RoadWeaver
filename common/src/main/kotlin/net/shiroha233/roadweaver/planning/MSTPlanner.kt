package net.shiroha233.roadweaver.planning

import net.minecraft.core.BlockPos
import net.shiroha233.roadweaver.helpers.Records
import java.util.ArrayList
import java.util.HashSet

object MSTPlanner {
    @JvmStatic
    fun planMST(points: List<BlockPos>?, maxEdgeLenBlocks: Int): List<Records.StructureConnection> {
        if (points === null || points.size < 2) return listOf()

        val unique = ArrayList<BlockPos>()
        val seen = HashSet<Long>()
        for (p in points) {
            val q = BlockPos(p.x, 0, p.z)
            val key = PlanningUtils.pos2dKey(q)
            if (seen.add(key)) unique.add(q)
        }
        val n = unique.size
        if (n < 2) return listOf()

        val maxD2 = if (maxEdgeLenBlocks > 0) {
            maxEdgeLenBlocks.toLong() * maxEdgeLenBlocks.toLong()
        } else {
            Long.MAX_VALUE
        }

        data class Edge(val a: Int, val b: Int, val d2: Long)

        val edges = ArrayList<Edge>()
        for (i in 0 until n) {
            val pi = unique[i]
            val ix = pi.x.toLong()
            val iz = pi.z.toLong()
            for (j in i + 1 until n) {
                val pj = unique[j]
                val dx = ix - pj.x.toLong()
                val dz = iz - pj.z.toLong()
                val d2 = dx * dx + dz * dz
                if (d2 > maxD2) continue
                edges.add(Edge(i, j, d2))
            }
        }
        if (edges.isEmpty()) return listOf()

        edges.sortWith(compareBy<Edge> { it.d2 })

        val parent = IntArray(n) { it }

        val result = ArrayList<Records.StructureConnection>()
        val edgeKeys = HashSet<Long>()

        for (e in edges) {
            val ra = find(parent, e.a)
            val rb = find(parent, e.b)
            if (ra == rb) continue
            parent[rb] = ra

            val ia = minOf(e.a, e.b)
            val ib = maxOf(e.a, e.b)
            val key = (ia.toLong() shl 32) xor ib.toLong()
            if (!edgeKeys.add(key)) continue

            result.add(Records.StructureConnection(unique[e.a], unique[e.b]))
        }

        return result
    }

    private fun find(parent: IntArray, x0: Int): Int {
        var x = x0
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]
            x = parent[x]
        }
        return x
    }
}
