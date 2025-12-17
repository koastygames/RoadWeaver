package net.shiroha233.roadweaver.features.path.pathlogic.bridge

import net.minecraft.core.BlockPos
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.helpers.Records
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

object BridgeRangeCalculator {
    data class RangeResult(
        val isBridge: BooleanArray,
        val mergedRanges: List<IntArray>,
        val skipSegments: BooleanArray
    )

    private fun dist2d(a: BlockPos, b: BlockPos): Double {
        val dx = (b.x.toLong() - a.x.toLong())
        val dz = (b.z.toLong() - a.z.toLong())
        return sqrt(dx.toDouble() * dx.toDouble() + dz.toDouble() * dz.toDouble())
    }

    private fun estimateRangeLengthBlocks(middlePositions: List<BlockPos>?, a: Int, b: Int): Int {
        if (middlePositions.isNullOrEmpty()) return 0
        val n = middlePositions.size
        var from = max(0, min(n - 1, a))
        var to = max(0, min(n - 1, b))
        if (from > to) {
            val t = from
            from = to
            to = t
        }
        var sum = 0.0
        for (i in from until to) {
            sum += dist2d(middlePositions[i], middlePositions[i + 1])
        }
        return round(sum).toInt()
    }

    @JvmStatic
    fun compute(middlePositions: List<BlockPos>, spans: List<Records.RoadSpan>?): RangeResult {
        val n = middlePositions.size
        val isBridge = BooleanArray(n)
        val skipSegments = BooleanArray(n)
        var bridgeRanges: MutableList<IntArray> = ArrayList()

        val cfg: ModConfig = ConfigService.get()
        if (!cfg.bridgeEnabled()) {
            return RangeResult(isBridge, listOf(), skipSegments)
        }

        val minLength = max(1, cfg.bridgeMinLength())
        val mergeGap = max(1, cfg.bridgeMergeGap())
        val useBuoysInstead = cfg.bridgeUseBuoysInstead()
        val maxLenBlocks = if (useBuoysInstead) 0 else max(0, cfg.bridgeMaxLengthBlocks())

        if (spans != null) {
            val indexMap = HashMap<Long, Int>()
            for (i in 0 until n) indexMap[middlePositions[i].asLong()] = i
            for (sp in spans) {
                if (sp.type != Records.SpanType.BRIDGE) continue
                val si = indexMap[sp.start.asLong()]
                val ei = indexMap[sp.end.asLong()]
                if (si == null || ei == null) continue
                val a = max(0, min(si, ei))
                val b = min(n - 1, max(si, ei))
                bridgeRanges.add(intArrayOf(a, b))
            }
        }

        if (bridgeRanges.isNotEmpty()) {
            // 按起点排序
            bridgeRanges.sortWith(compareBy { it[0] })

            // 第一轮：合并间隔小于 mergeGap 的区间（避免频繁起伏）
            val merged = ArrayList<IntArray>()
            var cur = bridgeRanges[0]
            for (idx in 1 until bridgeRanges.size) {
                val nxt = bridgeRanges[idx]
                if (nxt[0] <= cur[1] + mergeGap) {
                    cur[1] = max(cur[1], nxt[1])
                } else {
                    merged.add(cur)
                    cur = nxt
                }
            }
            merged.add(cur)

            // 第二轮：过滤掉太短的桥梁区间（避免小水坑建桥）
            val filtered = ArrayList<IntArray>()
            for (r in merged) {
                val len = r[1] - r[0] + 1
                if (len >= minLength) {
                    filtered.add(r)
                }
            }

            // 第三轮：过滤掉过长的桥梁区间（避免跨海桥），并标记需要跳过生成的水域段
            val filteredByMaxLen = ArrayList<IntArray>()
            for (r in filtered) {
                if (maxLenBlocks <= 0) {
                    filteredByMaxLen.add(r)
                    continue
                }
                val approxLen = estimateRangeLengthBlocks(middlePositions, r[0], r[1])
                if (approxLen > maxLenBlocks) {
                    val s = max(0, r[0])
                    val e = min(n - 1, r[1])
                    for (k in s..e) {
                        skipSegments[k] = true
                    }
                } else {
                    filteredByMaxLen.add(r)
                }
            }
            bridgeRanges = filteredByMaxLen
        }

        // 根据最终的桥梁区间设置 isBridge 标记
        for (r in bridgeRanges) {
            for (k in r[0]..r[1]) {
                isBridge[k] = true
            }
        }

        return RangeResult(isBridge, bridgeRanges, skipSegments)
    }
}
