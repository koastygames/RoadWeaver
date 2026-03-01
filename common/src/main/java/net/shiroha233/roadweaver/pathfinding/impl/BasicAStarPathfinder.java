package net.shiroha233.roadweaver.pathfinding.impl;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.pathfinding.PathResult;
import net.shiroha233.roadweaver.pathfinding.Pathfinder;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;

import java.util.*;

import static net.shiroha233.roadweaver.pathfinding.impl.PathfindingHelper.*;

/**
 * 基础 A* 寻路实现
 */
public final class BasicAStarPathfinder implements Pathfinder {

    private static final int BIOME_BASE_COST = 12;
    private static final double HEURISTIC_EPSILON = 0.2;

    @Override
    public PathResult findPath(BlockPos start, BlockPos end, int width,
                               ServerLevel level, int maxSteps,
                               TerrainSamplingCache cache, PathfindingCostConfig cfg) {
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Node> allNodes = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = new Node(start, null, 0.0, heuristic(start, end, cfg));
        openSet.add(startNode);
        allNodes.put(start, startNode);

        int d = cfg.effectiveAStarStep();
        int[][] offsets = {
                {d, 0}, {-d, 0}, {0, d}, {0, -d},
                {d, d}, {d, -d}, {-d, d}, {-d, -d}
        };

        int stepsBudget = Math.max(1, maxSteps);
        ThrottleHelper.resetThrottle();
        try {
            while (!openSet.isEmpty() && stepsBudget-- > 0) {
                ThrottleHelper.throttle(RoadConstants.DEFAULT_DUTY_CYCLE);
                if (Thread.currentThread().isInterrupted()) return PathResult.failure();

                Node current = openSet.poll();
                if (current == null) break;

                if (manhattan2d(current.pos, end) < d * 2) {
                    List<RoadSegmentPlacement> segments = reconstructPath(current, width, level, cache);
                    return segments != null ? PathResult.success(segments) : PathResult.failure();
                }

                closed.add(current.pos);
                allNodes.remove(current.pos);

                for (int[] off : offsets) {
                    if (Thread.currentThread().isInterrupted()) return PathResult.failure();
                    BlockPos nxz = current.pos.offset(off[0], 0, off[1]);
                    int y = heightSampler(cache, nxz.getX(), nxz.getZ(), level);
                    BlockPos np = new BlockPos(nxz.getX(), y, nxz.getZ());
                    if (closed.contains(np)) continue;

                    double tentativeG = current.g + computeMoveCost(current.pos, np, nxz, off, d,
                            start, end, level, cache, cfg);

                    Node n = allNodes.get(np);
                    if (n == null || tentativeG < n.g) {
                        double h = heuristic(np, end, cfg);
                        double f = tentativeG + (1.0 + HEURISTIC_EPSILON) * h;
                        n = new Node(np, current, tentativeG, f);
                        allNodes.put(np, n);
                        openSet.add(n);
                    }
                }
            }
            return PathResult.failure();
        } finally {
            openSet.clear();
            allNodes.clear();
            closed.clear();
            ThrottleHelper.clearThrottle();
        }
    }

    private double computeMoveCost(BlockPos current, BlockPos np, BlockPos nxz, int[] off, int d,
                                   BlockPos start, BlockPos end,
                                   ServerLevel level, TerrainSamplingCache cache,
                                   PathfindingCostConfig cfg) {
        Holder<Biome> biome = cache.getBiome(level, np.getX(), np.getZ());
        int biomeCost = (biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN)
                || biome.is(BiomeTags.IS_DEEP_OCEAN)) ? BIOME_BASE_COST : 0;
        int elevation = Math.abs(np.getY() - current.getY());
        int offsetSum = Math.abs(off[0]) + Math.abs(off[1]);
        double stepCost = (offsetSum == 2 * d) ? cfg.diagStepCost() : cfg.orthoStepCost();
        int stabilityCost = calculateTerrainStability(cache, np, np.getY(), level, d);
        int sea = level.getSeaLevel();
        boolean waterColumn = isColumnWater(cache, nxz.getX(), nxz.getZ(), level);
        boolean nearWater = isNearWaterLike(cache, nxz.getX(), nxz.getZ(), level);
        int oceanFloor = oceanFloorSampler(cache, nxz.getX(), nxz.getZ(), level);
        int waterDepth = Math.max(0, sea - oceanFloor);
        int waterDepthCost = waterColumn ? (int) (waterDepth * cfg.waterDepthWeight()) : 0;
        int nearWaterCost = nearWater ? cfg.nearWaterCost() : 0;
        double deviation = deviation2d(np, start, end);
        double deviationCost = deviation * cfg.deviationWeight() / Math.max(1.0, d);

        return stepCost
                + elevation * cfg.elevationWeight()
                + biomeCost * cfg.biomeWeight()
                + stabilityCost * cfg.stabilityWeight()
                + waterDepthCost
                + nearWaterCost
                + deviationCost;
    }

    private List<RoadSegmentPlacement> reconstructPath(Node endNode, int width,
                                                        ServerLevel level, TerrainSamplingCache cache) {
        List<BlockPos> rawPath = new ArrayList<>();
        Node c = endNode;
        while (c != null) { rawPath.add(c.pos); c = c.parent; }
        Collections.reverse(rawPath);
        AccurateHeightSampler accurate = AccurateHeightSampler.create(level);
        rawPath = accurate.samplePathHeights(rawPath);
        return PathPostProcessor.process(rawPath, width, level, cache,
                RoadConstants.DEFAULT_BRIDGE_MIN_WATER_DEPTH, accurate);
    }

    private static int manhattan2d(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private static double heuristic(BlockPos a, BlockPos b, PathfindingCostConfig cfg) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        double approx = Math.abs(dx) + Math.abs(dz) - 0.6 * Math.min(Math.abs(dx), Math.abs(dz));
        return approx * cfg.heuristicWeight();
    }

    private static double deviation2d(BlockPos p, BlockPos a, BlockPos b) {
        double ax = a.getX(), az = a.getZ(), bx = b.getX(), bz = b.getZ();
        double num = Math.abs((bz - az) * p.getX() - (bx - ax) * p.getZ() + bx * az - bz * ax);
        double den = Math.hypot(bx - ax, bz - az);
        return den <= 0.0 ? 0.0 : num / den;
    }

    private static final class Node {
        final BlockPos pos;
        final Node parent;
        final double g;
        final double f;
        Node(BlockPos pos, Node parent, double g, double f) {
            this.pos = pos; this.parent = parent; this.g = g; this.f = f;
        }
    }
}
