package net.shiroha233.roadweaver.features.path.pathlogic.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.features.path.bridge.BridgeBuilder;
import net.shiroha233.roadweaver.helpers.Records;

import java.util.List;

/**
 * 桥梁段规划器
 * 处理桥梁坡道、高度限制，调用 BridgeBuilder 放置桥梁
 */
public final class BridgeSegmentPlanner {
    private BridgeSegmentPlanner() {}

    /** 桥梁生成上下文，跟踪桥梁区间状态 */
    public static final class Context {
        public boolean insideBridgeRange = false;
        public int currentRangeEnd = -1;
        public Integer lastBridgeDeckY = null;
    }

    public static Context newContext() {
        return new Context();
    }

    /** 限制桥面高度变化 */
    private static int clampDeckY(int candidateDeckY,
                                  Integer lastDeckY,
                                  int deckY,
                                  ModConfig cfg,
                                  boolean inRamp,
                                  boolean approachDeck) {
        int segDeckY = candidateDeckY;
        if (lastDeckY != null) {
            int stepDeck = Math.max(0, Math.min(8, cfg.maxSlopeStepPerTwoSegments()));
            if (cfg.slopeLimitEnabled() && stepDeck > 0) {
                if (segDeckY > lastDeckY + stepDeck)
                    segDeckY = lastDeckY + stepDeck;
                if (segDeckY < lastDeckY - stepDeck)
                    segDeckY = lastDeckY - stepDeck;
            }

            if (inRamp) {
                int prevDist = Math.abs(lastDeckY - deckY);
                int newDist = Math.abs(segDeckY - deckY);
                if (approachDeck) {
                    if (newDist > prevDist) {
                        segDeckY = lastDeckY;
                    }
                } else {
                    if (newDist < prevDist) {
                        segDeckY = lastDeckY;
                    }
                }
            }
        }
        return segDeckY;
    }

    /**
     * 处理单个桥梁段
     */
    public static void processSegment(WorldGenLevel world,
                                      Records.RoadSegmentPlacement seg,
                                      BlockPos middle,
                                      BlockPos prev,
                                      BlockPos next,
                                      int roadWidth,
                                      int baseYForThis,
                                      int deckY,
                                      int segmentIndex,
                                      RandomSource random,
                                      ModConfig cfg,
                                      List<int[]> bridgeRanges,
                                      int[] baseYArr,
                                      int i,
                                      Context ctx) {
        int segDeckY = deckY;
        boolean placePier = true;
        boolean placeRail = true;

        boolean inRamp = false;
        boolean approachDeck = false;

        // 进入区间初始化
        if (!ctx.insideBridgeRange) {
            for (int[] r : bridgeRanges) {
                if (i >= r[0] && i <= r[1]) {
                    ctx.insideBridgeRange = true;
                    ctx.currentRangeEnd = r[1];
                    break;
                }
            }
            ctx.lastBridgeDeckY = null;
        }

        int rampN = Math.max(0, cfg.bridgeRampSegments());
        if (rampN > 0 && !bridgeRanges.isEmpty()) {
            for (int[] r : bridgeRanges) {
                if (i >= r[0] && i <= r[1]) {
                    int dStart = i - r[0];
                    int dEnd = r[1] - i;
                    if (dStart < rampN || dEnd < rampN) {
                        inRamp = true;
                        approachDeck = (dStart <= dEnd);

                        double f = (dStart < rampN) ? (dStart / (double) rampN) : (dEnd / (double) rampN);
                        f = Math.max(0.0, Math.min(1.0, f));
                        int rampBaseY = baseYForThis;
                        if (baseYArr != null && baseYArr.length > 0) {
                            // 坡道起点取桥梁区间外的点，确保与普通道路高度对接
                            if (dStart < rampN) {
                                int idx = Math.max(0, r[0] - 1);
                                idx = Math.min(baseYArr.length - 1, idx);
                                rampBaseY = baseYArr[idx];
                            } else {
                                int idx = Math.min(baseYArr.length - 1, r[1] + 1);
                                idx = Math.max(0, idx);
                                rampBaseY = baseYArr[idx];
                            }
                        }

                        segDeckY = (int) Math.round(rampBaseY + (deckY - rampBaseY) * f);
                        placePier = false;
                        placeRail = false;
                    }
                    break;
                }
            }
        }

        // 桥梁端点不放栏杆
        if (placeRail && !bridgeRanges.isEmpty()) {
            for (int[] r : bridgeRanges) {
                if (i == r[0] || i == r[1]) {
                    placeRail = false;
                    break;
                }
            }
        }

        segDeckY = clampDeckY(segDeckY, ctx.lastBridgeDeckY, deckY, cfg, inRamp, approachDeck);
        ctx.lastBridgeDeckY = segDeckY;

        // 离开区间重置
        if (ctx.insideBridgeRange && i >= ctx.currentRangeEnd) {
            ctx.insideBridgeRange = false;
            ctx.currentRangeEnd = -1;
            ctx.lastBridgeDeckY = null;
        }

        BridgeBuilder.placeSegment(world, seg, middle, prev, next, roadWidth, segDeckY, 
                segmentIndex, random, cfg, placePier, placeRail);
    }
}
