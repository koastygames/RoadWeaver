package net.shiroha233.roadweaver.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.sqlite.StructureCacheMigrator;
import net.shiroha233.roadweaver.persistence.sqlite.StructureSqliteStorage;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.search.StructureIndexService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Path 分叉路网规划（重做版）。
 *
 * 职责：
 * - 只在“已生成的公路（Highway，roadType=2）”附近规划 Path 连接
 * - 对每个结构点，只连接到最近的公路位置（形成从公路分叉出去的支路）
 * - 不做全局三角剖分/稠密连边，避免把分叉路做成网状主干
 */
public final class PathBranchPlanningService {
    private PathBranchPlanningService() {
    }

    private static final ConcurrentHashMap<Level, Set<Long>> PROCESSED_HIGHWAY_EDGES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Level, Boolean> INITIAL_PLANNED = new ConcurrentHashMap<>();

    // 每 tick 最多回补多少条“已完成但未触发分叉规划”的公路边
    private static final int BACKFILL_BUDGET_PER_TICK = 2;

    // Highway 点集构建的网格大小（2^7=128）
    private static final int POINT_CELL_SHIFT = 7;

    public static void resetAll() {
        PROCESSED_HIGHWAY_EDGES.clear();
        INITIAL_PLANNED.clear();
    }

    /**
     * 服务器 tick：
     * - 用于“存档重进/服务器重启”时回补已完成公路的分叉规划
     * - 用于初始区域（出生点附近）一次性规划
     */
    public static void tick(ServerLevel level) {
        if (level == null) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;

        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.highwayEnabled()) return;

        // 初始规划：只在“出生点范围内已存在已生成的 Highway(roadType=2)”时才算完成。
        // 这样能保证 Path 的分叉规划严格发生在公路生成之后，避免过早执行导致后续不再补做。
        if (!Boolean.TRUE.equals(INITIAL_PLANNED.get(level))) {
            if (planInitialAroundSpawn(level)) {
                INITIAL_PLANNED.put(level, Boolean.TRUE);
            }
        }

        // 回补：每 tick 处理少量已完成的公路边
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> highways = provider.getHighwayConnections(level);
        if (highways == null || highways.isEmpty()) return;

