package net.shiroha233.roadweaver.planning

import net.minecraft.core.BlockPos
import net.shiroha233.roadweaver.helpers.Records
import java.util.ArrayList
import java.util.HashSet

object RNGPlanner {
    @JvmStatic
    fun planRNG(points: List<BlockPos>?, maxEdgeLenBlocks: Int): List<Records.StructureConnection> {
        if (points == null || points.size < 2) return listOf()

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

        val xs = LongArray(n)
        val zs = LongArray(n)
        for (i in 0 until n) {
            xs[i] = unique[i].x.toLong()
            zs[i] = unique[i].z.toLong()
        }

        val edgeKeys = HashSet<Long>()
        val edges = ArrayList<Records.StructureConnection>()

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val dx = xs[i] - xs[j]
                val dz = zs[i] - zs[j]
                val d2 = dx * dx + dz * dz
                if (d2 > maxD2) continue

                var blocked = false
                for (k in 0 until n) {
                    if (k == i || k == j) continue
                    val dax = xs[i] - xs[k]
                    val daz = zs[i] - zs[k]
                    val dbx = xs[j] - xs[k]
                    val dbz = zs[j] - zs[k]
                    val da2 = dax * dax + daz * daz
                    val db2 = dbx * dbx + dbz * dbz
                    if (da2 < d2 && db2 < d2) {
                        blocked = true
                        break
                    }
                }

                if (blocked) continue

                val key = (i.toLong() shl 32) xor j.toLong()
                if (edgeKeys.add(key)) {
                    edges.add(Records.StructureConnection(unique[i], unique[j]))
                }
            }
        }

        return edges
    }
}
