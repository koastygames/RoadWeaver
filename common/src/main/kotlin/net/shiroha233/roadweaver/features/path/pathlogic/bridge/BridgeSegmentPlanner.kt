package net.shiroha233.roadweaver.features.path.pathlogic.bridge

import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.features.path.bridge.BridgeBuilder
import net.shiroha233.roadweaver.helpers.Records
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object BridgeSegmentPlanner {
    class Context {
        var insideBridgeRange: Boolean = false
        var currentRangeEnd: Int = -1
        var lastBridgeDeckY: Int? = null
    }

    @JvmStatic
    fun newContext(): Context = Context()

    private fun clampDeckY(
        candidateDeckY: Int,
        lastDeckY: Int?,
        deckY: Int,
        cfg: ModConfig,
        inRamp: Boolean,
        approachDeck: Boolean
    ): Int {
        var segDeckY = candidateDeckY
        if (lastDeckY != null) {
            val stepDeck = max(0, min(8, cfg.maxSlopeStepPerTwoSegments()))
            if (cfg.slopeLimitEnabled() && stepDeck > 0) {
                if (segDeckY > lastDeckY + stepDeck) segDeckY = lastDeckY + stepDeck
                if (segDeckY < lastDeckY - stepDeck) segDeckY = lastDeckY - stepDeck
            }

            if (inRamp) {
                val prevDist = abs(lastDeckY - deckY)
                val newDist = abs(segDeckY - deckY)
                if (approachDeck) {
                    if (newDist > prevDist) {
                        segDeckY = lastDeckY
                    }
                } else {
                    if (newDist < prevDist) {
                        segDeckY = lastDeckY
                    }
                }
            }
        }
        return segDeckY
    }

    @JvmStatic
    fun processSegment(
        world: WorldGenLevel,
        seg: Records.RoadSegmentPlacement,
        middle: BlockPos,
        prev: BlockPos,
        next: BlockPos,
        roadWidth: Int,
        baseYForThis: Int,
        deckY: Int,
        segmentIndex: Int,
        random: RandomSource,
        cfg: ModConfig,
        bridgeRanges: List<IntArray>,
        baseYArr: IntArray?,
        i: Int,
        ctx: Context
    ) {
        var segDeckY = deckY
        var placePier = true
        var placeRail = true

        var inRamp = false
        var approachDeck = false

        // 进入区间初始化
        if (!ctx.insideBridgeRange) {
            for (r in bridgeRanges) {
                if (i in r[0]..r[1]) {
                    ctx.insideBridgeRange = true
                    ctx.currentRangeEnd = r[1]
                    break
                }
            }
            ctx.lastBridgeDeckY = null
        }

        val rampN = max(0, cfg.bridgeRampSegments())
        if (rampN > 0 && bridgeRanges.isNotEmpty()) {
            for (r in bridgeRanges) {
                if (i in r[0]..r[1]) {
                    val dStart = i - r[0]
                    val dEnd = r[1] - i
                    if (dStart < rampN || dEnd < rampN) {
                        inRamp = true
                        approachDeck = (dStart <= dEnd)

                        var f = if (dStart < rampN) (dStart / rampN.toDouble()) else (dEnd / rampN.toDouble())
                        f = max(0.0, min(1.0, f))

                        var rampBaseY = baseYForThis
                        if (baseYArr != null && baseYArr.isNotEmpty()) {
                            // 修复：坡道起点应该取桥梁区间外的点，确保与普通道路高度对接
                            if (dStart < rampN) {
                                var idx = max(0, r[0] - 1)
                                idx = min(baseYArr.size - 1, idx)
                                rampBaseY = baseYArr[idx]
                            } else {
                                var idx = min(baseYArr.size - 1, r[1] + 1)
                                idx = max(0, idx)
                                rampBaseY = baseYArr[idx]
                            }
                        }

                        segDeckY = round(rampBaseY + (deckY - rampBaseY) * f).toInt()
                        placePier = false
                        placeRail = false
                    }
                    break
                }
            }
        }

        if (placeRail && bridgeRanges.isNotEmpty()) {
            for (r in bridgeRanges) {
                if (i == r[0] || i == r[1]) {
                    placeRail = false
                    break
                }
            }
        }

        segDeckY = clampDeckY(segDeckY, ctx.lastBridgeDeckY, deckY, cfg, inRamp, approachDeck)
        ctx.lastBridgeDeckY = segDeckY

        if (ctx.insideBridgeRange && i >= ctx.currentRangeEnd) {
            ctx.insideBridgeRange = false
            ctx.currentRangeEnd = -1
            ctx.lastBridgeDeckY = null
        }

        BridgeBuilder.placeSegment(world, seg, middle, prev, next, roadWidth, segDeckY, segmentIndex, random, cfg, placePier, placeRail)
    }
}
