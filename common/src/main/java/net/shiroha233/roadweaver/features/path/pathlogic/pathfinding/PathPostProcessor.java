package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.helpers.Records;

import java.util.*;

public final class PathPostProcessor {
    private PathPostProcessor() {}

    public enum CurveMode {
        CATMULL_ROM,
        BEZIER_CASTELJAU
    }

    /**
     * 将原始寻路节点列表转换为平滑的、具有宽度的路段列表。
     * 使用 Catmull-Rom 样条曲线生成平滑路径，并通过距离场光栅化生成路面。
     */
    /**
     * 将原始寻路节点列表转换为平滑的、具有宽度的路段列表
     * 
     * @param rawPath          原始路径节点
     * @param width            道路宽度
     * @param level            服务端世界
     * @param cache            地形采样缓存
     * @param bridgeMinWaterDepth 桥梁最小水深
     */
    public static List<Records.RoadSegmentPlacement> process(List<BlockPos> rawPath,
                                                             int width,
                                                             ServerLevel level,
                                                             TerrainSamplingCache cache,
                                                             int bridgeMinWaterDepth) {
        return process(rawPath, width, level, cache, bridgeMinWaterDepth, CurveMode.CATMULL_ROM);
    }

    public static List<Records.RoadSegmentPlacement> process(List<BlockPos> rawPath,
                                                             int width,
                                                             ServerLevel level,
                                                             TerrainSamplingCache cache,
                                                             int bridgeMinWaterDepth,
                                                             CurveMode curveMode) {
        if (rawPath == null || rawPath.size() < 2) return new ArrayList<>();

        // 1. 路径简化
        List<BlockPos> simplified = simplifyPath(rawPath);
        boolean[] bridgeMask = detectBridgeMask(simplified, level, cache, bridgeMinWaterDepth);
        // 进一步：对连续水域段做“整段拉直”，确保跨河桥梁为一条直线
        List<BlockPos> straightened = straightenBridgeRuns(simplified, bridgeMask);
        // 桥梁段不做曲线松弛/样条平滑，否则会出现弯桥
        List<BlockPos> controlPoints = relaxPathSkippingBridge(straightened, bridgeMask);
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
                Vec2d pt;
                if (bridgeSeg) {
                    pt = lerp2d(p1, p2, t);
                } else if (curveMode == CurveMode.BEZIER_CASTELJAU) {
                    pt = catmullRomBezierCasteljau(p0, p1, p2, p3, t);
                } else {
                    pt = catmullRomSplineDouble(p0, p1, p2, p3, t);
                }
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
        // 固定使用「到线段的投影距离」归仓（与高度插值一致）
        
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

                        // 扩展到 ±20 以覆盖宽道路在弯道处的情况
                        int extendedRadius = 20;
                        int searchStart = Math.max(0, currentCenterIdx - extendedRadius);
                        int searchEnd = Math.min(centers.size() - 2, currentCenterIdx + extendedRadius);

                        // 使用到线段的投影距离（与 RoadHeightInterpolator 一致）
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
                            double projDistSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ);

