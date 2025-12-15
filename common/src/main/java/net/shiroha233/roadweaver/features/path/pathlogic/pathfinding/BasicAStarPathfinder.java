package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.config.PathfindingConfig;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.*;

final class BasicAStarPathfinder {
    private BasicAStarPathfinder() {
    }

    private static final int BIOME_BASE_COST = 12; // 特定生物群系基础成本（河流/海洋/深海）
    private static final double HEURISTIC_EPSILON = 0.2; // 启发式 epsilon

    /**
     * 基础 A* 寻路算法
     * 
     * @param startGround 起点
     * @param endGround   终点
     * @param width       道路宽度
     * @param level       服务端世界
     * @param maxSteps    最大步数
     * @param cache       地形采样缓存
     * @param cfg         寻路配置快照（不可变）
     */
    public static List<Records.RoadSegmentPlacement> calculateLandPath(BlockPos startGround,
            BlockPos endGround,
            int width,
            ServerLevel level,
            int maxSteps,
            TerrainSamplingCache cache,
            PathfindingConfig cfg) {
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Node> allNodes = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = new Node(startGround, null, 0.0, heuristic(startGround, endGround, cfg));
        openSet.add(startNode);
        allNodes.put(startGround, startNode);

        int d = cfg.effectiveAStarStep();
        int[][] neighborOffsets = new int[][] {
                { d, 0 }, { -d, 0 }, { 0, d }, { 0, -d },
                { d, d }, { d, -d }, { -d, d }, { -d, -d }
        };

        int stepsBudget = Math.max(1, maxSteps);
        int dutyCycle = cfg.threadDutyCycle();
        ThreadPoolManager.resetThrottle();
        try {
            while (!openSet.isEmpty() && stepsBudget-- > 0) {
                ThreadPoolManager.throttle(dutyCycle);
                if (Thread.currentThread().isInterrupted()) {
                    return null;
                }
                Node current = openSet.poll();
                if (current == null)
                    break;

                if (manhattan2d(current.pos, endGround) < d * 2) {
                    List<BlockPos> rawPath = new ArrayList<>();
                    Node c = current;
                    while (c != null) {
                        rawPath.add(c.pos);
                        c = c.parent;
                    }
                    Collections.reverse(rawPath);
                    return PathPostProcessor.process(rawPath, width, level, cache, cfg.bridgeMinWaterDepth());
                }

                closed.add(current.pos);
                allNodes.remove(current.pos);

                for (int[] off : neighborOffsets) {
                    if (Thread.currentThread().isInterrupted()) {
                        return null;
                    }
                    BlockPos nxz = current.pos.offset(off[0], 0, off[1]);
                    int y = RoadPathCalculator.heightSampler(cache, nxz.getX(), nxz.getZ(), level);
                    BlockPos np = new BlockPos(nxz.getX(), y, nxz.getZ());
                    if (closed.contains(np))
                        continue;

                    Holder<Biome> biome = cache.getBiome(level, np.getX(), np.getZ());
                    int biomeCost = (biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN)
                            || biome.is(BiomeTags.IS_DEEP_OCEAN)) ? BIOME_BASE_COST : 0;
                    int elevation = Math.abs(y - current.pos.getY());

                    int offsetSum = Math.abs(Math.abs(off[0])) + Math.abs(off[1]);
                    double stepCost = (offsetSum == 2 * d) ? cfg.diagStepCost() : cfg.orthoStepCost();
                    int stabilityCost = RoadPathCalculator.calculateTerrainStability(cache, np, y, level, d);
                    int sea = level.getSeaLevel();
                    boolean waterColumn = RoadPathCalculator.isColumnWater(cache, nxz.getX(), nxz.getZ(), level);
                    boolean nearWater = RoadPathCalculator.isNearWaterLike(cache, nxz.getX(), nxz.getZ(), level);
                    int oceanFloor = RoadPathCalculator.oceanFloorSampler(cache, nxz.getX(), nxz.getZ(), level);
                    int waterDepth = Math.max(0, sea - oceanFloor);
                    int waterDepthCost = waterColumn ? (int)(waterDepth * cfg.waterDepthWeight()) : 0;
                    int nearWaterCost = nearWater ? (int)cfg.nearWaterCost() : 0;

                    double deviation = deviation2d(np, startGround, endGround);
                    double deviationCost = deviation * cfg.deviationWeight() / Math.max(1.0, d);

                    double tentativeG = current.g
                            + stepCost
                            + elevation * cfg.elevationWeight()
                            + biomeCost * cfg.biomeWeight()
                            + stabilityCost * cfg.stabilityWeight()
                            + waterDepthCost
                            + nearWaterCost
                            + deviationCost;

                    Node n = allNodes.get(np);
                    if (n == null || tentativeG < n.g) {
                        double h = heuristic(np, endGround, cfg);
                        double fWeighted = tentativeG + (1.0 + HEURISTIC_EPSILON) * h;
                        n = new Node(np, current, tentativeG, fWeighted);
                        allNodes.put(np, n);
                        openSet.add(n);
                    }
                }
            }
            return null;
        } finally {
            // 显式清理，帮助 GC 回收 Node 链表
            openSet.clear();
            allNodes.clear();
            closed.clear();
            // 清理 ThreadLocal，防止线程池复用导致内存泄漏
            ThreadPoolManager.clearThrottle();
        }
    }


    private static int manhattan2d(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private static double heuristic(BlockPos a, BlockPos b, PathfindingConfig cfg) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        double dxzApprox = Math.abs(dx) + Math.abs(dz) - 0.6 * Math.min(Math.abs(dx), Math.abs(dz));
        return dxzApprox * cfg.heuristicWeight();
    }

    private static double deviation2d(BlockPos p, BlockPos a, BlockPos b) {
        double ax = a.getX();
        double az = a.getZ();
        double bx = b.getX();
        double bz = b.getZ();
        double px = p.getX();
        double pz = p.getZ();
        double num = Math.abs((bz - az) * px - (bx - ax) * pz + bx * az - bz * ax);
        double den = Math.hypot(bx - ax, bz - az);
        if (den <= 0.0)
            return 0.0;
        return num / den;
    }

    private static final class Node {
        final BlockPos pos;
        final Node parent;
        final double g;
        final double f;

        Node(BlockPos pos, Node parent, double g, double f) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = f;
        }
    }
}
