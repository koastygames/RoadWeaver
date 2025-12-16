package net.shiroha233.roadweaver.planning

import net.minecraft.core.BlockPos
import net.shiroha233.roadweaver.helpers.Records
import java.util.ArrayList
import java.util.HashSet
import kotlin.math.abs
import kotlin.math.max

object DelaunayPlanner {
    @JvmStatic
    fun planDelaunay(points: List<BlockPos>?, maxEdgeLenBlocks: Int): List<Records.StructureConnection> {
        if (points == null || points.size < 2) return listOf()

        val unique = ArrayList<BlockPos>()
        val seen = HashSet<Long>()
        for (p in points) {
            val q = BlockPos(p.x, 0, p.z)
            val k = PlanningUtils.pos2dKey(q)
            if (seen.add(k)) unique.add(q)
        }
        if (unique.size < 2) return listOf()

        val b = bounds(unique)
        val dx = b.maxX - b.minX
        val dz = b.maxZ - b.minZ
        var delta = max(dx, dz)
        if (delta <= 0.0) delta = 1.0
        val cx = (b.minX + b.maxX) * 0.5
        val cz = (b.minZ + b.maxZ) * 0.5

        val v1 = Vertex(cx - 2.0 * delta, cz - delta)
        val v2 = Vertex(cx, cz + 2.0 * delta)
        val v3 = Vertex(cx + 2.0 * delta, cz - delta)

        val n = unique.size
        val verts = ArrayList<Vertex>(n + 3)
        for (p in unique) verts.add(Vertex(p.x.toDouble(), p.z.toDouble()))
        verts.add(v1)
        verts.add(v2)
        verts.add(v3)

        val s1 = n
        val s2 = n + 1
        val s3 = n + 2

        val tris = ArrayList<Tri>()
        tris.add(Tri(s1, s2, s3))

        for (i in 0 until n) {
            val p = verts[i]
            val bad = ArrayList<Tri>()
            for (t in tris) {
                if (t.invalid) continue
                if (inCircumcircle(verts, t, p)) bad.add(t)
            }

            val polygon = ArrayList<Edge>()
            for (t in bad) {
                t.invalid = true
                addOrRemove(polygon, Edge(t.a, t.b))
                addOrRemove(polygon, Edge(t.b, t.c))
                addOrRemove(polygon, Edge(t.c, t.a))
            }

            tris.removeAll { it.invalid }

            for (e in polygon) {
                tris.add(Tri(e.u, e.v, i))
            }
        }

        tris.removeAll { it.contains(s1) || it.contains(s2) || it.contains(s3) }

        val maxD2 = if (maxEdgeLenBlocks > 0) {
            maxEdgeLenBlocks.toLong() * maxEdgeLenBlocks.toLong()
        } else {
            Long.MAX_VALUE
        }

        val edgeKeys = HashSet<Long>()
        val out = ArrayList<Records.StructureConnection>()

        for (t in tris) {
            addEdge(out, edgeKeys, unique, t.a, t.b, maxD2)
            addEdge(out, edgeKeys, unique, t.b, t.c, maxD2)
            addEdge(out, edgeKeys, unique, t.c, t.a, maxD2)
        }

        return out
    }

    private fun addEdge(
        out: MutableList<Records.StructureConnection>,
        keys: MutableSet<Long>,
        pts: List<BlockPos>,
        ia: Int,
        ib: Int,
        maxD2: Long
    ) {
        if (ia >= pts.size || ib >= pts.size) return
        val a = minOf(ia, ib)
        val b = maxOf(ia, ib)
        val key = (a.toLong() shl 32) xor b.toLong()
        if (!keys.add(key)) return

        val pa = pts[ia]
        val pb = pts[ib]
        val dx = pa.x.toLong() - pb.x.toLong()
        val dz = pa.z.toLong() - pb.z.toLong()
        val d2 = dx * dx + dz * dz
        if (d2 > maxD2) return

        out.add(Records.StructureConnection(pa, pb))
    }

    private fun addOrRemove(polygon: MutableList<Edge>, e: Edge) {
        var idx = -1
        for (i in polygon.indices) {
            if (polygon[i] == e) {
                idx = i
                break
            }
        }
        if (idx >= 0) polygon.removeAt(idx) else polygon.add(e)
    }

    private fun inCircumcircle(verts: List<Vertex>, t: Tri, p: Vertex): Boolean {
        val a = verts[t.a]
        val b = verts[t.b]
        val c = verts[t.c]

        val area2 = (b.x - a.x) * (c.z - a.z) - (b.z - a.z) * (c.x - a.x)
        if (abs(area2) < 1e-6) return false

        val ax = a.x - p.x
        val ay = a.z - p.z
        val bx = b.x - p.x
        val by = b.z - p.z
        val cx = c.x - p.x
        val cy = c.z - p.z

        val det = (ax * ax + ay * ay) * (bx * cy - by * cx) -
            (bx * bx + by * by) * (ax * cy - ay * cx) +
            (cx * cx + cy * cy) * (ax * by - ay * bx)

        return if (area2 > 0) det > 1e-6 else det < -1e-6
    }

    private fun bounds(pts: List<BlockPos>): Bounds {
        var minX = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY

        for (p in pts) {
            val x = p.x.toDouble()
            val z = p.z.toDouble()
            if (x < minX) minX = x
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (z > maxZ) maxZ = z
        }

        return Bounds(minX, minZ, maxX, maxZ)
    }

    private data class Bounds(val minX: Double, val minZ: Double, val maxX: Double, val maxZ: Double)

    private data class Vertex(val x: Double, val z: Double)

    private class Tri(val a: Int, val b: Int, val c: Int) {
        var invalid: Boolean = false
        fun contains(v: Int): Boolean = a == v || b == v || c == v
    }

    private data class Edge private constructor(val u: Int, val v: Int) {
        companion object {
            operator fun invoke(a: Int, b: Int): Edge {
                return if (a < b) Edge(a, b) else Edge(b, a)
            }
        }
    }
}
