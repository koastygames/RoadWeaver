package net.shiroha233.roadweaver.features.path.pathlogic.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.PathfindingConfig;
import net.shiroha233.roadweaver.config.RoadGenerationConfig;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.features.path.pathlogic.core.RoadDirection;

import java.util.*;

public final class RoadPathCalculator {
    private RoadPathCalculator() {}

    /**
     * 计算 A* 道路路径（带配置参数）
     * 
     * @param startIn   起点
     * @param endIn     终点
     * @param width     道路宽度
     * @param level     服务端世界
     * @param maxSteps  最大步数
     * @param cache     地形采样缓存
     * @param cfg       道路生成配置快照
     */
    public static List<Records.RoadSegmentPlacement> calculateAStarRoadPath(BlockPos startIn,
                                                                            BlockPos endIn,
                                                                            int width,
                                                                            ServerLevel level,
                                                                            int maxSteps,
                                                                            TerrainSamplingCache cache,
                                                                            RoadGenerationConfig cfg) {
        PathfindingConfig pathCfg = cfg.pathfinding();
        int dGrid = pathCfg.effectiveAStarStep();
        int sx = snapToGrid(startIn.getX(), dGrid);
        int sz = snapToGrid(startIn.getZ(), dGrid);
        int ex = snapToGrid(endIn.getX(), dGrid);
        int ez = snapToGrid(endIn.getZ(), dGrid);

        BlockPos start = new BlockPos(sx, startIn.getY(), sz);
        BlockPos end = new BlockPos(ex, endIn.getY(), ez);

        BlockPos startGround = new BlockPos(start.getX(), heightSampler(cache, start.getX(), start.getZ(), level), start.getZ());
        BlockPos endGround = new BlockPos(end.getX(), heightSampler(cache, end.getX(), end.getZ(), level), end.getZ());

        if (cfg.hierarchicalPathfindingEnabled()) {
            // 注意：粗预热仅用于填充 TerrainSamplingCache，不参与最终道路路径。
            TerrainCachePrewarmer.prewarmAlongRoute(
                    startGround,
                    endGround,
                    level,
                    Math.max(500, maxSteps / 4),
                    cache);
        }

        List<Records.RoadSegmentPlacement> fastSegments = calculateDirect(startGround, endGround, width, level, maxSteps, cache, cfg, pathCfg);
        if (fastSegments == null || fastSegments.isEmpty()) {
            return fastSegments;
        }

        // 精采样已在寻路重建阶段对 rawPath 节点完成，并通过插值传递到中心点。
        return fastSegments;
    }

    private static List<Records.RoadSegmentPlacement> calculateDirect(BlockPos startGround,
                                                                      BlockPos endGround,
                                                                      int width,
                                                                      ServerLevel level,
                                                                      int maxSteps,
                                                                      TerrainSamplingCache cache,
                                                                      RoadGenerationConfig cfg,
                                                                      PathfindingConfig pathCfg) {
        List<Records.RoadSegmentPlacement> land;
        var algo = cfg.pathfindingAlgorithm();

        if (algo == net.shiroha233.roadweaver.config.ModConfig.PathfindingAlgorithm.GRADIENT_DESCENT) {
            land = GradientDescentPathfinder.calculatePath(startGround, endGround, width, level, maxSteps, cache, pathCfg);
        } else if (algo == net.shiroha233.roadweaver.config.ModConfig.PathfindingAlgorithm.ASTAR_BIDIRECTIONAL) {
            land = BidirectionalAStarPathfinder.calculateLandPath(startGround, endGround, width, level, maxSteps, cache, pathCfg);
        } else {
            land = BasicAStarPathfinder.calculateLandPath(startGround, endGround, width, level, maxSteps, cache, pathCfg);
        }
        return land;
    }

    /**
     * 计算地形稳定性：检查四个方向的高度差。
     * 使用 A* 步长采样，确保采样点与邻居网格对齐，提高缓存命中率。
     */
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
        return cache.isNearWaterLike(level, x, z, 16); // 默认步长
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

    /**
     * 提取道路跨度（桥梁、隧道等）
     * 
     * @param segments 道路段落
     * @param level    服务端世界
     * @param cache    地形采样缓存
     * @param cfg      寻路配置快照
     */
    public static List<Records.RoadSpan> extractSpans(List<Records.RoadSegmentPlacement> segments, 
                                                       ServerLevel level, 
                                                       TerrainSamplingCache cache,
                                                       PathfindingConfig cfg) {
        List<Records.RoadSpan> spans = new ArrayList<>();
        if (segments == null || segments.isEmpty()) return spans;

        List<BlockPos> centers = new ArrayList<>(segments.size());
        for (Records.RoadSegmentPlacement seg : segments) {
            centers.add(seg.middlePos());
        }

        AccurateHeightSampler accurate = AccurateHeightSampler.create(level);

        // 从配置快照读取最小水深阈值
        int minWaterDepth = cfg.bridgeMinWaterDepth();
        int sea = level.getSeaLevel();

        boolean inWater = false;
        int waterStart = -1;
        for (int i = 0; i < centers.size(); i++) {
            BlockPos p = centers.get(i);

            // 检测是否是水体且水深达到阈值（使用 getBaseHeight 二次采样，避免 FastHeightSampler 偏差）
            boolean isWaterBiome = isWaterLike(cache, p.getX(), p.getZ(), level);
            if (!isWaterBiome) {
                if (!inWater) {
                    continue;
                }
                // 从水域回到陆地，结束桥梁跨度
                int startIdx = Math.max(0, waterStart - 1);
                int endIdx = i;
                BlockPos start = centers.get(startIdx);
                BlockPos end = centers.get(Math.min(endIdx, centers.size() - 1));
                spans.add(new Records.RoadSpan(start, end, Records.SpanType.BRIDGE));
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
                // 离开水域，创建桥梁跨度
                int startIdx = Math.max(0, waterStart - 1);
                int endIdx = i;
                BlockPos start = centers.get(startIdx);
                BlockPos end = centers.get(Math.min(endIdx, centers.size() - 1));
                spans.add(new Records.RoadSpan(start, end, Records.SpanType.BRIDGE));
                inWater = false;
                waterStart = -1;
            }
        }
        // 修复：如果道路在水中结束（最后一段仍在水上），需要补上这个 span
        if (inWater && waterStart >= 0) {
            int startIdx = Math.max(0, waterStart - 1);
            BlockPos start = centers.get(startIdx);
            BlockPos end = centers.get(centers.size() - 1);
            spans.add(new Records.RoadSpan(start, end, Records.SpanType.BRIDGE));
        }

        final int SLOPE_ABS_THRESHOLD = 4;
        final int RUN_MIN_LENGTH = 3;
        int runStart = -1;
        for (int i = 1; i < centers.size(); i++) {
            BlockPos a = centers.get(i - 1);
            BlockPos b = centers.get(i);
            // 隧道识别只看坡度：优先使用二次精采样后的中心点高度
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
                    spans.add(new Records.RoadSpan(s, e, Records.SpanType.TUNNEL));
                }
                runStart = -1;
            }
        }
        if (runStart >= 0) {
            int len = centers.size() - runStart;
            if (len >= RUN_MIN_LENGTH) {
                BlockPos s = centers.get(runStart);
                BlockPos e = centers.get(centers.size() - 1);
                spans.add(new Records.RoadSpan(s, e, Records.SpanType.TUNNEL));
            }
        }

        return spans;
    }
}
