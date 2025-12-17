package net.shiroha233.roadweaver.features.path.bridge

import net.minecraft.core.BlockPos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object BuoyMarkerPlanner {
    private fun dist2d(a: BlockPos, b: BlockPos): Double {
        val dx = (b.x.toLong() - a.x.toLong())
        val dz = (b.z.toLong() - a.z.toLong())
        return sqrt(dx.toDouble() * dx.toDouble() + dz.toDouble() * dz.toDouble())
    }

    @JvmStatic
    fun markersForBridgeRanges(
        middlePositions: List<BlockPos>?,
        bridgeRanges: List<IntArray>?,
        intervalBlocks: Int
    ): BooleanArray {
        if (middlePositions.isNullOrEmpty()) return BooleanArray(0)
        val n = middlePositions.size
        val res = BooleanArray(n)
        if (bridgeRanges.isNullOrEmpty()) return res

        val interval = max(1, intervalBlocks)
        for (r in bridgeRanges) {
            if (r.size < 2) continue
            val s = max(0, r[0] + 1)
            val e = min(n - 1, r[1] - 1)
            if (s > e) continue

            var acc = 0.0
            var next = 0.0
            for (idx in s..e) {
                if (idx > s) {
                    acc += dist2d(middlePositions[idx - 1], middlePositions[idx])
                }
                if (acc + 1e-6 >= next) {
                    res[idx] = true
                    next += interval
                }
            }
        }
        return res
    }

    @JvmStatic
    fun markersForMask(
        middlePositions: List<BlockPos>?,
        mask: BooleanArray?,
        intervalBlocks: Int
    ): BooleanArray {
        if (middlePositions.isNullOrEmpty()) return BooleanArray(0)
        val n = middlePositions.size
        val res = BooleanArray(n)
        if (mask == null || mask.size != n) return res

        val interval = max(1, intervalBlocks)
        var idx = 0
        while (idx < n) {
            while (idx < n && !mask[idx]) idx++
            if (idx >= n) break
            val start = idx
            while (idx < n && mask[idx]) idx++
            val end = idx - 1

            var acc = 0.0
            var next = 0.0
            for (i in start..end) {
                if (i > start) {
                    acc += dist2d(middlePositions[i - 1], middlePositions[i])
                }
                if (acc + 1e-6 >= next) {
                    res[i] = true
                    next += interval
                }
            }
        }

        return res
    }
}
