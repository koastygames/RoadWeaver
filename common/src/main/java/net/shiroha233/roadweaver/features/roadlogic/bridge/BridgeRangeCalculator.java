package net.shiroha233.roadweaver.features.roadlogic.bridge;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.helpers.Records;

public final class BridgeRangeCalculator {
    private BridgeRangeCalculator() {}

    public static record RangeResult(boolean[] isBridge, java.util.List<int[]> mergedRanges) {}

    public static RangeResult compute(java.util.List<BlockPos> middlePositions, java.util.List<Records.RoadSpan> spans) {
        int n = middlePositions.size();
        boolean[] isBridge = new boolean[n];
        java.util.List<int[]> bridgeRanges = new java.util.ArrayList<>();
        
        ModConfig cfg = ConfigService.get();
        int minLength = Math.max(1, cfg.bridgeMinLength());
        int mergeGap = Math.max(1, cfg.bridgeMergeGap());
        
        if (spans != null) {
            java.util.Map<Long, Integer> indexMap = new java.util.HashMap<>();
            for (int i = 0; i < n; i++) indexMap.put(middlePositions.get(i).asLong(), i);
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
        
        if (!bridgeRanges.isEmpty()) {
            // 按起点排序
            bridgeRanges.sort(java.util.Comparator.comparingInt(o -> o[0]));
            
            // 第一轮：合并间隔小于 mergeGap 的区间（避免频繁起伏）
            java.util.List<int[]> merged = new java.util.ArrayList<>();
            int[] cur = bridgeRanges.get(0);
            for (int idx = 1; idx < bridgeRanges.size(); idx++) {
                int[] nxt = bridgeRanges.get(idx);
                // 如果下一个区间的起点距离当前区间的终点 <= mergeGap，则合并
                if (nxt[0] <= cur[1] + mergeGap) {
                    cur[1] = Math.max(cur[1], nxt[1]);
                } else {
                    merged.add(cur);
                    cur = nxt;
                }
            }
            merged.add(cur);
            
            // 第二轮：过滤掉太短的桥梁区间（避免小水坑建桥）
            java.util.List<int[]> filtered = new java.util.ArrayList<>();
            for (int[] r : merged) {
                int len = r[1] - r[0] + 1;
                if (len >= minLength) {
                    filtered.add(r);
                }
            }
            bridgeRanges = filtered;
        }
        
        // 根据最终的桥梁区间设置 isBridge 标记
        for (int[] r : bridgeRanges) {
            for (int k = r[0]; k <= r[1]; k++) {
                isBridge[k] = true;
            }
        }
        
        return new RangeResult(isBridge, bridgeRanges);
    }
}
