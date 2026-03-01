package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.features.path.pathlogic.core.RoadDirection;
import net.shiroha233.roadweaver.pathfinding.PathResult;
import net.shiroha233.roadweaver.pathfinding.Pathfinder;
import net.shiroha233.roadweaver.pathfinding.PathfinderFactory;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainCachePrewarmer;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;

import java.util.*;

/**
 * 道路路径计算器
 */
public final class RoadPathCalculator {
    private RoadPathCalculator() {}

    public static List<RoadSegmentPlacement> calculateAStarRoadPath(BlockPos startIn,
                                                                    BlockPos endIn,
                                                                    int width,
                                                                    ServerLevel level,
                                                                    int maxSteps,
                                                                    TerrainSamplingCache cache,
                                                                    RoadGenerationConfig cfg) {
        PathfindingCostConfig pathCfg = cfg.pathfinding();
        int dGrid = pathCfg.effectiveAStarStep();
        int sx = snapToGrid(startIn.getX(), dGrid);
        int sz = snapToGrid(startIn.getZ(), dGrid);
        int ex = snapToGrid(endIn.getX(), dGrid);
        int ez = snapToGrid(endIn.getZ(), dGrid);

        BlockPos start = new BlockPos(sx, startIn.getY(), sz);
        BlockPos end = new BlockPos(ex, endIn.getY(), ez);

        boolean accurateSampling = pathCfg.isAccurateSampling();
        if (accurateSampling) {
            cache.enableHighPrecision(level);
        }

        try {
            BlockPos startGround = new BlockPos(start.getX(), heightSampler(cache, start.getX(), start.getZ(), level), start.getZ());
            BlockPos endGround = new BlockPos(end.getX(), heightSampler(cache, end.getX(), end.getZ(), level), end.getZ());

            if (pathCfg.hierarchicalPathfindingEnabled()) {
                TerrainCachePrewarmer.prewarmAlongRoute(
                        startGround,
                        endGround,
                        level,
                        Math.max(500, maxSteps / 4),
                        cache);
            }

            return calculateDirect(startGround, endGround, width, level, maxSteps, cache, pathCfg);
        } finally {
            if (accurateSampling) {
                cache.disableHighPrecision();
            }
        }
    }

    private static List<RoadSegmentPlacement> calculateDirect(BlockPos startGround,
                                                              BlockPos endGround,
                                                              int width,
                                                              ServerLevel level,
                                                              int maxSteps,
                                                              TerrainSamplingCache cache,
                                                              PathfindingCostConfig pathCfg) {
        var algo = pathCfg.pathfindingAlgorithm();
        Pathfinder pathfinder = PathfinderFactory.create(algo);
        PathResult result = pathfinder.findPath(startGround, endGround, width, level, maxSteps, cache, pathCfg);
        if (!result.success() || result.isEmpty()) {
            return null;
        }
        return result.segments();
    }

    static int calculateTerrainStability(TerrainSamplingCache cache, BlockPos pos, int y, ServerLevel level, int step) {
        int cost = 0;
        if (Math.abs(heightSampler(cache, pos.getX() + step, pos.getZ(), level) - y) > 0) cost++;
        if (Math.abs(heightSampler(cache, pos.getX() - step, pos.getZ(), level) - y) > 0) cost++;
        if (Math.abs(heightSampler(cache, pos.getX(), pos.getZ() + step, level) - y) > 0) cost++;
        if (Math.abs(heightSampler(cache, pos.getX(), pos.getZ() - step, level) - y) > 0) cost++;
        return cost;
    }

    static int heightSampler(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.height(level, x, z);
    }

    static boolean isWaterLike(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.isWaterLike(level, x, z);
    }

    static int oceanFloorSampler(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.oceanFloor(level, x, z);
    }

    static boolean isNearWaterLike(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.isNearWaterLike(level, x, z, 16);
    }

    static boolean isColumnWater(TerrainSamplingCache cache, int x, int z, ServerLevel level) {
        return cache.isColumnWater(level, x, z);
    }

    static int snapToGrid(int v, int gridSize) {
        return Math.floorDiv(v, gridSize) * gridSize;
    }

