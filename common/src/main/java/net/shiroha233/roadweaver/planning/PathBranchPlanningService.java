package net.shiroha233.roadweaver.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.helpers.LevelCompat;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
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
 * 鏀矾瑙勫垝鏈嶅姟
 */
public final class PathBranchPlanningService {
    private PathBranchPlanningService() {}

    private static final ConcurrentHashMap<Level, Set<Long>> PROCESSED_HIGHWAY_EDGES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Level, Boolean> INITIAL_PLANNED = new ConcurrentHashMap<>();
    private static final int BACKFILL_BUDGET_PER_TICK = 2;
    private static final int POINT_CELL_SHIFT = 7;

    public static void resetAll() {
        PROCESSED_HIGHWAY_EDGES.clear();
        INITIAL_PLANNED.clear();
    }

    public static void tick(ServerLevel level) {
        if (level == null) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;
        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.highway().enabled()) return;
        if (!Boolean.TRUE.equals(INITIAL_PLANNED.get(level))) {
            if (planInitialAroundSpawn(level)) {
                INITIAL_PLANNED.put(level, Boolean.TRUE);
            }
        }
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> highways = provider.getHighwayConnections(level);
        if (highways == null || highways.isEmpty()) return;
        int budget = BACKFILL_BUDGET_PER_TICK;
        for (StructureConnection hc : highways) {
            if (budget <= 0) break;
            if (hc == null) continue;
            if (hc.status() != ConnectionStatus.COMPLETED) continue;
            long k = PlanningUtils.edgeKey(hc.from(), hc.to());
            Set<Long> seen = PROCESSED_HIGHWAY_EDGES.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet());
            if (!seen.add(k)) continue;
            planBranchesAroundEdge(level, cfg, hc);
            budget--;
        }
    }

    public static void onHighwayCompleted(ServerLevel level, StructureConnection highwayEdge) {
        if (level == null || highwayEdge == null) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;
        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.highway().enabled()) return;
        long k = PlanningUtils.edgeKey(highwayEdge.from(), highwayEdge.to());
        Set<Long> seen = PROCESSED_HIGHWAY_EDGES.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet());
        if (!seen.add(k)) return;
        planBranchesAroundEdge(level, cfg, highwayEdge);
    }

    private static void planBranchesAroundEdge(ServerLevel level, ModConfig cfg, StructureConnection highwayEdge) {
        int gridBlocks = Math.max(1, cfg.highway().gridBlocks());
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
        if (cfg == null || !cfg.highway().enabled()) return false;
        int radiusBlocks = Math.max(16, cfg.highway().planningRadiusBlocks());
        BlockPos spawn = LevelCompat.getWorldSpawnPos(level);
        int minX = spawn.getX() - radiusBlocks;
        int maxX = spawn.getX() + radiusBlocks;
        int minZ = spawn.getZ() - radiusBlocks;
        int maxZ = spawn.getZ() + radiusBlocks;
        return planBranchesInRect(level, cfg, minX, minZ, maxX, maxZ);
    }

    private static boolean planBranchesInRect(ServerLevel level, ModConfig cfg, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (level == null || cfg == null) return false;
        List<BlockPos> highwayPoints = collectHighwayPoints(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        if (highwayPoints.isEmpty()) return false;
        Map<Long, List<BlockPos>> pointGrid = buildPointGrid(highwayPoints);
        List<BlockPos> structures = collectStructurePoints(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        if (structures.isEmpty()) return true;
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> existing = provider.getStructureConnections(level);
        Set<Long> existingEdgeKeys = new HashSet<>();
        Set<Long> alreadyConnectedStructures = new HashSet<>();
        if (existing != null) {
            for (StructureConnection c : existing) {
                if (c == null) continue;
                existingEdgeKeys.add(PlanningUtils.edgeKey(c.from(), c.to()));
                alreadyConnectedStructures.add(PlanningUtils.pos2dKey(c.from()));
                alreadyConnectedStructures.add(PlanningUtils.pos2dKey(c.to()));
            }
        }
        int gridBlocks = Math.max(1, cfg.highway().gridBlocks());
        int maxBranchDist = Math.max(128, gridBlocks / 2);
        long maxDist2 = (long) maxBranchDist * (long) maxBranchDist;
        ArrayList<StructureConnection> incoming = new ArrayList<>();
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
            incoming.add(new StructureConnection(s2d, anchor, ConnectionStatus.PLANNED));
            existingEdgeKeys.add(ek);
            alreadyConnectedStructures.add(sk);
        }
        if (incoming.isEmpty()) return true;
        List<StructureConnection> merged = mergeConnections(existing, incoming);
        provider.setStructureConnections(level, merged);
        return true;
    }

    private static List<BlockPos> collectHighwayPoints(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        List<RoadData> roads = RoadShardStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        if (roads == null || roads.isEmpty()) return List.of();
        ArrayList<BlockPos> points = new ArrayList<>();
        for (RoadData rd : roads) {
            if (rd == null) continue;
            if (rd.roadType() != 2) continue;
            if (rd.roadSegmentList() == null) continue;
            for (RoadSegmentPlacement seg : rd.roadSegmentList()) {
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
                    if (d2 < bestD2) { bestD2 = d2; best = p; }
                }
            }
        }
        return best;
    }

    private static List<BlockPos> collectStructurePoints(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        ArrayList<BlockPos> out = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        ModConfig cfg = ConfigService.get();
        boolean allowPredicted = cfg != null
                && cfg.structurePrediction().enabled()
                && cfg.structurePrediction().isEnabledForDimension(level.dimension().identifier().toString());
        if (allowPredicted) {
            StructureIndexService.predictAndVerifyInRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        }
        int[] sources = allowPredicted
                ? new int[]{StructureSqliteStorage.SOURCE_MANUAL, StructureSqliteStorage.SOURCE_PREDICTED}
                : new int[]{StructureSqliteStorage.SOURCE_MANUAL};
        List<StructureInfo> cached = StructureSqliteStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, sources);
        if (cached != null && !cached.isEmpty()) {
            for (StructureInfo info : cached) {
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

    private static List<StructureConnection> mergeConnections(List<StructureConnection> existing, List<StructureConnection> incoming) {
        HashSet<Long> seen = new HashSet<>();
        ArrayList<StructureConnection> out = new ArrayList<>();
        if (existing != null) {
            for (StructureConnection c : existing) {
                if (c == null) continue;
                long k = PlanningUtils.edgeKey(c.from(), c.to());
                if (seen.add(k)) out.add(c);
            }
        }
        for (StructureConnection c : incoming) {
            if (c == null) continue;
            long k = PlanningUtils.edgeKey(c.from(), c.to());
            if (seen.add(k)) out.add(c);
        }
        return out;
    }
}
