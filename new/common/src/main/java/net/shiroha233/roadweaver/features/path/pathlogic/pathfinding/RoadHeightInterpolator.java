package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 道路高度插值器
 * 根据道路中心线和预计算的目标高度数组，为路面上任意方块计算插值高度
 */
public final class RoadHeightInterpolator {
    private RoadHeightInterpolator() {}

    /**
     * 计算给定方块位置的插值高度
     */
    public static int getInterpolatedY(int x, int z, List<BlockPos> centers, int[] targetY) {
        if (centers == null || centers.isEmpty() || targetY == null || targetY.length == 0) {
            return 64;
        }
        
        int n = centers.size();
        if (n == 1 || targetY.length == 1) {
            return targetY[0];
        }
        
        if (targetY.length != n) {
            return targetY[0];
        }
        
        ProjectionResult proj = findNearestProjection(x, z, centers);
        return interpolateY(proj.segmentIndex, proj.t, targetY);
    }

    private record ProjectionResult(int segmentIndex, double t, double distSq) {}

    private static ProjectionResult findNearestProjection(int x, int z, List<BlockPos> centers) {
        int n = centers.size();
        
        int bestSegment = 0;
        double bestT = 0.0;
        double bestDistSq = Double.MAX_VALUE;
        
        for (int i = 0; i < n - 1; i++) {
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
        double interpolated = y0 + t * (y1 - y0);
        return (int) Math.round(interpolated);
    }

    /**
     * 批量计算一组方块的插值高度（优化版本，减少重复搜索）
     */
    public static int[] batchInterpolate(List<BlockPos> positions, 
                                         int segmentIndex,
                                         List<BlockPos> centers, 
                                         int[] targetY) {
        if (positions == null || positions.isEmpty()) {
            return new int[0];
        }
        
        int[] results = new int[positions.size()];
        int n = centers.size();

        int extendedRadius = 20;
        int searchStart = Math.max(0, segmentIndex - extendedRadius);
        int searchEnd = Math.min(n - 2, segmentIndex + extendedRadius);

        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            int x = pos.getX();
            int z = pos.getZ();

            int bestSeg = segmentIndex;
            double bestT = 0.5;
            double bestDistSq = Double.MAX_VALUE;
            
            for (int seg = searchStart; seg <= searchEnd; seg++) {
                BlockPos a = centers.get(seg);
                BlockPos b = centers.get(seg + 1);
                
                double ax = a.getX(), az = a.getZ();
                double bx = b.getX(), bz = b.getZ();
                double dx = bx - ax, dz = bz - az;
                double lenSq = dx * dx + dz * dz;
                
                double t = (lenSq < 1e-9) ? 0.0 
                    : Math.max(0.0, Math.min(1.0, ((x - ax) * dx + (z - az) * dz) / lenSq));
                
                double projX = ax + t * dx;
                double projZ = az + t * dz;
                double distSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ);
                
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestSeg = seg;
                    bestT = t;
                }
            }
            
            results[i] = interpolateY(bestSeg, bestT, targetY);
        }
        
        return results;
    }
}
