package net.shiroha233.roadweaver.features.path.pathlogic.surface

import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.levelgen.Heightmap
import net.shiroha233.roadweaver.config.ModConfig
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object HeightProfileService {
    data class HeightProfile(
        val usePersisted: Boolean,
        val smoothedY: IntArray?
    )

    @JvmStatic
    fun build(
        world: WorldGenLevel,
        middlePositions: List<BlockPos>,
        currentChunk: ChunkPos,
        averagingRadius: Int,
        cfg: ModConfig,
        targetY: List<Int>?
    ): HeightProfile {
        val n = middlePositions.size
        val usePersisted = targetY !== null && targetY.size == n
        if (usePersisted) {
            return HeightProfile(true, null)
        }

        val baseYArr = IntArray(n)
        for (ii in 0 until n) {
            val hs = ArrayList<Int>()
            for (jj in (ii - averagingRadius)..(ii + averagingRadius)) {
                if (jj in 0 until n) {
                    val sp = middlePositions[jj]
                    if (ChunkPos(sp).equals(currentChunk)) {
                        val sea = world.level.seaLevel
                        val motion = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.x, sp.z)
                        val surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, sp.x, sp.z)
                        val yTop = if (motion > sea + 2) motion else surface
                        hs.add(yTop)
                    }
                }
            }

            if (hs.isEmpty()) {
                val mid = middlePositions[ii]
                if (ChunkPos(mid).equals(currentChunk)) {
                    val sea = world.level.seaLevel
                    val motion = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mid.x, mid.z)
                    val surface = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, mid.x, mid.z)
                    baseYArr[ii] = if (motion > sea + 2) motion else surface
                } else {
                    baseYArr[ii] = middlePositions[ii].y
                }
            } else {
                val avg = hs.average()
                baseYArr[ii] = round(avg).toInt()
            }
        }

        // 如果关闭限坡平滑，则直接返回基于平均的高度，不再进行每两段的步进限制
        if (!cfg.slopeLimitEnabled()) {
            val noSmoothed = IntArray(n)
            for (ii in 0 until n) noSmoothed[ii] = baseYArr[ii]
            return HeightProfile(false, noSmoothed)
        }

        val smoothed = IntArray(n)
        for (ii in 0 until n) smoothed[ii] = baseYArr[ii]

        val step2 = max(0, min(8, cfg.maxSlopeStepPerTwoSegments()))
        val halfLow = max(0, step2 / 2)
        val halfHigh = max(0, (step2 + 1) / 2)

        for (ii in 1 until n) {
            var y = smoothed[ii]
            if (ii == 1) {
                val py = smoothed[ii - 1]
                if (y > py + halfLow) y = py + halfLow
                if (y < py - halfLow) y = py - halfLow
            } else {
                val py = smoothed[ii - 1]
                if (y > py + halfHigh) y = py + halfHigh
                if (y < py - halfHigh) y = py - halfHigh
                val p2 = smoothed[ii - 2]
                val hi = p2 + step2
                val lo = p2 - step2
                if (y > hi) y = hi
                if (y < lo) y = lo
            }
            smoothed[ii] = y
        }

        for (ii in (n - 2) downTo 0) {
            var y = smoothed[ii]
            if (ii == n - 2) {
                val ny = smoothed[ii + 1]
                if (y > ny + halfLow) y = ny + halfLow
                if (y < ny - halfLow) y = ny - halfLow
            } else {
                val ny = smoothed[ii + 1]
                if (y > ny + halfHigh) y = ny + halfHigh
                if (y < ny - halfHigh) y = ny - halfHigh
                val n2 = smoothed[ii + 2]
                val hi = n2 + step2
                val lo = n2 - step2
                if (y > hi) y = hi
                if (y < lo) y = lo
            }
            smoothed[ii] = y
        }

        return HeightProfile(false, smoothed)
    }
}
