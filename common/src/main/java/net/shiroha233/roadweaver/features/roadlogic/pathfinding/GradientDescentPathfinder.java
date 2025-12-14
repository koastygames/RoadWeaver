package net.shiroha233.roadweaver.features.roadlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.config.PathfindingConfig;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.*;

/**
 * 基于梯度下降（流体模拟）的寻路算法。
 * 实质是限制区域的 Dijkstra 算法（无启发式 A*），模拟水流蔓延寻找绝对最小阻力路径。
 * 特点：
 * 1. 能够找到绕过高山的平缓路径，而不是翻山越岭。
 * 2. 路径极其自然，贴合地形等高线。
 * 3. 限制搜索范围以保证性能。
 */
final class GradientDescentPathfinder {
    private GradientDescentPathfinder() {}

    private static final int BIOME_BASE_COST = 12;
    private static final int SEARCH_BUFFER = 64; // 搜索边界缓冲
    private static final double WATER_COLUMN_BASE_PENALTY = 800.0;
    private static final double WATER_DEPTH_SQUARED_WEIGHT = 2.0;
    private static final double NEAR_WATER_COST_MULTIPLIER = 4.0;

    /**
     * 梯度下降寻路算法
     * 
     * @param startGround 起点
     * @param endGround   终点
     * @param width       道路宽度
     * @param level       服务端世界
     * @param maxSteps    最大步数
     * @param cache       地形采样缓存
     * @param cfg         寻路配置快照（不可变）
     */
    static List<Records.RoadSegmentPlacement> calculatePath(BlockPos startGround,
                                                           BlockPos endGround,
                                                           int width,
                                                           ServerLevel level,
                                                           int maxSteps,
                                                           TerrainSamplingCache cache,
                                                           PathfindingConfig cfg) {
        
        // 1. 定义搜索边界 (Bounding Box)
        // 即使有了启发式，保留边界检查也是个好习惯，防止跑太远
        int manhattan = manhattan2d(startGround, endGround);
        int dynamicBuffer = Math.min(512, Math.max(SEARCH_BUFFER, manhattan / 4));
        int minX = Math.min(startGround.getX(), endGround.getX()) - dynamicBuffer;
        int maxX = Math.max(startGround.getX(), endGround.getX()) + dynamicBuffer;
        int minZ = Math.min(startGround.getZ(), endGround.getZ()) - dynamicBuffer;
        int maxZ = Math.max(startGround.getZ(), endGround.getZ()) + dynamicBuffer;

        // A* 需要比较 f_cost = g_cost + h_cost
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
        Map<BlockPos, Node> allNodes = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = new Node(startGround, null, 0.0, heuristic(startGround, endGround, cfg));
        openSet.add(startNode);
        allNodes.put(startGround, startNode);

        int d = cfg.effectiveAStarStep();
        int[][] neighborOffsets = new int[][]{
                {d, 0}, {-d, 0}, {0, d}, {0, -d},
                {d, d}, {d, -d}, {-d, d}, {-d, -d}
        };

        // 既然有了启发式，步数预算可以稍微收紧，或者保持不变以支持长距离绕行
        // 但为了防止无解时的死循环，还是保留限制
        int stepsBudget = Math.max(5000, maxSteps * 3); 

        int dutyCycle = cfg.threadDutyCycle();
        ThreadPoolManager.resetThrottle(); // 重置节流计时器
        try {
            while (!openSet.isEmpty() && stepsBudget-- > 0) {
                ThreadPoolManager.throttle(dutyCycle); // 根据占空比控制CPU使用率
                if (Thread.currentThread().isInterrupted()) return null;
                
                Node current = openSet.poll();
                if (current == null) break;

                // 找到终点（或非常接近）
                if (manhattan2d(current.pos, endGround) < d * 1.5) {
                    return reconstructPath(current, width, level, cache, cfg.bridgeMinWaterDepth());
                }

                closed.add(current.pos);

                for (int[] off : neighborOffsets) {
                    BlockPos nxz = current.pos.offset(off[0], 0, off[1]);
                    
                    // 边界检查
                    if (nxz.getX() < minX || nxz.getX() > maxX || nxz.getZ() < minZ || nxz.getZ() > maxZ) continue;

                    int y = RoadPathCalculator.heightSampler(cache, nxz.getX(), nxz.getZ(), level);
                    BlockPos np = new BlockPos(nxz.getX(), y, nxz.getZ());
                    
                    if (closed.contains(np)) continue;

                    // --- 代价计算 ---
                    Holder<Biome> biome = cache.getBiome(level, np.getX(), np.getZ());
                    int biomeCost = (biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN)
                            || biome.is(BiomeTags.IS_DEEP_OCEAN)) ? (BIOME_BASE_COST * 4) : 0;
                    int elevation = Math.abs(y - current.pos.getY());

                    int offsetSum = Math.abs(Math.abs(off[0])) + Math.abs(off[1]);
                    double stepCost = (offsetSum == 2 * d) ? cfg.diagStepCost() : cfg.orthoStepCost();
                    int stabilityCost = RoadPathCalculator.calculateTerrainStability(cache, np, y, level, d);
                    int sea = level.getSeaLevel();
                    boolean waterColumn = RoadPathCalculator.isColumnWater(cache, nxz.getX(), nxz.getZ(), level);
                    boolean nearWater = RoadPathCalculator.isNearWaterLike(cache, nxz.getX(), nxz.getZ(), level);
                    int oceanFloor = RoadPathCalculator.oceanFloorSampler(cache, nxz.getX(), nxz.getZ(), level);
                    int waterDepth = Math.max(0, sea - oceanFloor);
                    double waterDepthPenalty = 0.0;
                    if (waterColumn) {
                        double w = Math.max(0.0, cfg.waterDepthWeight());
                        waterDepthPenalty = WATER_COLUMN_BASE_PENALTY
                                + (waterDepth * (double) waterDepth) * w * WATER_DEPTH_SQUARED_WEIGHT;
                    }
                    double nearWaterPenalty = nearWater ? (cfg.nearWaterCost() * NEAR_WATER_COST_MULTIPLIER) : 0.0;

                    double elevationCost = elevation * elevation * cfg.elevationWeight();
                    // 坡度阻断
                    double slope = (double) elevation / Math.max(1, d);
                    if (slope > 0.5) elevationCost += 800.0 * slope;
                    if (slope > 0.8) elevationCost += 8000.0;

                    double gCost = current.gCost
                            + stepCost
                            + elevationCost
                            + biomeCost * cfg.biomeWeight()
                            + stabilityCost * cfg.stabilityWeight()
                            + waterDepthPenalty
                            + nearWaterPenalty;

                    // 关键改动：加入启发式，但保持流体特性（无 deviation 惩罚）
                    double hCost = heuristic(np, endGround, cfg);
                    double fCost = gCost + hCost;

                    Node n = allNodes.get(np);
                    if (n == null || gCost < n.gCost) {
                        n = new Node(np, current, gCost, fCost);
                        allNodes.put(np, n);
                        openSet.add(n);
                    }
                }
            }
        } finally {
            // 显式清理引用，帮助 GC
            openSet.clear();
            allNodes.clear();
            closed.clear();
            // 清理 ThreadLocal，防止线程池复用导致内存泄漏
            ThreadPoolManager.clearThrottle();
        }
        return null;
    }

    private static List<Records.RoadSegmentPlacement> reconstructPath(Node endNode,
                                                                      int width,
                                                                      ServerLevel level,
                                                                      TerrainSamplingCache cache,
                                                                      int bridgeMinWaterDepth) {
        List<BlockPos> rawPath = new ArrayList<>();
        Node c = endNode;
        while (c != null) {
            rawPath.add(c.pos);
            c = c.parent;
        }
        Collections.reverse(rawPath);
        return PathPostProcessor.process(rawPath, width, level, cache, bridgeMinWaterDepth);
    }

    private static int manhattan2d(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private static double heuristic(BlockPos a, BlockPos b, PathfindingConfig cfg) {
        // 使用欧几里得距离，给予更平滑的方向指引
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz) * cfg.heuristicWeight();
    }

    private static final class Node {
        final BlockPos pos;
        final Node parent;
        final double gCost; // 实际行走代价
        final double fCost; // gCost + heuristic

        Node(BlockPos pos, Node parent, double gCost, double fCost) {
            this.pos = pos;
            this.parent = parent;
            this.gCost = gCost;
            this.fCost = fCost;
        }
    }
}
