package net.shiroha233.roadweaver.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.sqlite.StructureSqliteStorage;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.impl.KNNPlanner;
import net.shiroha233.roadweaver.search.StructureIndexService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highway 网格单元格四边驱动的 Path 规划服务
 */
public final class HighwayCellPathPlanningService {
    private HighwayCellPathPlanningService() {}

    private static final ConcurrentHashMap<Level, Set<Long>> PLANNED_CELLS = new ConcurrentHashMap<>();
    private static final int BORDER_ENTRY_MIN_BLOCKS = 48;
    private static final int BORDER_ENTRY_MAX_BLOCKS = 256;
    private static final int BACKFILL_BUDGET_PER_TICK = 2;

    public static void resetAll() {
        PLANNED_CELLS.clear();
    }

    public static void retainPlannedCellsInRect(ServerLevel level,
                                                int minCellGx, int minCellGz,
                                                int maxCellGx, int maxCellGz) {
        if (level == null) return;
        Set<Long> planned = PLANNED_CELLS.get(level);
        if (planned == null || planned.isEmpty()) return;

        planned.removeIf(k -> {
            int gx = (int) (k >> 32);
            int gz = (int) (k & 0xffffffffL);
            return gx < minCellGx || gx > maxCellGx || gz < minCellGz || gz > maxCellGz;
        });
    }

    public static void tick(ServerLevel level) {
        if (level == null) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;

        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.highway().enabled()) return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> highways = provider.getHighwayConnections(level);
        if (highways == null || highways.isEmpty()) return;

