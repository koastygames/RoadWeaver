package net.shiroha233.roadweaver.features.longdrive.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.pathfinding.GreedyPathfinder;
import net.shiroha233.roadweaver.pathfinding.PathResult;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 负责为长途主干道执行贪婪式前进寻路。
 */
public final class GreedyForwardPathfinder implements GreedyPathfinder {
    private static final double WATER_COLUMN_PENALTY = 800.0;
    private static final int BIOME_BASE_COST = 12;
    private static final int STUCK_THRESHOLD = 8;
    private static final double STUCK_TERRAIN_SCALE = 0.15;

    @Override
    public PathResult findPath(BlockPos start,
            double dirX,
            double dirZ,
            int maxSteps,
            int width,
            ServerLevel level,
            TerrainSamplingCache cache,
            PathfindingCostConfig config,
            double dirBias) {
        return findPath(start, dirX, dirZ, maxSteps, width, level, cache, null, config, dirBias);
    }

    @Override
    public PathResult findPath(BlockPos start,
            double dirX,
            double dirZ,
            int maxSteps,
            int width,
            ServerLevel level,
            TerrainSamplingCache cache,
            PathTerrainField terrain,
            PathfindingCostConfig config,
            double dirBias) {
        int stepSize = config.aStarStep();
        int[][] offsets = {
                {stepSize, 0}, {-stepSize, 0}, {0, stepSize}, {0, -stepSize},
                {stepSize, stepSize}, {stepSize, -stepSize}, {-stepSize, stepSize}, {-stepSize, -stepSize}
        };

        List<BlockPos> rawPath = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        BlockPos current = start;
        rawPath.add(current);
        visited.add(posKey(current));
        ThreadPoolManager.resetThrottle();

        int stepsSinceProgress = 0;
        try {
            for (int step = 0; step < maxSteps; step++) {
                ThreadPoolManager.throttle();
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                boolean stuck = stepsSinceProgress >= STUCK_THRESHOLD;
                double terrainScale = stuck ? STUCK_TERRAIN_SCALE : 1.0;
                BlockPos best = null;
                double bestCost = Double.MAX_VALUE;

                for (int[] offset : offsets) {
                    int nextX = current.getX() + offset[0];
                    int nextZ = current.getZ() + offset[1];
                    long key = posKey(nextX, nextZ);
                    if (visited.contains(key)) {
                        continue;
                    }
                    if (terrain != null && !terrain.contains(nextX, nextZ)) {
                        continue;
                    }

                    int nextY = sampleHeight(cache, terrain, level, nextX, nextZ);
                    BlockPos next = new BlockPos(nextX, nextY, nextZ);
                    double cost = evaluateStep(
                            current,
                            next,
                            offset,
                            stepSize,
                            dirX,
                            dirZ,
                            dirBias,
                            level,
                            cache,
                            terrain,
                            config,
                            terrainScale);
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = next;
                    }
                }

                if (best == null) {
                    break;
                }

                double deltaX = best.getX() - current.getX();
                double deltaZ = best.getZ() - current.getZ();
                double forward = deltaX * dirX + deltaZ * dirZ;
                stepsSinceProgress = forward > stepSize * 0.3 ? 0 : stepsSinceProgress + 1;

                rawPath.add(best);
                visited.add(posKey(best));
                current = best;
            }
        } finally {
            ThreadPoolManager.clearThrottle();
        }

        if (rawPath.size() < 3) {
            return PathResult.failure();
        }

