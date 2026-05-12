package net.shiroha233.roadweaver.features.highway.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.features.highway.config.HighwayGenerationConfig;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.impl.PathPostProcessor;
import net.shiroha233.roadweaver.pathfinding.impl.SplineHelper;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.*;

/**
 * Highway 双向 A* 寻路器
 */
public final class HighwayBidirectionalAStarPathfinder {
    private HighwayBidirectionalAStarPathfinder() {}

    private static final int BIOME_BASE_COST = 12;
    private static final double HEURISTIC_EPSILON = 0.2;

    public static PathCalculationResult calculateLandPath(BlockPos startGround,
                                                          BlockPos endGround,
                                                          int width,
                                                          ServerLevel level,
                                                          int maxSteps,
                                                          TerrainSamplingCache cache,
                                                          HighwayGenerationConfig cfg,
                                                          PathTerrainField terrain) {
        if (startGround == null || endGround == null || level == null || cache == null || cfg == null || terrain == null) {
            return PathCalculationResult.failure();
        }
        if (startGround.equals(endGround)) {
            return new PathCalculationResult(Collections.emptyList(), terrain);
        }

        PathfindingCostConfig pathCfg = cfg.pathfindingCost();
        int d = pathCfg.effectiveAStarStep();
        int[][] neighborOffsets = new int[][]{
                {d, 0}, {-d, 0}, {0, d}, {0, -d},
                {d, d}, {d, -d}, {-d, d}, {-d, -d}
        };

        PriorityQueue<Node> openF = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        PriorityQueue<Node> openB = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<BlockPos, Node> nodesF = new HashMap<>();
        Map<BlockPos, Node> nodesB = new HashMap<>();
        Set<BlockPos> closedF = new HashSet<>();
        Set<BlockPos> closedB = new HashSet<>();

        Node startNode = new Node(startGround, null, 0.0, heuristic(startGround, endGround, pathCfg));
        Node endNode = new Node(endGround, null, 0.0, heuristic(endGround, startGround, pathCfg));
        openF.add(startNode);
        nodesF.put(startGround, startNode);
        openB.add(endNode);
        nodesB.put(endGround, endNode);

        int stepsBudget = Math.max(1, maxSteps);
        int dutyCycle = cfg.threadDutyCycle();
        ThreadPoolManager.resetThrottle();

        while (!openF.isEmpty() && !openB.isEmpty() && stepsBudget-- > 0) {
            ThreadPoolManager.throttle(dutyCycle);
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }

            Node peekF = openF.peek();
            Node peekB = openB.peek();
            boolean expandForward;
            if (peekF == null) {
                expandForward = false;
            } else if (peekB == null) {
                expandForward = true;
            } else {
                expandForward = peekF.f <= peekB.f;
            }

            Meet meet;
            if (expandForward) {
                meet = expandOneSide(openF, nodesF, closedF, nodesB, closedB, nodesB,
                        true, startGround, endGround, level, cache, terrain, neighborOffsets, d, cfg);
            } else {
                meet = expandOneSide(openB, nodesB, closedB, nodesF, closedF, nodesF,
                        false, endGround, startGround, level, cache, terrain, neighborOffsets, d, cfg);
            }

            if (meet != null) {
                return reconstructPath(meet.forward, meet.backward, width, level, cache, cfg, terrain);
            }
        }