                            if (projDistSq < bestDistSq) {
                                bestDistSq = projDistSq;
                                // 归仓到插值位置更近的那个 center
                                // 如果 t < 0.5，归到 seg；否则归到 seg+1
                                bestIdx = (t < 0.5) ? seg : Math.min(seg + 1, centers.size() - 1);
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

    private static Vec2d catmullRomBezierCasteljau(BlockPos p0, BlockPos p1, BlockPos p2, BlockPos p3, double t) {
        t = Math.max(0.0, Math.min(1.0, t));

        Vec2d b0 = new Vec2d(p1.getX(), p1.getZ());
        Vec2d b1 = new Vec2d(
                p1.getX() + (p2.getX() - p0.getX()) / 6.0,
                p1.getZ() + (p2.getZ() - p0.getZ()) / 6.0
        );
        Vec2d b2 = new Vec2d(
                p2.getX() - (p3.getX() - p1.getX()) / 6.0,
                p2.getZ() - (p3.getZ() - p1.getZ()) / 6.0
        );
        Vec2d b3 = new Vec2d(p2.getX(), p2.getZ());

        Vec2d[] elevated = elevateBezierDegree(new Vec2d[]{b0, b1, b2, b3}, 5);
        return bezierDeCasteljau(elevated, t);
    }

    private static Vec2d[] elevateBezierDegree(Vec2d[] ctrl, int targetDegree) {
        if (ctrl == null || ctrl.length < 2) return ctrl;

        int degree = ctrl.length - 1;
        if (targetDegree <= degree) return ctrl;

        Vec2d[] cur = ctrl;
        int n = degree;
        while (n < targetDegree) {
            Vec2d[] next = new Vec2d[n + 2];
            next[0] = cur[0];
            next[n + 1] = cur[n];

            for (int i = 1; i <= n; i++) {
                double a = (double) i / (double) (n + 1);
                double x = a * cur[i - 1].x + (1.0 - a) * cur[i].x;
                double z = a * cur[i - 1].z + (1.0 - a) * cur[i].z;
                next[i] = new Vec2d(x, z);
            }

            cur = next;
            n++;
        }

        return cur;
    }

    private static Vec2d bezierDeCasteljau(Vec2d[] ctrl, double t) {
        int n = ctrl.length;
        double[] xs = new double[n];
        double[] zs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = ctrl[i].x;
            zs[i] = ctrl[i].z;
        }

        for (int k = n - 1; k > 0; k--) {
            for (int i = 0; i < k; i++) {
                xs[i] = xs[i] + (xs[i + 1] - xs[i]) * t;
                zs[i] = zs[i] + (zs[i + 1] - zs[i]) * t;
            }
        }

        return new Vec2d(xs[0], zs[0]);
    }

    /**
     * 识别“桥梁段”节点：用于让桥梁段跳过曲线平滑，避免弯桥。
     */
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

        boolean hasBridge = false;
        if (bridgeMask != null) {
            for (boolean b : bridgeMask) {
                if (b) {
                    hasBridge = true;
                    break;
                }
            }
        }
        if (!hasBridge) {
            return relaxPath(nodes);
        }

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

    /**
     * 将连续的桥梁（水域）节点段拉直：
     * - 以水域段两端“岸边节点”（水域段前后各一个点，若存在）为端点，定义桥梁直线方向
     * - 仅修改水域段内部的节点坐标，使其按索引比例插值到这条直线上
     *
     * 这样做的原因：桥梁是否需要生成（span）与道路寻路结果强相关，
     * 我们不希望改变水域识别逻辑，只希望让桥梁段的几何形状更稳定、可预测（直线）。
     */
    private static List<BlockPos> straightenBridgeRuns(List<BlockPos> nodes, boolean[] bridgeMask) {
        if (nodes == null || nodes.size() < 3 || bridgeMask == null || bridgeMask.length == 0) {
            return nodes;
        }

        int n = nodes.size();
        if (bridgeMask.length != n) {
            // 防御：mask 与 nodes 不一致时不做处理，避免索引越界
            return nodes;
        }

        List<BlockPos> out = new ArrayList<>(nodes);
        int i = 0;
        while (i < n) {
            if (!bridgeMask[i]) {
                i++;
                continue;
            }

            int runStart = i;
            while (i < n && bridgeMask[i]) {
                i++;
            }
            int runEnd = i - 1;

            // 取“岸边节点”作为直线端点：runStart-1 与 runEnd+1
            int entryIdx = Math.max(0, runStart - 1);
            int exitIdx = Math.min(n - 1, runEnd + 1);
            if (exitIdx <= entryIdx) {
                continue;
            }

            BlockPos a = nodes.get(entryIdx);
            BlockPos b = nodes.get(exitIdx);
            int ax = a.getX();
            int az = a.getZ();
            int dx = b.getX() - ax;
            int dz = b.getZ() - az;

            // 仅调整水域内部点；岸边点本身保持不动，以保证与陆地道路无缝衔接
            for (int k = runStart; k <= runEnd; k++) {
                double t = (k - entryIdx) / (double) (exitIdx - entryIdx);
                t = Math.max(0.0, Math.min(1.0, t));
                int x = (int) Math.round(ax + dx * t);
                int z = (int) Math.round(az + dz * t);
                BlockPos old = out.get(k);
                out.set(k, new BlockPos(x, old.getY(), z));
            }
        }
        return out;
    }
}
