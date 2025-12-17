package net.shiroha233.roadweaver.features.path.pathlogic.surface

import net.shiroha233.roadweaver.config.ModConfig
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object BridgeTransitionAdjuster {
    @JvmStatic
    fun adjust(baseY: IntArray?, bridgeRanges: List<IntArray>?, cfg: ModConfig): IntArray? {
        if (baseY == null || bridgeRanges.isNullOrEmpty()) return baseY
        val n = baseY.size
        val rampN = max(0, cfg.bridgeRampSegments())
        if (rampN <= 0) return baseY

        val original = baseY.clone()
        val adjusted = baseY.clone()

        for (r in bridgeRanges) {
            var a = max(0, min(n - 1, r[0]))
            var b = max(0, min(n - 1, r[1]))
            if (a > b) {
                val t = a
                a = b
                b = t
            }
            val leftStart = max(0, a - rampN)
            val rightEnd = min(n - 1, b + rampN)

            val boundaryLeftY = original[a]
            val boundaryRightY = original[b]

            val countLeft = a - leftStart
            if (countLeft > 0) {
                for (j in leftStart..(a - 1)) {
                    val dist = (a - 1) - j
                    val w = if (countLeft == 0) 0.0 else (1.0 - (dist / countLeft.toDouble()))
                    val target = original[j] * (1.0 - w) + boundaryLeftY * w
                    adjusted[j] = round(target).toInt()
                }
            }

            val countRight = rightEnd - b
            if (countRight > 0) {
                for (j in (b + 1)..rightEnd) {
                    val dist = j - (b + 1)
                    val w = if (countRight == 0) 0.0 else (1.0 - (dist / countRight.toDouble()))
                    val target = original[j] * (1.0 - w) + boundaryRightY * w
                    adjusted[j] = round(target).toInt()
                }
            }
        }

        val step = max(0, min(8, cfg.maxSlopeStepPerTwoSegments()))
        if (!cfg.slopeLimitEnabled() || step <= 0) {
            return adjusted
        }

        for (i in 1 until n) {
            if (adjusted[i] > adjusted[i - 1] + step) adjusted[i] = adjusted[i - 1] + step
            if (adjusted[i] < adjusted[i - 1] - step) adjusted[i] = adjusted[i - 1] - step
        }
        for (i in (n - 2) downTo 0) {
            if (adjusted[i] > adjusted[i + 1] + step) adjusted[i] = adjusted[i + 1] + step
            if (adjusted[i] < adjusted[i + 1] - step) adjusted[i] = adjusted[i + 1] - step
        }
        return adjusted
    }
}
