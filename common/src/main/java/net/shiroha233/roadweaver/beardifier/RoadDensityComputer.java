package net.shiroha233.roadweaver.beardifier;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 道路密度贡献计算器，供 Beardifier Mixin 使用
 */
public final class RoadDensityComputer {
    private RoadDensityComputer() {}

    private static final int BANK_WIDTH = 3;
    private static final double MAX_FILL_CONTRIBUTION = 0.8;
    private static final double MAX_CARVE_CONTRIBUTION = 1.0;
    private static final int VERTICAL_RANGE = 8;
    private static final int QUERY_MARGIN = 16;

    public record Segment(int x0, int z0, int y0, int x1, int z1, int y1, int halfWidth) {}

    /**
     * 从 RoadData 列表中提取与指定区块相关的道路中心线段
     */
    public static List<Segment> extractSegmentsForChunk(List<RoadData> roads, int chunkMinX, int chunkMinZ, int chunkMaxX, int chunkMaxZ) {
        if (roads == null || roads.isEmpty()) return Collections.emptyList();

        int qMinX = chunkMinX - QUERY_MARGIN;
        int qMaxX = chunkMaxX + QUERY_MARGIN;
        int qMinZ = chunkMinZ - QUERY_MARGIN;
        int qMaxZ = chunkMaxZ + QUERY_MARGIN;

        List<Segment> result = new ArrayList<>();
        for (RoadData data : roads) {
            List<RoadSegmentPlacement> segs = data.roadSegmentList();
            if (segs == null || segs.size() < 2) continue;

            List<Integer> ty = data.targetY();
            int hw = (data.width() + 1) / 2;

            for (int i = 0; i < segs.size() - 1; i++) {
                BlockPos a = segs.get(i).middlePos();
                BlockPos b = segs.get(i + 1).middlePos();

                int segMinX = Math.min(a.getX(), b.getX()) - hw - BANK_WIDTH;
                int segMaxX = Math.max(a.getX(), b.getX()) + hw + BANK_WIDTH;
                int segMinZ = Math.min(a.getZ(), b.getZ()) - hw - BANK_WIDTH;
                int segMaxZ = Math.max(a.getZ(), b.getZ()) + hw + BANK_WIDTH;

                if (segMaxX < qMinX || segMinX > qMaxX || segMaxZ < qMinZ || segMinZ > qMaxZ) {
                    continue;
                }

                int ya = (ty != null && i < ty.size()) ? ty.get(i) : a.getY();
                int yb = (ty != null && i + 1 < ty.size()) ? ty.get(i + 1) : b.getY();
                result.add(new Segment(a.getX(), a.getZ(), ya, b.getX(), b.getZ(), yb, hw));
            }
        }
        return result;
    }

    /**
     * 计算指定坐标的道路密度贡献（正=填充路基，负=切削地形）
     */
    public static double compute(int x, int y, int z, List<Segment> segments, int clearHeight) {
        if (segments == null || segments.isEmpty()) return 0.0;

        double bestFill = 0.0;
        double bestCarve = 0.0;
        for (int i = 0, n = segments.size(); i < n; i++) {
            double c = computeForSegment(x, y, z, segments.get(i), clearHeight);
            if (c > 0 && c > bestFill) bestFill = c;
            if (c < 0 && c < bestCarve) bestCarve = c;
        }
        if (bestFill > 0.0) return bestFill;
        return bestCarve;
    }

    private static double computeForSegment(int x, int y, int z, Segment seg, int clearHeight) {
        double dx = seg.x1 - seg.x0;
        double dz = seg.z1 - seg.z0;
        double lenSq = dx * dx + dz * dz;

        double t;
        if (lenSq < 1e-9) {
            t = 0.0;
        } else {
            t = ((x - seg.x0) * dx + (z - seg.z0) * dz) / lenSq;
            t = Math.max(0.0, Math.min(1.0, t));
        }

        double projX = seg.x0 + t * dx;
        double projZ = seg.z0 + t * dz;

        double latDistSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ);

        double targetY = seg.y0 + t * (seg.y1 - seg.y0);
        int roadSurfaceY = (int) Math.round(targetY);

        if (y < roadSurfaceY) {
            double totalWidth = seg.halfWidth + BANK_WIDTH;
            if (latDistSq > totalWidth * totalWidth) return 0.0;
            double latDist = Math.sqrt(latDistSq);

            double lateralFactor;
            if (latDist <= seg.halfWidth) {
                lateralFactor = 1.0;
            } else {
                double edge = (latDist - seg.halfWidth) / BANK_WIDTH;
                lateralFactor = 1.0 - edge * edge * (3.0 - 2.0 * edge);
            }

            int belowRoad = roadSurfaceY - 1 - y;
            if (belowRoad > VERTICAL_RANGE) return 0.0;
            double verticalFactor = Mth.clampedMap(belowRoad, 0, VERTICAL_RANGE, MAX_FILL_CONTRIBUTION, 0.0);
            return lateralFactor * verticalFactor;
        } else if (y > roadSurfaceY && y <= roadSurfaceY + clearHeight) {
            if (latDistSq > (double) seg.halfWidth * seg.halfWidth) return 0.0;

            int aboveRoad = y - roadSurfaceY;
            double verticalFactor = Mth.clampedMap(aboveRoad, 1, clearHeight, -MAX_CARVE_CONTRIBUTION, 0.0);
            return verticalFactor;
        }

        return 0.0;
    }
}