        int budget = BACKFILL_BUDGET_PER_TICK;
        for (Records.StructureConnection hc : highways) {
            if (budget <= 0) break;
            if (hc == null) continue;
            if (hc.status() != Records.ConnectionStatus.COMPLETED) continue;
            long k = PlanningUtils.edgeKey(hc.from(), hc.to());
            Set<Long> seen = PROCESSED_HIGHWAY_EDGES.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet());
            if (!seen.add(k)) continue;
            planBranchesAroundEdge(level, cfg, hc);
            budget--;
        }
    }

    /**
     * 在单条公路（Highway）生成完成后调用：围绕该边规划分叉支路。
     */
    public static void onHighwayCompleted(ServerLevel level, Records.StructureConnection highwayEdge) {
        if (level == null || highwayEdge == null) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;

        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.highwayEnabled()) return;

        long k = PlanningUtils.edgeKey(highwayEdge.from(), highwayEdge.to());
        Set<Long> seen = PROCESSED_HIGHWAY_EDGES.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet());
        if (!seen.add(k)) return;

        planBranchesAroundEdge(level, cfg, highwayEdge);
    }

    private static void planBranchesAroundEdge(ServerLevel level, ModConfig cfg, Records.StructureConnection highwayEdge) {
        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        int maxBranchDist = Math.max(128, gridBlocks / 2);

        int minX = Math.min(highwayEdge.from().getX(), highwayEdge.to().getX()) - maxBranchDist;
        int maxX = Math.max(highwayEdge.from().getX(), highwayEdge.to().getX()) + maxBranchDist;
        int minZ = Math.min(highwayEdge.from().getZ(), highwayEdge.to().getZ()) - maxBranchDist;
        int maxZ = Math.max(highwayEdge.from().getZ(), highwayEdge.to().getZ()) + maxBranchDist;

        planBranchesInRect(level, cfg, minX, minZ, maxX, maxZ);
    }

    public static boolean planInitialAroundSpawn(ServerLevel level) {
        if (level == null) return false;
        if (!Level.OVERWORLD.equals(level.dimension())) return false;

        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.highwayEnabled()) return false;

        int radiusBlocks = Math.max(16, cfg.highwayPlanningRadiusBlocks());
        BlockPos spawn = level.getSharedSpawnPos();

        int minX = spawn.getX() - radiusBlocks;
        int maxX = spawn.getX() + radiusBlocks;
        int minZ = spawn.getZ() - radiusBlocks;
        int maxZ = spawn.getZ() + radiusBlocks;

        return planBranchesInRect(level, cfg, minX, minZ, maxX, maxZ);
    }

    private static boolean planBranchesInRect(ServerLevel level, ModConfig cfg,
                                              int minBlockX, int minBlockZ,
                                              int maxBlockX, int maxBlockZ) {
        if (level == null || cfg == null) return false;

        // 1) 收集矩形内“已生成公路（roadType=2）”的候选锚点（用于吸附）
        List<BlockPos> highwayPoints = collectHighwayPoints(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        if (highwayPoints.isEmpty()) return false;

        // 2) 建一个简单网格索引加速最近点查询
        Map<Long, List<BlockPos>> pointGrid = buildPointGrid(highwayPoints);

        // 3) 收集结构点（只在该矩形内）
        List<BlockPos> structures = collectStructurePoints(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        if (structures.isEmpty()) return true;

        // 4) 只给“还没连过路”的结构做一次分叉
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> existing = provider.getStructureConnections(level);

        Set<Long> existingEdgeKeys = new HashSet<>();
        Set<Long> alreadyConnectedStructures = new HashSet<>();
        if (existing != null) {
            for (Records.StructureConnection c : existing) {
                if (c == null) continue;
                existingEdgeKeys.add(PlanningUtils.edgeKey(c.from(), c.to()));
                alreadyConnectedStructures.add(PlanningUtils.pos2dKey(c.from()));
                alreadyConnectedStructures.add(PlanningUtils.pos2dKey(c.to()));
            }
        }

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        int maxBranchDist = Math.max(128, gridBlocks / 2);
        long maxDist2 = (long) maxBranchDist * (long) maxBranchDist;

        ArrayList<Records.StructureConnection> incoming = new ArrayList<>();
        for (BlockPos s : structures) {
            BlockPos s2d = new BlockPos(s.getX(), 0, s.getZ());
            long sk = PlanningUtils.pos2dKey(s2d);
            if (alreadyConnectedStructures.contains(sk)) continue;

            BlockPos anchor = findNearest(pointGrid, s2d, maxBranchDist);
            if (anchor == null) continue;

            long d2 = dist2XZ(s2d, anchor);
            if (d2 > maxDist2) continue;

            long ek = PlanningUtils.edgeKey(s2d, anchor);
            if (existingEdgeKeys.contains(ek)) continue;

            incoming.add(new Records.StructureConnection(s2d, anchor, Records.ConnectionStatus.PLANNED));
            existingEdgeKeys.add(ek);
            alreadyConnectedStructures.add(sk);
        }

        if (incoming.isEmpty()) return true;

        List<Records.StructureConnection> merged = mergeConnections(existing, incoming);
        provider.setStructureConnections(level, merged);
        return true;
    }

    private static List<BlockPos> collectHighwayPoints(ServerLevel level,
                                                      int minBlockX, int minBlockZ,
                                                      int maxBlockX, int maxBlockZ) {
        List<Records.RoadData> roads = RoadShardStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        if (roads == null || roads.isEmpty()) return List.of();

        ArrayList<BlockPos> points = new ArrayList<>();
        for (Records.RoadData rd : roads) {
            if (rd == null) continue;
            if (rd.roadType() != 2) continue;
            if (rd.roadSegmentList() == null) continue;

            for (Records.RoadSegmentPlacement seg : rd.roadSegmentList()) {
                if (seg == null) continue;
                BlockPos p = seg.middlePos();
                if (p == null) continue;
                points.add(new BlockPos(p.getX(), 0, p.getZ()));
            }
        }

        return points;
    }

    private static Map<Long, List<BlockPos>> buildPointGrid(List<BlockPos> points) {
        HashMap<Long, List<BlockPos>> grid = new HashMap<>();
        for (BlockPos p : points) {
            int gx = p.getX() >> POINT_CELL_SHIFT;
            int gz = p.getZ() >> POINT_CELL_SHIFT;
            long k = (((long) gx) << 32) ^ (gz & 0xffffffffL);
            grid.computeIfAbsent(k, kk -> new ArrayList<>()).add(p);
        }
        return grid;
    }

    private static BlockPos findNearest(Map<Long, List<BlockPos>> grid, BlockPos target, int maxDistBlocks) {
        int cellRadius = Math.max(1, (maxDistBlocks >> POINT_CELL_SHIFT) + 1);
        int gx0 = target.getX() >> POINT_CELL_SHIFT;
        int gz0 = target.getZ() >> POINT_CELL_SHIFT;

        BlockPos best = null;
        long bestD2 = Long.MAX_VALUE;

        for (int dx = -cellRadius; dx <= cellRadius; dx++) {
            for (int dz = -cellRadius; dz <= cellRadius; dz++) {
                long k = (((long) (gx0 + dx)) << 32) ^ ((gz0 + dz) & 0xffffffffL);
                List<BlockPos> list = grid.get(k);
                if (list == null || list.isEmpty()) continue;
                for (BlockPos p : list) {
                    long d2 = dist2XZ(target, p);
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = p;
                    }
                }
            }
        }

        return best;
    }

    private static List<BlockPos> collectStructurePoints(ServerLevel level,
                                                        int minBlockX, int minBlockZ,
                                                        int maxBlockX, int maxBlockZ) {
        ArrayList<BlockPos> out = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();

        // 迁移 legacy 并按需触发预测扫描：结构点统一走 SQLite
        StructureCacheMigrator.migrateLegacyIfNeeded(level);
        ModConfig cfg = ConfigService.get();
        boolean allowPredicted = cfg != null
                && cfg.structurePredictionEnabled()
                && cfg.isStructurePredictionEnabledForDimension(level.dimension().location().toString());
        if (allowPredicted) {
            StructureIndexService.predictAndVerifyInRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        }

        int[] sources = allowPredicted
                ? new int[]{StructureSqliteStorage.SOURCE_MANUAL, StructureSqliteStorage.SOURCE_LEGACY, StructureSqliteStorage.SOURCE_PREDICTED}
                : new int[]{StructureSqliteStorage.SOURCE_MANUAL, StructureSqliteStorage.SOURCE_LEGACY};
        List<Records.StructureInfo> cached = StructureSqliteStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, sources);
        if (cached != null && !cached.isEmpty()) {
            for (Records.StructureInfo info : cached) {
                if (info == null || info.pos() == null) continue;
                BlockPos p = info.pos();
                int x = p.getX(), z = p.getZ();
                if (x < minBlockX || x > maxBlockX || z < minBlockZ || z > maxBlockZ) continue;
                BlockPos q = new BlockPos(x, 0, z);
                long k = PlanningUtils.pos2dKey(q);
                if (seen.add(k)) out.add(q);
            }
        }

        return out;
    }

    private static long dist2XZ(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static List<Records.StructureConnection> mergeConnections(List<Records.StructureConnection> existing,
                                                                      List<Records.StructureConnection> incoming) {
        HashSet<Long> seen = new HashSet<>();
        ArrayList<Records.StructureConnection> out = new ArrayList<>();
        if (existing != null) {
            for (Records.StructureConnection c : existing) {
                if (c == null) continue;
                long k = PlanningUtils.edgeKey(c.from(), c.to());
                if (seen.add(k)) out.add(c);
            }
        }
        for (Records.StructureConnection c : incoming) {
            if (c == null) continue;
            long k = PlanningUtils.edgeKey(c.from(), c.to());
            if (seen.add(k)) out.add(c);
        }
        return out;
    }
}
