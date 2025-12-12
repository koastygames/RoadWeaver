package net.shiroha233.roadweaver.features.roadlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.helpers.Records;

import java.util.*;

public final class PathPostProcessor {
    private PathPostProcessor() {}

    /**
     * 将原始寻路节点列表转换为平滑的、具有宽度的路段列表。
     * 使用 Catmull-Rom 样条曲线生成平滑路径，并通过距离场光栅化生成路面。
     */
    public static List<Records.RoadSegmentPlacement> process(List<BlockPos> rawPath,
                                                             int width,
                                                             ServerLevel level,
                                                             TerrainSamplingCache cache,
                                                             int bridgeMinWaterDepth) {
        if (rawPath == null || rawPath.size() < 2) return new ArrayList<>();

        // 1. 路径简化
        List<BlockPos> simplified = simplifyPath(rawPath);
        boolean[] bridgeMask = detectBridgeMask(simplified, level, cache, bridgeMinWaterDepth);
        // 桥梁段不做曲线松弛/样条平滑，否则会出现弯桥
        List<BlockPos> controlPoints = relaxPathSkippingBridge(simplified, bridgeMask);
        if (controlPoints.size() < 2) return new ArrayList<>();

        // 2. 生成高密度样条曲线点集
        List<BlockPos> extendedPoints = new ArrayList<>();
        extendedPoints.add(controlPoints.get(0));
        extendedPoints.addAll(controlPoints);
        extendedPoints.add(controlPoints.get(controlPoints.size() - 1));

        List<Vec2d> splinePoints = new ArrayList<>();
        
        for (int i = 0; i < controlPoints.size() - 1; i++) {
            BlockPos p0 = extendedPoints.get(i);
            BlockPos p1 = extendedPoints.get(i + 1);
            BlockPos p2 = extendedPoints.get(i + 2);
            BlockPos p3 = extendedPoints.get(i + 3);

            double dist = Math.sqrt(p1.distSqr(p2));
            int steps = (int) Math.ceil(dist * 4.0); // 极高密度采样
            if (steps < 1) steps = 1;

            boolean bridgeSeg = (i >= 0 && i < bridgeMask.length - 1) && (bridgeMask[i] || bridgeMask[i + 1]);

            for (int s = 0; s < steps; s++) {
                double t = (double) s / steps;
                Vec2d pt = bridgeSeg
                        ? lerp2d(p1, p2, t)
                        : catmullRomSplineDouble(p0, p1, p2, p3, t);
                splinePoints.add(pt);
            }
        }
        // 添加最后一点
        BlockPos lastBP = controlPoints.get(controlPoints.size() - 1);
        Vec2d lastPt = new Vec2d(lastBP.getX(), lastBP.getZ());
        splinePoints.add(lastPt);

        // 3. 提取骨架中心点 (Centers)
        List<BlockPos> centers = new ArrayList<>();
        List<Double> centerDists = new ArrayList<>();
        
        double currentDist = 0;
        double nextCenterDist = 0;
        
        for (int i = 0; i < splinePoints.size(); i++) {
            Vec2d p = splinePoints.get(i);
            if (i > 0) {
                currentDist += p.dist(splinePoints.get(i - 1));
            }
            
            if (currentDist >= nextCenterDist || i == splinePoints.size() - 1) {
                int y = RoadPathCalculator.heightSampler(cache, (int)Math.round(p.x), (int)Math.round(p.z), level);
                BlockPos centerPos = new BlockPos((int)Math.round(p.x), y, (int)Math.round(p.z));
                
                if (centers.isEmpty() || !centers.get(centers.size() - 1).equals(centerPos)) {
                    centers.add(centerPos);
                    centerDists.add(currentDist);
                    nextCenterDist = currentDist + 1.0;
                }
            }
        }

        // 4. 距离场光栅化 & 归仓
        Map<Integer, Set<BlockPos>> segmentedBlocks = new HashMap<>();
        for (int i = 0; i < centers.size(); i++) segmentedBlocks.put(i, new HashSet<>());

        double halfWidth = width / 2.0;
        double halfWidthSq = halfWidth * halfWidth;
        
        double pathDist = 0;
        int currentCenterIdx = 0;
        
        for (int i = 0; i < splinePoints.size() - 1; i++) {
            Vec2d pStart = splinePoints.get(i);
            Vec2d pEnd = splinePoints.get(i + 1);
            double segLen = pStart.dist(pEnd);
            pathDist += segLen;
            
            while (currentCenterIdx < centerDists.size() - 1 && centerDists.get(currentCenterIdx + 1) < pathDist) {
                currentCenterIdx++;
            }

            int minX = (int) Math.floor(Math.min(pStart.x, pEnd.x) - halfWidth - 1);
            int maxX = (int) Math.ceil(Math.max(pStart.x, pEnd.x) + halfWidth + 1);
            int minZ = (int) Math.floor(Math.min(pStart.z, pEnd.z) - halfWidth - 1);
            int maxZ = (int) Math.ceil(Math.max(pStart.z, pEnd.z) + halfWidth + 1);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dSq = distToSegmentSq(x, z, pStart, pEnd);
                    
                    if (dSq <= halfWidthSq) {
                        BlockPos blockPos = new BlockPos(x, 0, z);
                        
                        int bestIdx = currentCenterIdx;
                        double bestDistSq = Double.MAX_VALUE;
                        
                        int searchStart = Math.max(0, currentCenterIdx - 5);
                        int searchEnd = Math.min(centers.size() - 1, currentCenterIdx + 5);
                        
                        for (int k = searchStart; k <= searchEnd; k++) {
                            BlockPos c = centers.get(k);
                            double dx = x - c.getX();
                            double dz = z - c.getZ();
                            double cDistSq = dx*dx + dz*dz;
                            if (cDistSq < bestDistSq) {
                                bestDistSq = cDistSq;
                                bestIdx = k;
                            }
                        }
                        
                        segmentedBlocks.get(bestIdx).add(blockPos);
                    }
                }
            }
        }

        List<Records.RoadSegmentPlacement> out = new ArrayList<>();
        for (int i = 0; i < centers.size(); i++) {
            BlockPos center = centers.get(i);
            Set<BlockPos> blocks = segmentedBlocks.get(i);
            if (blocks.isEmpty()) blocks.add(new BlockPos(center.getX(), 0, center.getZ()));
            out.add(new Records.RoadSegmentPlacement(center, new ArrayList<>(blocks)));
        }
        return out;
    }
    
    private record Vec2d(double x, double z) {
        double dist(Vec2d o) { return Math.sqrt(distSqr(o)); }
        double distSqr(Vec2d o) { return (x-o.x)*(x-o.x) + (z-o.z)*(z-o.z); }
    }

    private static Vec2d lerp2d(BlockPos a, BlockPos b, double t) {
        double x = a.getX() + (b.getX() - a.getX()) * t;
        double z = a.getZ() + (b.getZ() - a.getZ()) * t;
        return new Vec2d(x, z);
    }

    private static boolean[] detectBridgeMask(List<BlockPos> nodes,
                                              ServerLevel level,
                                              TerrainSamplingCache cache,
                                              int bridgeMinWaterDepth) {
        int n = nodes.size();
        boolean[] mask = new boolean[n];
        int minDepth = Math.max(1, bridgeMinWaterDepth);
        int sea = level.getSeaLevel();
        for (int i = 0; i < n; i++) {
            BlockPos p = nodes.get(i);
            boolean isWater = RoadPathCalculator.isColumnWater(cache, p.getX(), p.getZ(), level);
            if (!isWater) {
                mask[i] = false;
                continue;
            }
            int oceanFloor = RoadPathCalculator.oceanFloorSampler(cache, p.getX(), p.getZ(), level);
            int waterDepth = Math.max(0, sea - oceanFloor);
            mask[i] = waterDepth >= minDepth;
        }
        return mask;
    }

    private static double distToSegmentSq(double px, double pz, Vec2d v, Vec2d w) {
        double l2 = v.distSqr(w);
        if (l2 == 0) return (px - v.x)*(px - v.x) + (pz - v.z)*(pz - v.z);
        double t = ((px - v.x) * (w.x - v.x) + (pz - v.z) * (w.z - v.z)) / l2;
        t = Math.max(0, Math.min(1, t));
        double projX = v.x + t * (w.x - v.x);
        double projZ = v.z + t * (w.z - v.z);
        return (px - projX)*(px - projX) + (pz - projZ)*(pz - projZ);
    }

    private static Vec2d catmullRomSplineDouble(BlockPos p0, BlockPos p1, BlockPos p2, BlockPos p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double f0 = -0.5 * t3 + t2 - 0.5 * t;
        double f1 = 1.5 * t3 - 2.5 * t2 + 1.0;
        double f2 = -1.5 * t3 + 2.0 * t2 + 0.5 * t;
        double f3 = 0.5 * t3 - 0.5 * t2;

        double x = p0.getX() * f0 + p1.getX() * f1 + p2.getX() * f2 + p3.getX() * f3;
        double z = p0.getZ() * f0 + p1.getZ() * f1 + p2.getZ() * f2 + p3.getZ() * f3;
        
        return new Vec2d(x, z);
    }

    private static List<BlockPos> simplifyPath(List<BlockPos> nodes) {
        if (nodes.size() < 3) return new ArrayList<>(nodes);

        List<BlockPos> simplified = new ArrayList<>();
        simplified.add(nodes.get(0));
        
        for (int i = 1; i < nodes.size() - 1; i++) {
            BlockPos prev = simplified.get(simplified.size() - 1);
            BlockPos curr = nodes.get(i);
            BlockPos next = nodes.get(i + 1);

            long dx1 = (long) curr.getX() - prev.getX();
            long dz1 = (long) curr.getZ() - prev.getZ();
            long dx2 = (long) next.getX() - curr.getX();
            long dz2 = (long) next.getZ() - curr.getZ();

            long crossProduct = dx1 * dz2 - dz1 * dx2;
            
            if (Math.abs(crossProduct) > 16) {
                simplified.add(curr);
            }
        }
        
        simplified.add(nodes.get(nodes.size() - 1));
        return simplified;
    }

    /**
     * 对路径控制点进行松弛操作，消除尖锐的折角。
     * 解决 Z 字形路径在样条插值后产生扭曲的问题。
     */
    private static List<BlockPos> relaxPath(List<BlockPos> nodes) {
        if (nodes.size() < 3) return new ArrayList<>(nodes);
        
        List<BlockPos> relaxed = new ArrayList<>();
        relaxed.add(nodes.get(0)); // 起点不动
        
        for (int i = 1; i < nodes.size() - 1; i++) {
            BlockPos prev = nodes.get(i - 1);
            BlockPos curr = nodes.get(i);
            BlockPos next = nodes.get(i + 1);
            
            // 加权平均: (Prev + 2*Curr + Next) / 4
            // 这样可以保留大部分原始位置，但会把尖角稍微"磨圆"
            int nx = (prev.getX() + curr.getX() * 2 + next.getX()) / 4;
            int nz = (prev.getZ() + curr.getZ() * 2 + next.getZ()) / 4;
            
            relaxed.add(new BlockPos(nx, curr.getY(), nz));
        }
        
        relaxed.add(nodes.get(nodes.size() - 1)); // 终点不动
        return relaxed;
    }

    private static List<BlockPos> relaxPathSkippingBridge(List<BlockPos> nodes, boolean[] bridgeMask) {
        if (nodes.size() < 3) return new ArrayList<>(nodes);
        List<BlockPos> relaxed = new ArrayList<>();
        relaxed.add(nodes.get(0));

        for (int i = 1; i < nodes.size() - 1; i++) {
            boolean isBridge = i < bridgeMask.length && bridgeMask[i];
            boolean nearBridge = (i - 1 >= 0 && i - 1 < bridgeMask.length && bridgeMask[i - 1])
                    || (i + 1 >= 0 && i + 1 < bridgeMask.length && bridgeMask[i + 1]);
            if (isBridge || nearBridge) {
                relaxed.add(nodes.get(i));
                continue;
            }

            BlockPos prev = nodes.get(i - 1);
            BlockPos curr = nodes.get(i);
            BlockPos next = nodes.get(i + 1);
            int nx = (prev.getX() + curr.getX() * 2 + next.getX()) / 4;
            int nz = (prev.getZ() + curr.getZ() * 2 + next.getZ()) / 4;
            relaxed.add(new BlockPos(nx, curr.getY(), nz));
        }

        relaxed.add(nodes.get(nodes.size() - 1));
        return relaxed;
    }
}
