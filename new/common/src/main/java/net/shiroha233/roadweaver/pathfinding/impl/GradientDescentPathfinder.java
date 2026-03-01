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
 * 梯度下降寻路
 */
public final class GradientDescentPathfinder implements Pathfinder {

    private static final int BIOME_BASE_COST = 12;
    private static final int SEARCH_BUFFER = 64;
    private static final double WATER_COLUMN_BASE_PENALTY = 800.0;
    private static final double WATER_DEPTH_SQUARED_WEIGHT = 2.0;
    private static final double NEAR_WATER_COST_MULTIPLIER = 4.0;
    private static final double SLOPE_SOFT_THRESHOLD = 0.5;
    private static final double SLOPE_HARD_THRESHOLD = 0.8;
    private static final double SLOPE_SOFT_PENALTY = 800.0;
    private static final double SLOPE_HARD_PENALTY = 8000.0;

    @Override
    public PathResult findPath(BlockPos start, BlockPos end, int width,
                               ServerLevel level, int maxSteps,
                               TerrainSamplingCache cache, PathfindingCostConfig cfg) {
        int manhattan = manhattan2d(start, end);
        int dynamicBuffer = Math.min(512, Math.max(SEARCH_BUFFER, manhattan / 4));
        int minX = Math.min(start.getX(), end.getX()) - dynamicBuffer;
        int maxX = Math.max(start.getX(), end.getX()) + dynamicBuffer;
        int minZ = Math.min(start.getZ(), end.getZ()) - dynamicBuffer;
        int maxZ = Math.max(start.getZ(), end.getZ()) + dynamicBuffer;

        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
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

        int stepsBudget = Math.max(5000, maxSteps * RoadConstants.GRADIENT_DESCENT_STEPS_MULTIPLIER);
        ThrottleHelper.resetThrottle();
        try {
            while (!openSet.isEmpty() && stepsBudget-- > 0) {
                ThrottleHelper.throttle(RoadConstants.DEFAULT_DUTY_CYCLE);
                if (Thread.currentThread().isInterrupted()) return PathResult.failure();

                Node current = openSet.poll();
                if (current == null) break;

                if (manhattan2d(current.pos, end) < (int) (d * RoadConstants.GRADIENT_DESCENT_SUCCESS_DISTANCE_FACTOR)) {
                    List<RoadSegmentPlacement> segments = reconstructPath(current, width, level, cache);
                    return segments != null ? PathResult.success(segments) : PathResult.failure();
                }

                closed.add(current.pos);

                for (int[] off : offsets) {
                    BlockPos nxz = current.pos.offset(off[0], 0, off[1]);
                    if (nxz.getX() < minX || nxz.getX() > maxX || nxz.getZ() < minZ || nxz.getZ() > maxZ) continue;

                    int y = heightSampler(cache, nxz.getX(), nxz.getZ(), level);
                    BlockPos np = new BlockPos(nxz.getX(), y, nxz.getZ());
                    if (closed.contains(np)) continue;

                    double gCost = current.gCost + computeMoveCost(current.pos, np, nxz, off, d,
                            level, cache, cfg);
                    double hCost = heuristic(np, end, cfg);
                    double fCost = gCost + hCost;

                    Node n = allNodes.get(np);
                    if (n == null || gCost < n.gCost) {
                        n = new Node(np, current, gCost, fCost);
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
                                   ServerLevel level, TerrainSamplingCache cache,
                                   PathfindingCostConfig cfg) {
        Holder<Biome> biome = cache.getBiome(level, np.getX(), np.getZ());
        int biomeCost = (biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN)
                || biome.is(BiomeTags.IS_DEEP_OCEAN)) ? (BIOME_BASE_COST * 4) : 0;
        int elevation = Math.abs(np.getY() - current.getY());
        int offsetSum = Math.abs(off[0]) + Math.abs(off[1]);
        double stepCost = (offsetSum == 2 * d) ? cfg.diagStepCost() : cfg.orthoStepCost();
        int stabilityCost = calculateTerrainStability(cache, np, np.getY(), level, d);
        int sea = level.getSeaLevel();
        boolean waterColumn = isColumnWater(cache, nxz.getX(), nxz.getZ(), level);
        boolean nearWater = isNearWaterLike(cache, nxz.getX(), nxz.getZ(), level);
        int oceanFloor = oceanFloorSampler(cache, nxz.getX(), nxz.getZ(), level);
        int waterDepth = Math.max(0, sea - oceanFloor);

        double waterDepthPenalty = 0.0;
        if (waterColumn) {
            double w = Math.max(0.0, cfg.waterDepthWeight());
            waterDepthPenalty = WATER_COLUMN_BASE_PENALTY
                    + (waterDepth * (double) waterDepth) * w * WATER_DEPTH_SQUARED_WEIGHT;
        }
        double nearWaterPenalty = nearWater ? (cfg.nearWaterCost() * NEAR_WATER_COST_MULTIPLIER) : 0.0;

        double elevationCost = elevation * elevation * cfg.elevationWeight();
        double slope = (double) elevation / Math.max(1, d);
        if (slope > SLOPE_SOFT_THRESHOLD) elevationCost += SLOPE_SOFT_PENALTY * slope;
        if (slope > SLOPE_HARD_THRESHOLD) elevationCost += SLOPE_HARD_PENALTY;

        return stepCost
                + elevationCost
                + biomeCost * cfg.biomeWeight()
                + stabilityCost * cfg.stabilityWeight()
                + waterDepthPenalty
                + nearWaterPenalty;
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
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz) * cfg.heuristicWeight();
    }

    private static final class Node {
        final BlockPos pos;
        final Node parent;
        final double gCost;
        final double fCost;
        Node(BlockPos pos, Node parent, double gCost, double fCost) {
            this.pos = pos; this.parent = parent; this.gCost = gCost; this.fCost = fCost;
        }
    }
}