        List<RoadSegmentPlacement> segments = rawPath.stream()
                .map(pos -> new RoadSegmentPlacement(pos, List.of(pos)))
                .toList();
        return PathResult.success(segments);
    }

    private double evaluateStep(BlockPos current,
            BlockPos next,
            int[] offset,
            int stepSize,
            double dirX,
            double dirZ,
            double dirBias,
            ServerLevel level,
            TerrainSamplingCache cache,
            PathTerrainField terrain,
            PathfindingCostConfig config,
            double terrainScale) {
        double offsetX = offset[0];
        double offsetZ = offset[1];
        double length = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        double normX = offsetX / length;
        double normZ = offsetZ / length;
        double alignment = normX * dirX + normZ * dirZ;
        double directionCost = (1.0 - alignment) * (1.0 - alignment) * dirBias;
        if (alignment < -0.2) {
            directionCost += 50_000.0;
        }

        int elevation = Math.abs(next.getY() - current.getY());
        double elevationCost = elevation * 30.0 + Math.min(elevation * elevation * 5.0, 2_000.0);
        double slope = (double) elevation / Math.max(1, stepSize);
        if (slope > 0.8) {
            elevationCost += 3_000.0;
        }

        boolean diagonal = (Math.abs(offset[0]) + Math.abs(offset[1])) == 2 * stepSize;
        double stepCost = diagonal ? config.diagStepCost() : config.orthoStepCost();
        int stability = terrainStability(cache, terrain, next, next.getY(), level, stepSize);

        boolean waterColumn = terrain != null
                ? terrain.isColumnWater(next.getX(), next.getZ())
                : cache.isColumnWater(level, next.getX(), next.getZ());
        boolean nearWater = terrain != null
                ? terrain.isNearWater(next.getX(), next.getZ(), stepSize)
                : cache.isNearWaterLike(level, next.getX(), next.getZ(), stepSize);
        int oceanFloor = terrain != null
                ? terrain.oceanFloor(next.getX(), next.getZ())
                : cache.oceanFloor(level, next.getX(), next.getZ());
        int seaLevel = terrain != null ? terrain.seaLevel() : level.getSeaLevel();
        int waterDepth = Math.max(0, seaLevel - oceanFloor);
        double waterPenalty = waterColumn ? WATER_COLUMN_PENALTY + waterDepth * 40.0 : 0.0;
        double nearWaterPenalty = nearWater ? 200.0 : 0.0;

        Holder<Biome> biome = terrain != null
                ? terrain.biome(next.getX(), next.getZ())
                : cache.getBiome(level, next.getX(), next.getZ());
        int biomeCost = (biome.is(BiomeTags.IS_RIVER)
                || biome.is(BiomeTags.IS_OCEAN)
                || biome.is(BiomeTags.IS_DEEP_OCEAN)) ? (BIOME_BASE_COST * 4) : 0;

        double terrainCost = (elevationCost + stepCost + stability * 10.0
                + biomeCost * 2.0 + waterPenalty + nearWaterPenalty) * terrainScale;
        return directionCost + terrainCost;
    }

    private int terrainStability(TerrainSamplingCache cache,
            PathTerrainField terrain,
            BlockPos pos,
            int y,
            ServerLevel level,
            int stepSize) {
        int cost = 0;
        if (Math.abs(sampleHeight(cache, terrain, level, pos.getX() + stepSize, pos.getZ()) - y) > 0) {
            cost++;
        }
        if (Math.abs(sampleHeight(cache, terrain, level, pos.getX() - stepSize, pos.getZ()) - y) > 0) {
            cost++;
        }
        if (Math.abs(sampleHeight(cache, terrain, level, pos.getX(), pos.getZ() + stepSize) - y) > 0) {
            cost++;
        }
        if (Math.abs(sampleHeight(cache, terrain, level, pos.getX(), pos.getZ() - stepSize) - y) > 0) {
            cost++;
        }
        return cost;
    }

    private int sampleHeight(TerrainSamplingCache cache,
            PathTerrainField terrain,
            ServerLevel level,
            int x,
            int z) {
        if (terrain != null && terrain.contains(x, z)) {
            return terrain.height(x, z);
        }
        return cache.height(level, x, z);
    }

    private long posKey(BlockPos pos) {
        return posKey(pos.getX(), pos.getZ());
    }

    private long posKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
