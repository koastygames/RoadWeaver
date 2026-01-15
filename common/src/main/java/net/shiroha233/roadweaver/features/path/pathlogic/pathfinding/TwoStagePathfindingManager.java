package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.helpers.Records;

import java.util.ArrayList;
import java.util.List;

/**
 * 双层寻路管理器：
 * - 第一层：使用 FastHeightSampler（TerrainSamplingCache）完成快速寻路与几何后处理
 * - 第二层：路径确定后，对中心线节点使用 ChunkGenerator#getBaseHeight 进行二次精采样
 *
 * 这样可以把“性能敏感的搜索”与“需要真实高度的铺设/平滑”解耦，避免整体性能退化。
 */
public final class TwoStagePathfindingManager {
    private TwoStagePathfindingManager() {
    }

    public static List<Records.RoadSegmentPlacement> refineSegmentHeights(ServerLevel level,
                                                                         List<Records.RoadSegmentPlacement> segments,
                                                                         AccurateHeightSampler accurate) {
        if (level == null || segments == null || segments.isEmpty() || accurate == null) {
            return segments;
        }

        int n = segments.size();
        int stride;
        if (n <= 512) {
            stride = 1;
        } else if (n <= 2048) {
            stride = 2;
        } else if (n <= 8192) {
            stride = 4;
        } else {
            stride = 8;
        }

        // 仅对抽样点调用 getBaseHeight；其余点按区间线性插值。
        int[] sampledY = new int[n];
        boolean[] sampled = new boolean[n];

        for (int i = 0; i < n; i += stride) {
            Records.RoadSegmentPlacement seg = segments.get(i);
            if (seg == null) {
                continue;
            }
            BlockPos mid = seg.middlePos();
            sampledY[i] = accurate.surfaceHeight(mid.getX(), mid.getZ());
            sampled[i] = true;
        }

        // 确保末尾有采样点，避免尾段全靠外推
        int lastIdx = n - 1;
        if (!sampled[lastIdx]) {
            Records.RoadSegmentPlacement seg = segments.get(lastIdx);
            if (seg != null) {
                BlockPos mid = seg.middlePos();
                sampledY[lastIdx] = accurate.surfaceHeight(mid.getX(), mid.getZ());
                sampled[lastIdx] = true;
            }
        }

        // 区间插值填充
        int prev = -1;
        for (int i = 0; i < n; i++) {
            if (!sampled[i]) {
                continue;
            }
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

        // 生成替换后的 segments
        List<Records.RoadSegmentPlacement> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Records.RoadSegmentPlacement seg = segments.get(i);
            if (seg == null) {
                continue;
            }
            BlockPos mid = seg.middlePos();
            out.add(new Records.RoadSegmentPlacement(
                    new BlockPos(mid.getX(), sampledY[i], mid.getZ()),
                    seg.positions()));
        }
        return out;
    }
}