        return PathCalculationResult.failure();
    }

    private static Meet expandOneSide(PriorityQueue<Node> open,
                                     Map<BlockPos, Node> nodesThis,
                                     Set<BlockPos> closedThis,
                                     Map<BlockPos, Node> nodesOther,
                                     Set<BlockPos> closedOther,
                                     Map<BlockPos, Node> closedOtherNodes,
                                     boolean isForward,
                                     BlockPos from,
                                     BlockPos to,
                                     ServerLevel level,
                                     TerrainSamplingCache cache,
                                     PathTerrainField terrain,
                                     int[][] neighborOffsets,
                                     int d,
                                     HighwayGenerationConfig cfg) {
        if (open.isEmpty()) return null;
        Node current = open.poll();
        if (current == null) return null;

        closedThis.add(current.pos);

        PathfindingCostConfig pathCfg = cfg.pathfindingCost();

        for (int[] off : neighborOffsets) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            BlockPos nxz = current.pos.offset(off[0], 0, off[1]);
            if (!terrain.contains(nxz.getX(), nxz.getZ())) {
                continue;
            }
            int y = heightSampler(cache, terrain, nxz.getX(), nxz.getZ(), level);
            BlockPos np = new BlockPos(nxz.getX(), y, nxz.getZ());
            if (closedThis.contains(np)) continue;

            Holder<Biome> biome = biome(cache, terrain, level, np.getX(), np.getZ());
            int biomeCost = (biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN)
                    || biome.is(BiomeTags.IS_DEEP_OCEAN)) ? BIOME_BASE_COST : 0;
            int elevation = Math.abs(y - current.pos.getY());

            int offsetSum = Math.abs(Math.abs(off[0])) + Math.abs(off[1]);
            double stepCost = (offsetSum == 2 * d) ? pathCfg.diagStepCost() : pathCfg.orthoStepCost();
            int stabilityCost = calculateTerrainStability(cache, terrain, np, y, level, d);

            int sea = terrain.seaLevel();
            boolean waterColumn = isColumnWater(cache, terrain, nxz.getX(), nxz.getZ(), level);
            boolean nearWater = isNearWaterLike(cache, terrain, nxz.getX(), nxz.getZ(), level, d);
            int oceanFloor = oceanFloorSampler(cache, terrain, nxz.getX(), nxz.getZ(), level);
            int waterDepth = Math.max(0, sea - oceanFloor);
            int waterDepthCost = waterColumn ? (int) (waterDepth * pathCfg.waterDepthWeight()) : 0;
            int nearWaterCost = nearWater ? (int) pathCfg.nearWaterCost() : 0;

            double deviation = deviation2d(np, from, to);
            double deviationCost = deviation * pathCfg.deviationWeight() / Math.max(1.0, d);

            double extraCost = HighwayExtraCostModel.profileCost(np, from, to,
                    cfg.floatingWeight(), cfg.penetrationWeight());

            double tentativeG = current.g
                    + stepCost
                    + elevation * pathCfg.elevationWeight()
                    + biomeCost * pathCfg.biomeWeight()
                    + stabilityCost * pathCfg.stabilityWeight()
                    + waterDepthCost
                    + nearWaterCost
                    + deviationCost
                    + extraCost;

            Node existing = nodesThis.get(np);
            if (existing != null && tentativeG >= existing.g) {
                continue;
            }

            double h = heuristic(np, to, pathCfg);
            double fWeighted = tentativeG + (1.0 + HEURISTIC_EPSILON) * h;
            Node next = new Node(np, current, tentativeG, fWeighted);
            nodesThis.put(np, next);
            open.add(next);

            Node other = nodesOther.get(np);
            if (other == null && closedOther.contains(np)) {
                other = closedOtherNodes.get(np);
            }
            if (other != null) {
                if (isForward) {
                    return new Meet(next, other);
                } else {
                    return new Meet(other, next);
                }
            }
        }

        return null;
    }

    private static PathCalculationResult reconstructPath(Node meetForward,
                                                         Node meetBackward,
                                                         int width,
                                                         ServerLevel level,
                                                         TerrainSamplingCache cache,
                                                         HighwayGenerationConfig cfg,
                                                         PathTerrainField terrain) {
        List<BlockPos> rawPath = new ArrayList<>();
        Node cur = meetForward;
        while (cur != null) {
            rawPath.add(cur.pos);
            cur = cur.parent;
        }
        Collections.reverse(rawPath);

        Node backStart = (meetBackward != null && meetBackward.pos.equals(meetForward.pos))
                ? meetBackward.parent : meetBackward;
        cur = backStart;
        while (cur != null) {
            rawPath.add(cur.pos);
            cur = cur.parent;
        }

        AccurateHeightSampler accurate = cache.getAccurateSampler(level);
        PathfindingCostConfig costCfg = cfg.pathfindingCost();
        boolean needsRefinement = costCfg == null || costCfg.needsRefinement();
        if (needsRefinement) {
            rawPath = accurate.samplePathHeights(rawPath, 0);
        }
        List<RoadSegmentPlacement> segments = PathPostProcessor.process(
                rawPath,
                width,
                level,
                cache,
                terrain,
                cfg.bridgeMinWaterDepth(),
                SplineHelper.CurveMode.BEZIER_CASTELJAU,
                accurate);
        return segments == null ? PathCalculationResult.failure() : new PathCalculationResult(segments, terrain);
    }

    private static double heuristic(BlockPos a, BlockPos b, PathfindingCostConfig cfg) {
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
        if (den <= 0.0) return 0.0;
        return num / den;
    }

    private static int heightSampler(TerrainSamplingCache cache, PathTerrainField terrain, int x, int z, ServerLevel level) {
        if (terrain.contains(x, z)) {
            return terrain.height(x, z);
        }
        return cache.height(level, x, z);
    }

    private static int oceanFloorSampler(TerrainSamplingCache cache, PathTerrainField terrain, int x, int z, ServerLevel level) {
        if (terrain.contains(x, z)) {
            return terrain.oceanFloor(x, z);
        }
        return cache.oceanFloor(level, x, z);
    }

    private static boolean isColumnWater(TerrainSamplingCache cache, PathTerrainField terrain, int x, int z, ServerLevel level) {
        if (terrain.contains(x, z)) {
            return terrain.isColumnWater(x, z);
        }
        return cache.isColumnWater(level, x, z);
    }

    private static boolean isNearWaterLike(TerrainSamplingCache cache,
                                           PathTerrainField terrain,
                                           int x,
                                           int z,
                                           ServerLevel level,
                                           int step) {
        if (terrain.contains(x, z)) {
            return terrain.isNearWater(x, z, step);
        }
        return cache.isNearWaterLike(level, x, z, 16);
    }

    private static Holder<Biome> biome(TerrainSamplingCache cache,
                                       PathTerrainField terrain,
                                       ServerLevel level,
                                       int x,
                                       int z) {
        if (terrain.contains(x, z)) {
            return terrain.biome(x, z);
        }
        return cache.getBiome(level, x, z);
    }

    private static int calculateTerrainStability(TerrainSamplingCache cache,
                                                 PathTerrainField terrain,
                                                 BlockPos pos,
                                                 int y,
                                                 ServerLevel level,
                                                 int step) {
        int cost = 0;
        if (Math.abs(heightSampler(cache, terrain, pos.getX() + step, pos.getZ(), level) - y) > 0) cost++;
        if (Math.abs(heightSampler(cache, terrain, pos.getX() - step, pos.getZ(), level) - y) > 0) cost++;
        if (Math.abs(heightSampler(cache, terrain, pos.getX(), pos.getZ() + step, level) - y) > 0) cost++;
        if (Math.abs(heightSampler(cache, terrain, pos.getX(), pos.getZ() - step, level) - y) > 0) cost++;
        return cost;
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

    private static final class Meet {
        final Node forward;
        final Node backward;

        Meet(Node forward, Node backward) {
            this.forward = forward;
            this.backward = backward;
        }
    }

    public record PathCalculationResult(List<RoadSegmentPlacement> segments, PathTerrainField terrain) {
        public static PathCalculationResult failure() {
            return new PathCalculationResult(null, null);
        }
    }
}
