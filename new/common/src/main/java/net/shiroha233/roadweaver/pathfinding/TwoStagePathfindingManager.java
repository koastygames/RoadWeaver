package net.shiroha233.roadweaver.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;

import java.util.ArrayList;
import java.util.List;

/**
 * 两阶段寻路管理器：对路径分段执行基于步幅采样 + 线性插值的高度精修
 */
public final class TwoStagePathfindingManager {
    private TwoStagePathfindingManager() {}

    public static List<RoadSegmentPlacement> refineSegmentHeights(ServerLevel level,
                                                                  List<RoadSegmentPlacement> segments,
                                                                  AccurateHeightSampler accurate) {
        if (level == null || segments == null || segments.isEmpty() || accurate == null) {
            return segments;
        }

        int n = segments.size();
        int stride;
        if (n <= 512) stride = 1;
        else if (n <= 2048) stride = 2;
        else if (n <= 8192) stride = 4;
        else stride = 8;

        int[] sampledY = new int[n];
        boolean[] sampled = new boolean[n];

        for (int i = 0; i < n; i += stride) {
            RoadSegmentPlacement seg = segments.get(i);
            if (seg == null) continue;
            BlockPos mid = seg.middlePos();
            sampledY[i] = accurate.surfaceHeight(mid.getX(), mid.getZ());
            sampled[i] = true;
        }

        int lastIdx = n - 1;
        if (!sampled[lastIdx]) {
            RoadSegmentPlacement seg = segments.get(lastIdx);
            if (seg != null) {
                BlockPos mid = seg.middlePos();
                sampledY[lastIdx] = accurate.surfaceHeight(mid.getX(), mid.getZ());
                sampled[lastIdx] = true;
            }
        }

        int prev = -1;
        for (int i = 0; i < n; i++) {
            if (!sampled[i]) continue;
            if (prev >= 0 && i > prev + 1) {
                int y0 = sampledY[prev];
                int y1 = sampledY[i];
                int span = i - prev;
                for (int j = prev + 1; j < i; j++) {
                    double t = (j - prev) / (double) span;
                    sampledY[j] = (int) Math.round(y0 + (y1 - y0) * t);
                }
            }
            prev = i;
        }

        List<RoadSegmentPlacement> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            RoadSegmentPlacement seg = segments.get(i);
            if (seg == null) continue;
            BlockPos mid = seg.middlePos();
            out.add(new RoadSegmentPlacement(
                    new BlockPos(mid.getX(), sampledY[i], mid.getZ()),
                    seg.positions()));
        }
        return out;
    }
}
