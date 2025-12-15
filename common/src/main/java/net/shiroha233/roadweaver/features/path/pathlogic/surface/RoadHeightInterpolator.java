package net.shiroha233.roadweaver.features.path.pathlogic.surface;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 道路高度插值器
 * 根据道路中心线和预计算的目标高度数组，为路面上任意方块计算插值高度。
 * 
 * 核心原理：
 * 1. 将道路中心线视为一条参数化曲线 (0 → n-1)
 * 2. 对于路面上的每个方块 (x, z)，找到它在中心线上的最近投影点
 * 3. 根据投影点的参数位置，在 targetY 数组中进行线性插值
 * 
 * 这样可以消除"每路段统一高度"导致的锯齿问题，实现平滑的斜向爬坡。
 */
public final class RoadHeightInterpolator {
    private RoadHeightInterpolator() {}

    /**
     * 计算给定方块位置的插值高度
     * 
     * @param x 方块 X 坐标
     * @param z 方块 Z 坐标
     * @param centers 道路中心点列表（有序）
     * @param targetY 每个中心点的目标高度数组
     * @return 插值计算的 Y 高度
     */
    public static int getInterpolatedY(int x, int z, List<BlockPos> centers, int[] targetY) {
        if (centers == null || centers.isEmpty() || targetY == null || targetY.length == 0) {
            return 64; // fallback
        }
        
        int n = centers.size();
        if (n == 1 || targetY.length == 1) {
            return targetY[0];
        }
        
        // 确保 targetY 长度与 centers 匹配
        if (targetY.length != n) {
            return targetY[0];
        }
        
        // 找到 (x, z) 在中心线上的最近投影点及其参数位置
        ProjectionResult proj = findNearestProjection(x, z, centers);
        
        // 根据参数位置在 targetY 中进行线性插值
        return interpolateY(proj.segmentIndex, proj.t, targetY);
    }

    /**
     * 投影结果
     * @param segmentIndex 最近线段的起始索引 (0 ~ n-2)
     * @param t 在该线段上的参数位置 (0.0 ~ 1.0)
     * @param distSq 到投影点的距离平方
     */
    private record ProjectionResult(int segmentIndex, double t, double distSq) {}

    /**
     * 找到点 (x, z) 在中心线上的最近投影点
     */
    private static ProjectionResult findNearestProjection(int x, int z, List<BlockPos> centers) {
        int n = centers.size();
        
        int bestSegment = 0;
        double bestT = 0.0;
        double bestDistSq = Double.MAX_VALUE;
        
        for (int i = 0; i < n - 1; i++) {
            BlockPos a = centers.get(i);
            BlockPos b = centers.get(i + 1);
            
            // 计算点 (x, z) 到线段 (a, b) 的投影
            double ax = a.getX();
            double az = a.getZ();
            double bx = b.getX();
            double bz = b.getZ();
            
            double dx = bx - ax;
            double dz = bz - az;
            double lenSq = dx * dx + dz * dz;
            
            double t;
            if (lenSq < 1e-9) {
                // 退化情况：a 和 b 重合
                t = 0.0;
            } else {
                // 投影参数：t = dot(p-a, b-a) / |b-a|^2
                t = ((x - ax) * dx + (z - az) * dz) / lenSq;
                // 钳制到 [0, 1]
                t = Math.max(0.0, Math.min(1.0, t));
            }
            
            // 投影点坐标
            double projX = ax + t * dx;
            double projZ = az + t * dz;
            
            // 到投影点的距离平方
            double distSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ);
            
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestSegment = i;
                bestT = t;
            }
        }
        
        return new ProjectionResult(bestSegment, bestT, bestDistSq);
    }

    /**
     * 根据线段索引和参数位置，在 targetY 数组中进行线性插值
     */
    private static int interpolateY(int segmentIndex, double t, int[] targetY) {
        int y0 = targetY[segmentIndex];
        int y1 = targetY[segmentIndex + 1];
        
        // 线性插值
        double interpolated = y0 + t * (y1 - y0);
        return (int) Math.round(interpolated);
    }

    /**
     * 批量计算一组方块的插值高度（优化版本，减少重复搜索）
     * 
     * @param positions 方块位置列表
     * @param segmentIndex 当前路段索引（用于局部搜索优化）
     * @param centers 道路中心点列表
     * @param targetY 目标高度数组
     * @return 每个位置对应的插值高度数组
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
        
        // 性能优化：使用扩展的搜索范围而非全局搜索
        // 道路宽度通常不超过15，所以搜索范围扩展到 segmentIndex ± 20 应该足够
        // 这比全局搜索快得多，同时能覆盖宽道路在弯道处的情况
        int extendedRadius = 20;
        int searchStart = Math.max(0, segmentIndex - extendedRadius);
        int searchEnd = Math.min(n - 2, segmentIndex + extendedRadius);
        
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            int x = pos.getX();
            int z = pos.getZ();
            
            // 在扩展范围内搜索最近投影
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
            
            int resultY = interpolateY(bestSeg, bestT, targetY);
            results[i] = resultY;
        }
        
        return results;
    }
}
