package net.shiroha233.roadweaver.features.path.pathlogic.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.helpers.Records;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 桥梁范围计算器
 * 
 * 1.20.1 重构：支持两种模式
 * 1. 预计算模式：使用寻路阶段存储的 spans 数据（兼容旧数据）
 * 2. 实时检测模式：在区块生成阶段使用 RealTimeWaterDetector 实时检测水域
 */
public final class BridgeRangeCalculator {
    private BridgeRangeCalculator() {}

    /** 计算结果 */
    public record RangeResult(boolean[] isBridge, List<int[]> mergedRanges, boolean[] skipSegments) {}

    private static double dist2d(BlockPos a, BlockPos b) {
        long dx = (long) b.getX() - a.getX();
        long dz = (long) b.getZ() - a.getZ();
        return Math.sqrt((double) dx * dx + (double) dz * dz);
    }

    /** 估算区间长度（方块） */
    private static int estimateRangeLengthBlocks(List<BlockPos> middlePositions, int a, int b) {
        if (middlePositions == null || middlePositions.isEmpty()) return 0;
        int n = middlePositions.size();
        int from = Math.max(0, Math.min(n - 1, a));
        int to = Math.max(0, Math.min(n - 1, b));
        if (from > to) {
            int t = from; from = to; to = t;
        }
        double sum = 0.0;
        for (int i = from; i < to; i++) {
            sum += dist2d(middlePositions.get(i), middlePositions.get(i + 1));
        }
        return (int) Math.round(sum);
    }

    public static RangeResult compute(List<BlockPos> middlePositions, List<Records.RoadSpan> spans) {
        ModConfig cfg = ConfigService.get();
        return compute(middlePositions, spans, cfg, null);
    }

    public static RangeResult compute(List<BlockPos> middlePositions,
                                      List<Records.RoadSpan> spans,
                                      ModConfig cfg,
                                      String dimensionId) {
        int n = middlePositions.size();
        boolean[] isBridge = new boolean[n];
        boolean[] skipSegments = new boolean[n];
        List<int[]> bridgeRanges = new ArrayList<>();

        if (cfg == null) {
            return new RangeResult(isBridge, List.of(), skipSegments);
        }

        boolean bridgeEnabled = (dimensionId == null || dimensionId.isEmpty())
                ? cfg.bridgeEnabled()
                : cfg.bridgeEnabledForDimension(dimensionId);
        if (!bridgeEnabled) {
            return new RangeResult(isBridge, List.of(), skipSegments);
        }

        // 从预计算的 spans 中提取桥梁区间（兼容旧数据）
        if (spans != null && !spans.isEmpty()) {
            Map<Long, Integer> indexMap = new HashMap<>();
            for (int i = 0; i < n; i++) {
                indexMap.put(middlePositions.get(i).asLong(), i);
            }
            for (Records.RoadSpan sp : spans) {
                if (sp.type() != Records.SpanType.BRIDGE) continue;
                Integer si = indexMap.get(sp.start().asLong());
                Integer ei = indexMap.get(sp.end().asLong());
                if (si == null || ei == null) continue;
                int a = Math.max(0, Math.min(si, ei));
                int b = Math.min(n - 1, Math.max(si, ei));
                bridgeRanges.add(new int[]{a, b});
            }
        }

        return postProcess(middlePositions, bridgeRanges, isBridge, skipSegments, cfg);
    }

