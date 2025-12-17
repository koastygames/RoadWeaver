package net.shiroha233.roadweaver.features.path.pathlogic.surface

import net.minecraft.core.BlockPos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * 道路高度插值器
 */
object RoadHeightInterpolator {
    @JvmStatic
    fun getInterpolatedY(x: Int, z: Int, centers: List<BlockPos>?, targetY: IntArray?): Int {
        if (centers.isNullOrEmpty() || targetY == null || targetY.isEmpty()) {
            return 64
        }

        val n = centers.size
        if (n == 1 || targetY.size == 1) {
            return targetY[0]
        }

        if (targetY.size != n) {
            return targetY[0]
        }

        val proj = findNearestProjection(x, z, centers)
        return interpolateY(proj.segmentIndex, proj.t, targetY)
    }

    private data class ProjectionResult(val segmentIndex: Int, val t: Double, val distSq: Double)

    private fun findNearestProjection(x: Int, z: Int, centers: List<BlockPos>): ProjectionResult {
        val n = centers.size

        var bestSegment = 0
        var bestT = 0.0
        var bestDistSq = Double.MAX_VALUE

        for (i in 0 until (n - 1)) {
            val a = centers[i]
            val b = centers[i + 1]

            val ax = a.x.toDouble()
            val az = a.z.toDouble()
            val bx = b.x.toDouble()
            val bz = b.z.toDouble()

            val dx = bx - ax
            val dz = bz - az
            val lenSq = dx * dx + dz * dz

            val t = if (lenSq < 1e-9) {
                0.0
            } else {
                max(0.0, min(1.0, ((x - ax) * dx + (z - az) * dz) / lenSq))
            }

            val projX = ax + t * dx
            val projZ = az + t * dz

            val distSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ)

            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestSegment = i
                bestT = t
            }
        }

        return ProjectionResult(bestSegment, bestT, bestDistSq)
    }

    private fun interpolateY(segmentIndex: Int, t: Double, targetY: IntArray): Int {
        val y0 = targetY[segmentIndex]
        val y1 = targetY[segmentIndex + 1]
        val interpolated = y0 + t * (y1 - y0)
        return round(interpolated).toInt()
    }

    @JvmStatic
    fun batchInterpolate(positions: List<BlockPos>?, segmentIndex: Int, centers: List<BlockPos>, targetY: IntArray): IntArray {
        if (positions.isNullOrEmpty()) {
            return IntArray(0)
        }

        val results = IntArray(positions.size)
        val n = centers.size

        val extendedRadius = 20
        val searchStart = max(0, segmentIndex - extendedRadius)
        val searchEnd = min(n - 2, segmentIndex + extendedRadius)

        for (i in positions.indices) {
            val pos = positions[i]
            val x = pos.x
            val z = pos.z

            var bestSeg = segmentIndex
            var bestT = 0.5
            var bestDistSq = Double.MAX_VALUE

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

                val t = if (lenSq < 1e-9) {
                    0.0
                } else {
                    max(0.0, min(1.0, ((x - ax) * dx + (z - az) * dz) / lenSq))
                }

                val projX = ax + t * dx
                val projZ = az + t * dz
                val distSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ)

                if (distSq < bestDistSq) {
                    bestDistSq = distSq
                    bestSeg = seg
                    bestT = t
                }
            }

            results[i] = interpolateY(bestSeg, bestT, targetY)
        }

        return results
    }
}