        int budget = BACKFILL_BUDGET_PER_TICK;
        for (StructureConnection c : highways) {
            if (budget <= 0) break;
            if (c == null) continue;
            if (!isTerminal(c.status())) continue;

            onHighwayEdgeFinalized(level, c);
            budget--;
        }
    }

    public static void onHighwayEdgeFinalized(ServerLevel level, StructureConnection highwayEdge) {
        if (level == null || highwayEdge == null) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;

        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.highway().enabled()) return;

        int gridBlocks = Math.max(1, cfg.highway().gridBlocks());

        int x0 = highwayEdge.from().getX();
        int z0 = highwayEdge.from().getZ();
        int x1 = highwayEdge.to().getX();
        int z1 = highwayEdge.to().getZ();

        if (z0 == z1) {
            int gz = floorDiv(z0, gridBlocks);
            int gx = Math.min(floorDiv(x0, gridBlocks), floorDiv(x1, gridBlocks));
            maybePlanCell(level, cfg, gx, gz - 1);
            maybePlanCell(level, cfg, gx, gz);
        } else if (x0 == x1) {
            int gx = floorDiv(x0, gridBlocks);
            int gz = Math.min(floorDiv(z0, gridBlocks), floorDiv(z1, gridBlocks));
            maybePlanCell(level, cfg, gx - 1, gz);
            maybePlanCell(level, cfg, gx, gz);
        }
    }

    public static void planCompletedCellsInRect(ServerLevel level,
                                                int minBlockX, int minBlockZ,
                                                int maxBlockX, int maxBlockZ) {
        if (level == null) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;

        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.highway().enabled()) return;

        int gridBlocks = Math.max(1, cfg.highway().gridBlocks());

        int gx0 = floorDiv(minBlockX, gridBlocks);
        int gz0 = floorDiv(minBlockZ, gridBlocks);
        int gx1 = floorDiv(maxBlockX, gridBlocks);
        int gz1 = floorDiv(maxBlockZ, gridBlocks);

        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                maybePlanCell(level, cfg, gx, gz);
            }
        }
    }

    private static void maybePlanCell(ServerLevel level, ModConfig cfg, int cellGx, int cellGz) {
        if (cellGx < -1_000_000 || cellGx > 1_000_000) return;
        if (cellGz < -1_000_000 || cellGz > 1_000_000) return;

        long cellKey = (((long) cellGx) << 32) ^ (cellGz & 0xffffffffL);
        Set<Long> planned = PLANNED_CELLS.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet());
        if (planned.contains(cellKey)) return;

        int gridBlocks = Math.max(1, cfg.highway().gridBlocks());

        BlockPos a = new BlockPos(cellGx * gridBlocks, 0, cellGz * gridBlocks);
        BlockPos b = new BlockPos((cellGx + 1) * gridBlocks, 0, cellGz * gridBlocks);
        BlockPos c = new BlockPos(cellGx * gridBlocks, 0, (cellGz + 1) * gridBlocks);
        BlockPos d = new BlockPos((cellGx + 1) * gridBlocks, 0, (cellGz + 1) * gridBlocks);

        Map<Long, ConnectionStatus> statusMap = buildHighwayStatusMap(level);
        ConnectionStatus ab = statusMap.get(PlanningUtils.edgeKey(a, b));
        ConnectionStatus ac = statusMap.get(PlanningUtils.edgeKey(a, c));
        ConnectionStatus bd = statusMap.get(PlanningUtils.edgeKey(b, d));
        ConnectionStatus cd = statusMap.get(PlanningUtils.edgeKey(c, d));

        if (!isTerminal(ab) || !isTerminal(ac) || !isTerminal(bd) || !isTerminal(cd)) {
            return;
        }

        planned.add(cellKey);
        planCell(level, cfg, cellGx, cellGz, a, b, c, d, ab, ac, bd, cd);
    }

    private static Map<Long, ConnectionStatus> buildHighwayStatusMap(ServerLevel level) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> list = provider.getHighwayConnections(level);

        HashMap<Long, ConnectionStatus> map = new HashMap<>();
        if (list == null || list.isEmpty()) return map;

        for (StructureConnection c : list) {
            if (c == null) continue;
            map.put(PlanningUtils.edgeKey(c.from(), c.to()), c.status());
        }
        return map;
    }

    private static void planCell(ServerLevel level,
                                 ModConfig cfg,
                                 int cellGx, int cellGz,
                                 BlockPos a, BlockPos b, BlockPos c, BlockPos d,
                                 ConnectionStatus ab,
                                 ConnectionStatus ac,
                                 ConnectionStatus bd,
                                 ConnectionStatus cd) {
        int gridBlocks = Math.max(1, cfg.highway().gridBlocks());

        int minX = cellGx * gridBlocks;
        int maxXExcl = (cellGx + 1) * gridBlocks;
        int minZ = cellGz * gridBlocks;
        int maxZExcl = (cellGz + 1) * gridBlocks;

        List<BlockPos> points = collectStructurePointsInCell(level, minX, minZ, maxXExcl, maxZExcl);
        if (points.isEmpty()) return;

        ArrayList<StructureConnection> incoming = new ArrayList<>();
        if (points.size() >= 2) {
            incoming.addAll(planStructureGraphLikeOriginal(level, points));
        }

        List<StructureConnection> borderEntries = buildBorderEntryEdges(level, cfg, points,
                minX, minZ, maxXExcl, maxZExcl,
                a, b, c, d,
                ab, ac, bd, cd);
        if (borderEntries != null && !borderEntries.isEmpty()) {
            incoming.addAll(borderEntries);
        } else {
            StructureConnection entry = buildSingleEntryEdge(level, cfg, points,
                    minX, minZ, maxXExcl, maxZExcl,
                    a, b, c, d,
                    ab, ac, bd, cd);
            if (entry != null) {
                incoming.add(entry);
            }
        }

        if (incoming.isEmpty()) return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> existing = provider.getStructureConnections(level);
        List<StructureConnection> merged = mergeConnections(existing, incoming);
        if (merged.size() != (existing == null ? 0 : existing.size())) {
            provider.setStructureConnections(level, merged);
        }
    }

    private static List<StructureConnection> buildBorderEntryEdges(ServerLevel level,
                                                                   ModConfig cfg,
                                                                   List<BlockPos> points,
                                                                   int minX, int minZ,
                                                                   int maxXExcl, int maxZExcl,
                                                                   BlockPos a, BlockPos b, BlockPos c, BlockPos d,
                                                                   ConnectionStatus ab,
                                                                   ConnectionStatus ac,
                                                                   ConnectionStatus bd,
                                                                   ConnectionStatus cd) {
        if (points == null || points.isEmpty()) return List.of();

        int gridBlocks = Math.max(1, cfg.highway().gridBlocks());
        int borderTh = Math.max(BORDER_ENTRY_MIN_BLOCKS, Math.min(BORDER_ENTRY_MAX_BLOCKS, gridBlocks / 8));
        int maxEntryDist = Math.max(128, Math.min(1024, gridBlocks / 2));
        long maxEntryDist2 = (long) maxEntryDist * (long) maxEntryDist;

        int maxX = maxXExcl - 1;
        int maxZ = maxZExcl - 1;

        int band = Math.max(64, Math.min(512, Math.max(1, cfg.highway().roadWidth()) * 16));
        List<BlockPos> highwayPoints = collectHighwayPointsNearCellBorder(level, minX, minZ, maxXExcl, maxZExcl, band);

        ArrayList<BlockPos> mids = new ArrayList<>();
        if (ab == ConnectionStatus.COMPLETED) mids.add(midpoint(a, b));
        if (ac == ConnectionStatus.COMPLETED) mids.add(midpoint(a, c));
        if (bd == ConnectionStatus.COMPLETED) mids.add(midpoint(b, d));
        if (cd == ConnectionStatus.COMPLETED) mids.add(midpoint(c, d));

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> existing = provider.getStructureConnections(level);

        ArrayList<StructureConnection> out = new ArrayList<>();
        HashSet<Long> existingEdgeKeys = new HashSet<>();
        if (existing != null) {
            for (StructureConnection c0 : existing) {
                if (c0 == null) continue;
                existingEdgeKeys.add(PlanningUtils.edgeKey(c0.from(), c0.to()));
            }
        }

        for (BlockPos p : points) {
            if (p == null) continue;

            int dx = Math.min(Math.abs(p.getX() - minX), Math.abs(maxX - p.getX()));
            int dz = Math.min(Math.abs(p.getZ() - minZ), Math.abs(maxZ - p.getZ()));
            int borderDist = Math.min(dx, dz);
            if (borderDist > borderTh) continue;

            if (existing != null && hasCrossCellEdgeFor(existing, p, minX, minZ, maxXExcl, maxZExcl)) {
                continue;
            }

            BlockPos anchor = null;
            if (highwayPoints != null && !highwayPoints.isEmpty()) {
                anchor = findNearestLinear(p, highwayPoints);
            }
            if (anchor == null && !mids.isEmpty()) {
                anchor = findNearestLinear(p, mids);
            }
            if (anchor == null) continue;
            if (PlanningUtils.pos2dKey(p) == PlanningUtils.pos2dKey(anchor)) continue;

            long d2 = dist2XZ(p, anchor);
            if (d2 > maxEntryDist2) continue;

            long ek = PlanningUtils.edgeKey(p, anchor);
            if (!existingEdgeKeys.add(ek)) continue;

            out.add(new StructureConnection(p, anchor, ConnectionStatus.PLANNED));
        }

        return out;
    }

    private static boolean hasCrossCellEdgeFor(List<StructureConnection> existing,
                                               BlockPos p,
                                               int minX, int minZ,
                                               int maxXExcl, int maxZExcl) {
        if (existing == null || existing.isEmpty() || p == null) return false;
        for (StructureConnection c0 : existing) {
            if (c0 == null) continue;
            if (!p.equals(c0.from()) && !p.equals(c0.to())) continue;
            boolean inA = isInCell(c0.from(), minX, minZ, maxXExcl, maxZExcl);
            boolean inB = isInCell(c0.to(), minX, minZ, maxXExcl, maxZExcl);
            if (inA ^ inB) return true;
        }
        return false;
    }

    private static List<BlockPos> collectStructurePointsInCell(ServerLevel level,
                                                               int minX, int minZ,
                                                               int maxXExcl, int maxZExcl) {
        ArrayList<BlockPos> out = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();

        ModConfig cfg = ConfigService.get();
        boolean allowPredicted = cfg != null
                && cfg.structurePrediction().enabled()
                && cfg.structurePrediction().isEnabledForDimension(level.dimension().location().toString());
        if (allowPredicted) {
            StructureIndexService.predictAndVerifyInRect(level, minX, minZ, maxXExcl - 1, maxZExcl - 1);
        }

        int[] sources = allowPredicted
                ? new int[]{StructureSqliteStorage.SOURCE_MANUAL, StructureSqliteStorage.SOURCE_PREDICTED}
                : new int[]{StructureSqliteStorage.SOURCE_MANUAL};
        List<StructureInfo> cached = StructureSqliteStorage.queryRect(level, minX, minZ, maxXExcl - 1, maxZExcl - 1, sources);
        if (cached != null && !cached.isEmpty()) {
            for (StructureInfo info : cached) {
                if (info == null || info.pos() == null) continue;
                BlockPos p = info.pos();
                int x = p.getX(), z = p.getZ();
                if (x < minX || x >= maxXExcl || z < minZ || z >= maxZExcl) continue;
                BlockPos q = new BlockPos(x, 0, z);
                long k = PlanningUtils.pos2dKey(q);
                if (seen.add(k)) out.add(q);
            }
        }

        return out;
    }

    private static List<StructureConnection> planStructureGraphLikeOriginal(ServerLevel level, List<BlockPos> points) {
        if (points == null || points.size() < 2) return List.of();

        ModConfig cfg0 = ConfigService.get();

        List<StructureConnection> primaryEdges = NetworkPlannerFactory
                .create(cfg0.planning().planningAlgorithm())
                .plan(points, RoadConstants.DEFAULT_PLAN_MAX_EDGE_LEN_BLOCKS);

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> existing = provider.getStructureConnections(level);

        HashSet<BlockPos> inRect = new HashSet<>(points);
        ArrayList<StructureConnection> existingInRect = new ArrayList<>();
        HashSet<Long> existingEdgeKeys = new HashSet<>();
        if (existing != null) {
            for (StructureConnection c : existing) {
                if (c == null) continue;
                if (inRect.contains(c.from()) && inRect.contains(c.to())) {
                    existingInRect.add(c);
                    existingEdgeKeys.add(PlanningUtils.edgeKey(c.from(), c.to()));
                }
            }
        }

        ArrayList<StructureConnection> filteredPrimary = new ArrayList<>();
        for (StructureConnection c : primaryEdges) {
            if (c == null) continue;
            long ek = PlanningUtils.edgeKey(c.from(), c.to());
            if (!existingEdgeKeys.contains(ek)) {
                filteredPrimary.add(c);
            }
        }

        ArrayList<StructureConnection> base = new ArrayList<>(existingInRect);
        base.addAll(filteredPrimary);

        List<StructureConnection> bridges = KNNPlanner.connectComponents(points, base,
                RoadConstants.DEFAULT_BRIDGE_JOIN_LEN_BLOCKS,
                RoadConstants.DEFAULT_COMPONENT_MIN_ANGLE_DEG,
                RoadConstants.DEFAULT_COMPONENT_DEGREE_CAP);

        ArrayList<StructureConnection> incoming = new ArrayList<>(filteredPrimary);
        if (bridges != null && !bridges.isEmpty()) {
            incoming.addAll(bridges);
        }

        return incoming;
    }

    private static StructureConnection buildSingleEntryEdge(ServerLevel level,
                                                            ModConfig cfg,
                                                            List<BlockPos> points,
                                                            int minX, int minZ,
                                                            int maxXExcl, int maxZExcl,
                                                            BlockPos a, BlockPos b, BlockPos c, BlockPos d,
                                                            ConnectionStatus ab,
                                                            ConnectionStatus ac,
                                                            ConnectionStatus bd,
                                                            ConnectionStatus cd) {
        if (points == null || points.isEmpty()) return null;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> existing = provider.getStructureConnections(level);
        if (existing != null && !existing.isEmpty()) {
            for (StructureConnection c0 : existing) {
                if (c0 == null) continue;
                boolean inA = isInCell(c0.from(), minX, minZ, maxXExcl, maxZExcl);
                boolean inB = isInCell(c0.to(), minX, minZ, maxXExcl, maxZExcl);
                if (inA ^ inB) {
                    return null;
                }
            }
        }

        BlockPos root = null;
        int best = Integer.MAX_VALUE;
        int maxX = maxXExcl - 1;
        int maxZ = maxZExcl - 1;
        for (BlockPos p : points) {
            int dx = Math.min(Math.abs(p.getX() - minX), Math.abs(maxX - p.getX()));
            int dz = Math.min(Math.abs(p.getZ() - minZ), Math.abs(maxZ - p.getZ()));
            int d2 = Math.min(dx, dz);
            if (d2 < best) {
                best = d2;
                root = p;
            }
        }
        if (root == null) return null;

        int band = Math.max(64, Math.min(512, Math.max(1, cfg.highway().roadWidth()) * 16));
        List<BlockPos> highwayPoints = collectHighwayPointsNearCellBorder(level, minX, minZ, maxXExcl, maxZExcl, band);

        BlockPos anchor = null;
        if (!highwayPoints.isEmpty()) {
            anchor = findNearestLinear(root, highwayPoints);
        }

        if (anchor == null) {
            ArrayList<BlockPos> mids = new ArrayList<>();
            if (ab == ConnectionStatus.COMPLETED) mids.add(midpoint(a, b));
            if (ac == ConnectionStatus.COMPLETED) mids.add(midpoint(a, c));
            if (bd == ConnectionStatus.COMPLETED) mids.add(midpoint(b, d));
            if (cd == ConnectionStatus.COMPLETED) mids.add(midpoint(c, d));
            if (!mids.isEmpty()) {
                anchor = findNearestLinear(root, mids);
            }
        }

        if (anchor == null) return null;
        if (PlanningUtils.pos2dKey(root) == PlanningUtils.pos2dKey(anchor)) return null;

        long ek = PlanningUtils.edgeKey(root, anchor);
        if (existing != null) {
            for (StructureConnection c0 : existing) {
                if (c0 == null) continue;
                if (PlanningUtils.edgeKey(c0.from(), c0.to()) == ek) return null;
            }
        }

        return new StructureConnection(root, anchor, ConnectionStatus.PLANNED);
    }

    private static List<BlockPos> collectHighwayPointsNearCellBorder(ServerLevel level,
                                                                     int minX, int minZ,
                                                                     int maxXExcl, int maxZExcl,
                                                                     int band) {
        int maxX = maxXExcl - 1;
        int maxZ = maxZExcl - 1;

        List<RoadData> roads = RoadShardStorage.queryRect(level,
                minX - band, minZ - band,
                maxX + band, maxZ + band);
        if (roads == null || roads.isEmpty()) return List.of();

        ArrayList<BlockPos> out = new ArrayList<>();
        for (RoadData rd : roads) {
            if (rd == null) continue;
            if (rd.roadType() != 2) continue;
            if (rd.roadSegmentList() == null) continue;

            for (RoadSegmentPlacement seg : rd.roadSegmentList()) {
                if (seg == null) continue;
                BlockPos p = seg.middlePos();
                if (p == null) continue;
                int x = p.getX();
                int z = p.getZ();

                boolean near = (Math.abs(x - minX) <= band) || (Math.abs(x - maxX) <= band)
                        || (Math.abs(z - minZ) <= band) || (Math.abs(z - maxZ) <= band);
                if (!near) continue;

                out.add(new BlockPos(x, 0, z));
            }
        }
        return out;
    }

    private static BlockPos findNearestLinear(BlockPos target, List<BlockPos> candidates) {
        BlockPos best = null;
        long bestD2 = Long.MAX_VALUE;
        for (BlockPos p : candidates) {
            if (p == null) continue;
            long d2 = dist2XZ(target, p);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = p;
            }
        }
        return best;
    }

    private static BlockPos midpoint(BlockPos a, BlockPos b) {
        return new BlockPos((a.getX() + b.getX()) / 2, 0, (a.getZ() + b.getZ()) / 2);
    }

    private static boolean isInCell(BlockPos p, int minX, int minZ, int maxXExcl, int maxZExcl) {
        if (p == null) return false;
        int x = p.getX();
        int z = p.getZ();
        return x >= minX && x < maxXExcl && z >= minZ && z < maxZExcl;
    }

    private static boolean isTerminal(ConnectionStatus st) {
        if (st == null) return false;
        return st == ConnectionStatus.COMPLETED || st == ConnectionStatus.FAILED;
    }

    private static long dist2XZ(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    private static List<StructureConnection> mergeConnections(List<StructureConnection> existing,
                                                              List<StructureConnection> incoming) {
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
            if (seen.add(k)) out.add(new StructureConnection(c.from(), c.to(), ConnectionStatus.PLANNED));
        }

        return out;
    }
}