    static Set<BlockPos> generateWidth(BlockPos center, int radius, Set<BlockPos> cache, RoadDirection dir) {
        Set<BlockPos> set = new HashSet<>();
        int cx = center.getX();
        int cz = center.getZ();
        int y = 0;
        if (dir == RoadDirection.X_AXIS) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos p = new BlockPos(cx, y, cz + dz);
                if (cache.add(p)) set.add(p);
            }
        } else if (dir == RoadDirection.Z_AXIS) {
            for (int dx = -radius; dx <= radius; dx++) {
                BlockPos p = new BlockPos(cx + dx, y, cz);
                if (cache.add(p)) set.add(p);
            }
        } else {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dir == RoadDirection.DIAGONAL_2) {
                        if ((dx == -radius && dz == -radius) || (dx == radius && dz == radius)) continue;
                    }
                    if (dir == RoadDirection.DIAGONAL_1) {
                        if ((dx == -radius && dz == radius) || (dx == radius && dz == -radius)) continue;
                    }
                    BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                    if (cache.add(p)) set.add(p);
                }
            }
        }
        return set;
    }

    public static List<RoadSpan> extractSpans(List<RoadSegmentPlacement> segments,
                                               ServerLevel level,
                                               TerrainSamplingCache cache,
                                               int bridgeMinWaterDepth) {
        List<RoadSpan> spans = new ArrayList<>();
        if (segments == null || segments.isEmpty()) return spans;

        List<BlockPos> centers = new ArrayList<>(segments.size());
        for (RoadSegmentPlacement seg : segments) {
            centers.add(seg.middlePos());
        }

        AccurateHeightSampler accurate = AccurateHeightSampler.create(level);

        int minWaterDepth = bridgeMinWaterDepth;
        int sea = level.getSeaLevel();

        boolean inWater = false;
        int waterStart = -1;
        for (int i = 0; i < centers.size(); i++) {
            BlockPos p = centers.get(i);

            boolean isWaterBiome = isWaterLike(cache, p.getX(), p.getZ(), level);
            if (!isWaterBiome) {
                if (!inWater) {
                    continue;
                }
                int startIdx = Math.max(0, waterStart - 1);
                int endIdx = i;
                BlockPos start = centers.get(startIdx);
                BlockPos end = centers.get(Math.min(endIdx, centers.size() - 1));
                spans.add(new RoadSpan(start, end, SpanType.BRIDGE));
                inWater = false;
                waterStart = -1;
                continue;
            }

            int oceanFloor = accurate.oceanFloorWg(p.getX(), p.getZ());
            int surfaceY = accurate.worldSurfaceWg(p.getX(), p.getZ());
            boolean biomeWater = oceanFloor < sea;
            boolean heightWater = (surfaceY <= sea + 1) && (oceanFloor < surfaceY - 1);
            boolean waterColumn = biomeWater || heightWater;
            int waterDepth = waterColumn ? Math.max(0, sea - oceanFloor) : 0;
            boolean water = waterColumn && waterDepth >= minWaterDepth;

            if (water && !inWater) {
                inWater = true;
                waterStart = i;
            } else if (!water && inWater) {
                int startIdx = Math.max(0, waterStart - 1);
                int endIdx = i;
                BlockPos start = centers.get(startIdx);
                BlockPos end = centers.get(Math.min(endIdx, centers.size() - 1));
                spans.add(new RoadSpan(start, end, SpanType.BRIDGE));
                inWater = false;
                waterStart = -1;
            }
        }
        if (inWater && waterStart >= 0) {
            int startIdx = Math.max(0, waterStart - 1);
            BlockPos start = centers.get(startIdx);
            BlockPos end = centers.get(centers.size() - 1);
            spans.add(new RoadSpan(start, end, SpanType.BRIDGE));
        }

        final int SLOPE_ABS_THRESHOLD = 4;
        final int RUN_MIN_LENGTH = 3;
        int runStart = -1;
        for (int i = 1; i < centers.size(); i++) {
            BlockPos a = centers.get(i - 1);
            BlockPos b = centers.get(i);
            int ya = a.getY();
            int yb = b.getY();
            int dy = Math.abs(yb - ya);
            boolean steep = dy >= SLOPE_ABS_THRESHOLD;
            if (steep) {
                if (runStart < 0) runStart = i - 1;
            } else if (runStart >= 0) {
                int len = i - runStart;
                if (len >= RUN_MIN_LENGTH) {
                    BlockPos s = centers.get(runStart);
                    BlockPos e = centers.get(i);
                    spans.add(new RoadSpan(s, e, SpanType.TUNNEL));
                }
                runStart = -1;
            }
        }
        if (runStart >= 0) {
            int len = centers.size() - runStart;
            if (len >= RUN_MIN_LENGTH) {
                BlockPos s = centers.get(runStart);
                BlockPos e = centers.get(centers.size() - 1);
                spans.add(new RoadSpan(s, e, SpanType.TUNNEL));
            }
        }

        return spans;
    }
}
