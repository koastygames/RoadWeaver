package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Road height interpolation helpers.
 *
 * 3.1.0 adds a segment-local projection path for callers that already know the
 * current road segment. This avoids repeatedly scanning an entire long road
 * while paving slabs and other per-block features.
 */
public final class RoadHeightInterpolator {
    private static final int LOCAL_SEARCH_RADIUS = 20;

    private RoadHeightInterpolator() {}

    public static int getInterpolatedY(int x, int z, List<BlockPos> centers, int[] targetY) {
        if (!valid(centers, targetY)) {
            return 64;
        }

        int n = centers.size();
        if (n == 1 || targetY.length == 1) {
            return targetY[0];
        }

        ProjectionResult proj = findNearestProjection(x, z, centers, 0, n - 2);
        return interpolateY(proj.segmentIndex, proj.t, targetY);
    }

    /**
     * Projects onto only the road segments around the supplied segment index.
     * Use this from per-block generation code where the active segment is known.
     */
    public static int getInterpolatedYNear(int x, int z, int segmentIndex,
            List<BlockPos> centers, int[] targetY) {
        if (!valid(centers, targetY)) {
            return 64;
        }

        int n = centers.size();
        if (n == 1 || targetY.length == 1) {
            return targetY[0];
        }

        int clampedSegment = Math.max(0, Math.min(segmentIndex, n - 2));
        int start = Math.max(0, clampedSegment - LOCAL_SEARCH_RADIUS);
        int end = Math.min(n - 2, clampedSegment + LOCAL_SEARCH_RADIUS);
        ProjectionResult proj = findNearestProjection(x, z, centers, start, end);
        return interpolateY(proj.segmentIndex, proj.t, targetY);
    }

    private static boolean valid(List<BlockPos> centers, int[] targetY) {
        return centers != null && !centers.isEmpty()
                && targetY != null && targetY.length > 0
                && (centers.size() == 1 || targetY.length == centers.size());
    }

    private record ProjectionResult(int segmentIndex, double t, double distSq) {}

    private static ProjectionResult findNearestProjection(int x, int z, List<BlockPos> centers,
            int searchStart, int searchEnd) {
        int bestSegment = searchStart;
        double bestT = 0.0;
        double bestDistSq = Double.MAX_VALUE;

        for (int i = searchStart; i <= searchEnd; i++) {
            BlockPos a = centers.get(i);
            BlockPos b = centers.get(i + 1);

            double ax = a.getX();
            double az = a.getZ();
            double bx = b.getX();
            double bz = b.getZ();
            double dx = bx - ax;
            double dz = bz - az;
            double lenSq = dx * dx + dz * dz;

            double t;
            if (lenSq < 1e-9) {
                t = 0.0;
            } else {
                t = ((x - ax) * dx + (z - az) * dz) / lenSq;
                t = Math.max(0.0, Math.min(1.0, t));
            }

            double projX = ax + t * dx;
            double projZ = az + t * dz;
            double distSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ);

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestSegment = i;
                bestT = t;
            }
        }

        return new ProjectionResult(bestSegment, bestT, bestDistSq);
    }

    private static int interpolateY(int segmentIndex, double t, int[] targetY) {
        int y0 = targetY[segmentIndex];
        int y1 = targetY[segmentIndex + 1];
        return (int) Math.round(y0 + t * (y1 - y0));
    }

    public static int[] batchInterpolate(List<BlockPos> positions,
            int segmentIndex,
            List<BlockPos> centers,
            int[] targetY) {
        if (positions == null || positions.isEmpty()) {
            return new int[0];
        }

        int[] results = new int[positions.size()];
        if (!valid(centers, targetY)) {
            java.util.Arrays.fill(results, 64);
            return results;
        }

        int n = centers.size();
        if (n == 1 || targetY.length == 1) {
            java.util.Arrays.fill(results, targetY[0]);
            return results;
        }

        int clampedSegment = Math.max(0, Math.min(segmentIndex, n - 2));
        int searchStart = Math.max(0, clampedSegment - LOCAL_SEARCH_RADIUS);
        int searchEnd = Math.min(n - 2, clampedSegment + LOCAL_SEARCH_RADIUS);

        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            ProjectionResult proj = findNearestProjection(pos.getX(), pos.getZ(), centers,
                    searchStart, searchEnd);
            results[i] = interpolateY(proj.segmentIndex, proj.t, targetY);
        }

        return results;
    }
}