    /**
     * 实时检测模式：在区块生成阶段使用实际地形数据检测水域
     * 
     * @param world           世界生成上下文
     * @param middlePositions 道路中心点列表
     * @param roadWidth       道路宽度
     * @param cfg             配置
     * @param dimensionId     维度ID
     * @return 桥梁范围计算结果
     */
    public static RangeResult computeRealTime(WorldGenLevel world,
                                              List<BlockPos> middlePositions,
                                              int roadWidth,
                                              ModConfig cfg,
                                              String dimensionId) {
        int n = middlePositions.size();
        boolean[] isBridge = new boolean[n];
        boolean[] skipSegments = new boolean[n];
        List<int[]> bridgeRanges = new ArrayList<>();

        if (cfg == null || world == null) {
            return new RangeResult(isBridge, List.of(), skipSegments);
        }

        boolean bridgeEnabled = (dimensionId == null || dimensionId.isEmpty())
                ? cfg.bridgeEnabled()
                : cfg.bridgeEnabledForDimension(dimensionId);
        if (!bridgeEnabled) {
            return new RangeResult(isBridge, List.of(), skipSegments);
        }

        // 获取海平面高度
        int seaLevel = 63; // 默认值
        if (world.getLevel() instanceof ServerLevel) {
            seaLevel = ((ServerLevel) world.getLevel()).getSeaLevel();
        }
        int minDepth = Math.max(1, cfg.bridgeMinWaterDepth());

        // 实时检测每个路段是否为水域
        boolean[] rawWater = new boolean[n];
        for (int i = 0; i < n; i++) {
            BlockPos pos = middlePositions.get(i);
            rawWater[i] = RealTimeWaterDetector.shouldBeBridge(
                    world, pos.getX(), pos.getZ(), roadWidth, seaLevel, minDepth);
        }

        // 将连续的水域段转换为桥梁区间
        int start = -1;
        for (int i = 0; i < n; i++) {
            if (rawWater[i]) {
                if (start < 0) start = i;
            } else {
                if (start >= 0) {
                    bridgeRanges.add(new int[]{start, i - 1});
                    start = -1;
                }
            }
        }
        if (start >= 0) {
            bridgeRanges.add(new int[]{start, n - 1});
        }

        return postProcess(middlePositions, bridgeRanges, isBridge, skipSegments, cfg);
    }

    /** 后处理：合并、过滤、标记 */
    private static RangeResult postProcess(List<BlockPos> middlePositions,
                                           List<int[]> bridgeRanges,
                                           boolean[] isBridge,
                                           boolean[] skipSegments,
                                           ModConfig cfg) {
        int n = middlePositions.size();
        int minLength = Math.max(1, cfg.bridgeMinLength());
        int mergeGap = Math.max(1, cfg.bridgeMergeGap());
        boolean useBuoysInstead = cfg.bridgeUseBuoysInstead();
        int maxLenBlocks = useBuoysInstead ? 0 : Math.max(0, cfg.bridgeMaxLengthBlocks());

        if (!bridgeRanges.isEmpty()) {
            // 按起点排序
            bridgeRanges.sort(Comparator.comparingInt(o -> o[0]));

            // 第一轮：合并间隔小于 mergeGap 的区间
            List<int[]> merged = new ArrayList<>();
            int[] cur = bridgeRanges.get(0);
            for (int idx = 1; idx < bridgeRanges.size(); idx++) {
                int[] nxt = bridgeRanges.get(idx);
                if (nxt[0] <= cur[1] + mergeGap) {
                    cur[1] = Math.max(cur[1], nxt[1]);
                } else {
                    merged.add(cur);
                    cur = nxt;
                }
            }
            merged.add(cur);

            // 第二轮：过滤掉太短的桥梁区间
            List<int[]> filtered = new ArrayList<>();
            for (int[] r : merged) {
                int len = r[1] - r[0] + 1;
                if (len >= minLength) {
                    filtered.add(r);
                }
            }

            // 第三轮：过滤掉过长的桥梁区间，并标记需要跳过的水域段
            List<int[]> filteredByMaxLen = new ArrayList<>();
            for (int[] r : filtered) {
                if (maxLenBlocks <= 0) {
                    filteredByMaxLen.add(r);
                    continue;
                }
                int approxLen = estimateRangeLengthBlocks(middlePositions, r[0], r[1]);
                if (approxLen > maxLenBlocks) {
                    // 超长水域跨度：整段跳过生成
                    int s = Math.max(0, r[0]);
                    int e = Math.min(n - 1, r[1]);
                    for (int k = s; k <= e; k++) {
                        skipSegments[k] = true;
                    }
                } else {
                    filteredByMaxLen.add(r);
                }
            }
            bridgeRanges = filteredByMaxLen;
        }

        // 根据最终的桥梁区间设置 isBridge 标记
        for (int[] r : bridgeRanges) {
            for (int k = r[0]; k <= r[1]; k++) {
                isBridge[k] = true;
            }
        }

        return new RangeResult(isBridge, bridgeRanges, skipSegments);
    }
}
