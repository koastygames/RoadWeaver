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
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 贪婪前进寻路器：无终点，沿大方向持续前进
 * 核心策略：方向偏好主导 + 地形代价辅助，卡住时自动降低地形权重强制翻越
 */
public final class GreedyForwardPathfinder implements GreedyPathfinder {
    private static final double WATER_COLUMN_PENALTY = 800.0;
    private static final int BIOME_BASE_COST = 12;
    private static final int STUCK_THRESHOLD = 8;
    private static final double STUCK_TERRAIN_SCALE = 0.15;

    @Override
    public PathResult findPath(BlockPos start, double dirX, double dirZ,
                               int maxSteps, int width,
                               ServerLevel level, TerrainSamplingCache cache,
                               PathfindingCostConfig cfg, double dirBias) {
        int d = cfg.aStarStep();
        int[][] offsets = {
                {d, 0}, {-d, 0}, {0, d}, {0, -d},
                {d, d}, {d, -d}, {-d, d}, {-d, -d}
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
                if (Thread.currentThread().isInterrupted()) break;

                boolean stuck = stepsSinceProgress >= STUCK_THRESHOLD;
                double terrainScale = stuck ? STUCK_TERRAIN_SCALE : 1.0;

                BlockPos best = null;
                double bestCost = Double.MAX_VALUE;

                for (int[] off : offsets) {
                    int nx = current.getX() + off[0];
                    int nz = current.getZ() + off[1];
                    long key = posKey(nx, nz);
                    if (visited.contains(key)) continue;

                    int ny = cache.height(level, nx, nz);
                    BlockPos np = new BlockPos(nx, ny, nz);

                    double cost = evaluateStep(
                            current, np, off, d, dirX, dirZ, dirBias,
                            level, cache, cfg, terrainScale);
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = np;
                    }
                }

                if (best == null) break;

                double dx = best.getX() - current.getX();
                double dz = best.getZ() - current.getZ();
                double fwd = dx * dirX + dz * dirZ;

                if (fwd > d * 0.3) {
                    stepsSinceProgress = 0;
                } else {
                    stepsSinceProgress++;
                }

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

    private double evaluateStep(
            BlockPos current, BlockPos next, int[] offset, int d,
            double dirX, double dirZ, double dirBias,
            ServerLevel level, TerrainSamplingCache cache,
            PathfindingCostConfig cfg, double terrainScale) {

        // 方向代价（主导因素）
        double ox = offset[0], oz = offset[1];
        double len = Math.sqrt(ox * ox + oz * oz);
        double nox = ox / len, noz = oz / len;
        double dot = nox * dirX + noz * dirZ;
        double dirCost = (1.0 - dot) * (1.0 - dot) * dirBias;
        if (dot < -0.2) dirCost += 50000.0;

        // 地形代价（辅助因素）
        int elevation = Math.abs(next.getY() - current.getY());
        double elevCost = elevation * 30.0 + Math.min(elevation * elevation * 5.0, 2000.0);
        double slope = (double) elevation / Math.max(1, d);
        if (slope > 0.8) elevCost += 3000.0;

        boolean isDiag = (Math.abs(offset[0]) + Math.abs(offset[1])) == 2 * d;
        double stepCost = isDiag ? cfg.diagStepCost() : cfg.orthoStepCost();

        int stability = terrainStability(cache, next, next.getY(), level, d);

        boolean waterCol = cache.isColumnWater(level, next.getX(), next.getZ());
        boolean nearWater = cache.isNearWaterLike(level, next.getX(), next.getZ(), d);
        int oceanFloor = cache.oceanFloor(level, next.getX(), next.getZ());
        int waterDepth = Math.max(0, level.getSeaLevel() - oceanFloor);
        double waterPenalty = 0.0;
        if (waterCol) {
            waterPenalty = WATER_COLUMN_PENALTY + waterDepth * 40.0;
        }
        double nearWaterPenalty = nearWater ? 200.0 : 0.0;

        Holder<Biome> biome = cache.getBiome(level, next.getX(), next.getZ());
        int biomeCost = (biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN)
                || biome.is(BiomeTags.IS_DEEP_OCEAN)) ? (BIOME_BASE_COST * 4) : 0;

        double terrain = (elevCost + stepCost + stability * 10.0
                + biomeCost * 2.0 + waterPenalty + nearWaterPenalty) * terrainScale;

        return dirCost + terrain;
    }

    private int terrainStability(TerrainSamplingCache cache, BlockPos pos, int y, ServerLevel level, int step) {
        int cost = 0;
        if (Math.abs(cache.height(level, pos.getX() + step, pos.getZ()) - y) > 0) cost++;
        if (Math.abs(cache.height(level, pos.getX() - step, pos.getZ()) - y) > 0) cost++;
        if (Math.abs(cache.height(level, pos.getX(), pos.getZ() + step) - y) > 0) cost++;
        if (Math.abs(cache.height(level, pos.getX(), pos.getZ() - step) - y) > 0) cost++;
        return cost;
    }

    private long posKey(BlockPos p) {
        return posKey(p.getX(), p.getZ());
    }

    private long posKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
