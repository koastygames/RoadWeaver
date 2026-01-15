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
 * Highway 网格单元格（四边）驱动的 Path 规划服务。
 *
 * 职责（SRP）：
 * - 监听 Highway 边进入终态（COMPLETED/FAILED）
 * - 当某个网格单元格的四条边都进入终态后：
 *   1) 在该单元格内部，使用原有 RoadPlanningService 相同的结构点路网算法进行规划
 *   2) 仅额外添加 1 条“入口边”：把离公路最近的结构点连接到该单元格边界上最近的 Highway 道路点
 *
 * 注意：这里不改变结构点之间的规划算法，只改变触发时机与“接入公路”的方式。
 */
public final class HighwayCellPathPlanningService {
    private HighwayCellPathPlanningService() {
    }

    private static final ConcurrentHashMap<Level, Set<Long>> PLANNED_CELLS = new ConcurrentHashMap<>();

    // 认为“贴边”的阈值会随网格大小缩放（避免网格很大时过于苛刻）
    private static final int BORDER_ENTRY_MIN_BLOCKS = 48;
    private static final int BORDER_ENTRY_MAX_BLOCKS = 256;

    // 回补预算：每 tick 最多检查/触发多少个单元格
    private static final int BACKFILL_BUDGET_PER_TICK = 2;

    public static void resetAll() {
        PLANNED_CELLS.clear();
    }

    /**
     * 裁剪本服务内部的“已规划 cell”标记。
     *
     * 原理：3x3 滚动窗口会不断移动，如果不裁剪，PLANNED_CELLS 会无限增长，
     * 并且窗口外的 cell 永远无法再次触发（即使你重新进入该区域）。
     *
     * 这里仅维护本服务内部状态（SRP），不修改任何世界持久化数据。
     */
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

    /**
     * tick 用于存档重进/服务器重启时的回补。
     *
     * 原理：扫描少量已终态的 highway 边，尝试触发其相邻单元格。
     */
    public static void tick(ServerLevel level) {
        if (level == null) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;

        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.highwayEnabled()) return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> highways = provider.getHighwayConnections(level);
        if (highways == null || highways.isEmpty()) return;

        int budget = BACKFILL_BUDGET_PER_TICK;
        for (Records.StructureConnection c : highways) {
            if (budget <= 0) break;
            if (c == null) continue;
            if (!isTerminal(c.status())) continue;

            onHighwayEdgeFinalized(level, c);
            budget--;
        }
    }

    /**
     * 当某条 Highway 边进入终态（成功或失败）后调用。
     *
     * 注意：调用方应确保该状态已写入 WorldDataProvider（highwayConnections）。
     */
    public static void onHighwayEdgeFinalized(ServerLevel level, Records.StructureConnection highwayEdge) {
        if (level == null || highwayEdge == null) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;

        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.highwayEnabled()) return;

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());

        // 一条边最多影响两个相邻单元格：左/右 或 上/下
        int x0 = highwayEdge.from().getX();
        int z0 = highwayEdge.from().getZ();
        int x1 = highwayEdge.to().getX();
        int z1 = highwayEdge.to().getZ();

        if (z0 == z1) {
            // 水平边：上下单元格
            int gz = floorDiv(z0, gridBlocks);
            int gx = Math.min(floorDiv(x0, gridBlocks), floorDiv(x1, gridBlocks));
            maybePlanCell(level, cfg, gx, gz - 1);
            maybePlanCell(level, cfg, gx, gz);
        } else if (x0 == x1) {
            // 垂直边：左右单元格
            int gx = floorDiv(x0, gridBlocks);
            int gz = Math.min(floorDiv(z0, gridBlocks), floorDiv(z1, gridBlocks));
            maybePlanCell(level, cfg, gx - 1, gz);
            maybePlanCell(level, cfg, gx, gz);
        } else {
            // 理论上不会出现（HighwayPlanningService 生成的边是轴对齐的）
        }
    }

    /**
     * 在指定矩形范围内，批量尝试触发已完成的单元格（用于初始生成阶段）。
     */
    public static void planCompletedCellsInRect(ServerLevel level,
                                                int minBlockX, int minBlockZ,
                                                int maxBlockX, int maxBlockZ) {
        if (level == null) return;
        if (!Level.OVERWORLD.equals(level.dimension())) return;

        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.highwayEnabled()) return;

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());

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

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());

        // 单元格四角
        BlockPos a = new BlockPos(cellGx * gridBlocks, 0, cellGz * gridBlocks);
        BlockPos b = new BlockPos((cellGx + 1) * gridBlocks, 0, cellGz * gridBlocks);
        BlockPos c = new BlockPos(cellGx * gridBlocks, 0, (cellGz + 1) * gridBlocks);
        BlockPos d = new BlockPos((cellGx + 1) * gridBlocks, 0, (cellGz + 1) * gridBlocks);

        // 读取 highwayConnections 状态，判断四边是否都进入终态
        Map<Long, Records.ConnectionStatus> statusMap = buildHighwayStatusMap(level);
        Records.ConnectionStatus ab = statusMap.get(PlanningUtils.edgeKey(a, b));
        Records.ConnectionStatus ac = statusMap.get(PlanningUtils.edgeKey(a, c));
        Records.ConnectionStatus bd = statusMap.get(PlanningUtils.edgeKey(b, d));
        Records.ConnectionStatus cd = statusMap.get(PlanningUtils.edgeKey(c, d));

        if (!isTerminal(ab) || !isTerminal(ac) || !isTerminal(bd) || !isTerminal(cd)) {
            return;
        }

        // 标记为已规划（防止重复触发）
        planned.add(cellKey);

        planCell(level, cfg, cellGx, cellGz, a, b, c, d, ab, ac, bd, cd);
    }

    private static Map<Long, Records.ConnectionStatus> buildHighwayStatusMap(ServerLevel level) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> list = provider.getHighwayConnections(level);

        HashMap<Long, Records.ConnectionStatus> map = new HashMap<>();
        if (list == null || list.isEmpty()) return map;

        for (Records.StructureConnection c : list) {
            if (c == null) continue;
            map.put(PlanningUtils.edgeKey(c.from(), c.to()), c.status());
        }
        return map;
    }

    private static void planCell(ServerLevel level,
                                 ModConfig cfg,
                                 int cellGx, int cellGz,
                                 BlockPos a, BlockPos b, BlockPos c, BlockPos d,
                                 Records.ConnectionStatus ab,
                                 Records.ConnectionStatus ac,
                                 Records.ConnectionStatus bd,
                                 Records.ConnectionStatus cd) {
        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());

        // 半开区间：避免边界结构点被两个格子重复纳入
        int minX = cellGx * gridBlocks;
        int maxXExcl = (cellGx + 1) * gridBlocks;
        int minZ = cellGz * gridBlocks;
        int maxZExcl = (cellGz + 1) * gridBlocks;

        // 收集结构点（只在该单元格内）
        List<BlockPos> points = collectStructurePointsInCell(level, minX, minZ, maxXExcl, maxZExcl);
        if (points.isEmpty()) return;

        // 生成格内结构路网（复用原算法：Delaunay/RNG/KNN + connectComponents）
        ArrayList<Records.StructureConnection> incoming = new ArrayList<>();
        if (points.size() >= 2) {
            incoming.addAll(planStructureGraphLikeOriginal(level, points));
        }

        // 入口策略：
        // 1) 优先让“贴边结构点”各自短连到最近 Highway（避免从格内深处拉一条长线直连公路）
        // 2) 如果该格子没有任何可接入的贴边结构点，再回退到单入口边
        List<Records.StructureConnection> borderEntries = buildBorderEntryEdges(level, cfg, points,
                minX, minZ, maxXExcl, maxZExcl,
                a, b, c, d,
                ab, ac, bd, cd);
        if (borderEntries != null && !borderEntries.isEmpty()) {
            incoming.addAll(borderEntries);
        } else {
            Records.StructureConnection entry = buildSingleEntryEdge(level, cfg, points,
                    minX, minZ, maxXExcl, maxZExcl,
                    a, b, c, d,
                    ab, ac, bd, cd);
            if (entry != null) {
                incoming.add(entry);
            }
        }

        if (incoming.isEmpty()) return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> existing = provider.getStructureConnections(level);
        List<Records.StructureConnection> merged = mergeConnections(existing, incoming);
        if (merged.size() != (existing == null ? 0 : existing.size())) {
            provider.setStructureConnections(level, merged);
        }
    }

    private static List<Records.StructureConnection> buildBorderEntryEdges(ServerLevel level,
                                                                           ModConfig cfg,
                                                                           List<BlockPos> points,
                                                                           int minX, int minZ,
                                                                           int maxXExcl, int maxZExcl,
                                                                           BlockPos a, BlockPos b, BlockPos c, BlockPos d,
                                                                           Records.ConnectionStatus ab,
                                                                           Records.ConnectionStatus ac,
                                                                           Records.ConnectionStatus bd,
                                                                           Records.ConnectionStatus cd) {
        if (points == null || points.isEmpty()) return List.of();

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        int borderTh = Math.max(BORDER_ENTRY_MIN_BLOCKS, Math.min(BORDER_ENTRY_MAX_BLOCKS, gridBlocks / 8));
        int maxEntryDist = Math.max(128, Math.min(1024, gridBlocks / 2));
        long maxEntryDist2 = (long) maxEntryDist * (long) maxEntryDist;

        int maxX = maxXExcl - 1;
        int maxZ = maxZExcl - 1;

        // 候选 Highway 锚点：尽量取真实已生成公路道路点
        int band = Math.max(64, Math.min(512, Math.max(1, cfg.highwayRoadWidth()) * 16));
        List<BlockPos> highwayPoints = collectHighwayPointsNearCellBorder(level, minX, minZ, maxXExcl, maxZExcl, band);

        ArrayList<BlockPos> mids = new ArrayList<>();
        if (ab == Records.ConnectionStatus.COMPLETED) mids.add(midpoint(a, b));
        if (ac == Records.ConnectionStatus.COMPLETED) mids.add(midpoint(a, c));
        if (bd == Records.ConnectionStatus.COMPLETED) mids.add(midpoint(b, d));
        if (cd == Records.ConnectionStatus.COMPLETED) mids.add(midpoint(c, d));

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> existing = provider.getStructureConnections(level);

        ArrayList<Records.StructureConnection> out = new ArrayList<>();
        HashSet<Long> existingEdgeKeys = new HashSet<>();
        if (existing != null) {
            for (Records.StructureConnection c0 : existing) {
                if (c0 == null) continue;
                existingEdgeKeys.add(PlanningUtils.edgeKey(c0.from(), c0.to()));
            }
        }

        for (BlockPos p : points) {
            if (p == null) continue;

            // 仅处理“贴边结构点”
            int dx = Math.min(Math.abs(p.getX() - minX), Math.abs(maxX - p.getX()));
            int dz = Math.min(Math.abs(p.getZ() - minZ), Math.abs(maxZ - p.getZ()));
            int borderDist = Math.min(dx, dz);
            if (borderDist > borderTh) continue;

            // 如果该点已经有一条跨出单元格边界的连接，则认为已接入外部网络，跳过
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

            out.add(new Records.StructureConnection(p, anchor, Records.ConnectionStatus.PLANNED));
        }

        return out;
    }

    private static boolean hasCrossCellEdgeFor(List<Records.StructureConnection> existing,
                                               BlockPos p,
                                               int minX, int minZ,
                                               int maxXExcl, int maxZExcl) {
        if (existing == null || existing.isEmpty() || p == null) return false;
        for (Records.StructureConnection c0 : existing) {
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

        // 迁移 legacy 并按需触发预测扫描：结构点统一走 SQLite
        StructureCacheMigrator.migrateLegacyIfNeeded(level);
        ModConfig cfg = ConfigService.get();
        boolean allowPredicted = cfg != null
                && cfg.structurePredictionEnabled()
                && cfg.isStructurePredictionEnabledForDimension(level.dimension().location().toString());
        if (allowPredicted) {
            // 注意：predictAndVerifyInRect 是闭区间，因此 max-1
            StructureIndexService.predictAndVerifyInRect(level, minX, minZ, maxXExcl - 1, maxZExcl - 1);
        }

        int[] sources = allowPredicted
                ? new int[]{StructureSqliteStorage.SOURCE_MANUAL, StructureSqliteStorage.SOURCE_LEGACY, StructureSqliteStorage.SOURCE_PREDICTED}
                : new int[]{StructureSqliteStorage.SOURCE_MANUAL, StructureSqliteStorage.SOURCE_LEGACY};
        List<Records.StructureInfo> cached = StructureSqliteStorage.queryRect(level, minX, minZ, maxXExcl - 1, maxZExcl - 1, sources);
        if (cached != null && !cached.isEmpty()) {
            for (Records.StructureInfo info : cached) {
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

    private static List<Records.StructureConnection> planStructureGraphLikeOriginal(ServerLevel level, List<BlockPos> points) {
        if (points == null || points.size() < 2) return List.of();

        ModConfig cfg0 = ConfigService.get();

        List<Records.StructureConnection> primaryEdges;
        if (cfg0.planningAlgorithm() == ModConfig.PlanningAlgorithm.DELAUNAY) {
            primaryEdges = DelaunayPlanner.planDelaunay(points, 2048);
        } else if (cfg0.planningAlgorithm() == ModConfig.PlanningAlgorithm.RNG) {
            primaryEdges = RNGPlanner.planRNG(points, 2048);
        } else {
            primaryEdges = KNNPlanner.planKNN(points, 2, 2048, 1.8, 40.0, 2);
        }

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> existing = provider.getStructureConnections(level);

        HashSet<BlockPos> inRect = new HashSet<>(points);
        ArrayList<Records.StructureConnection> existingInRect = new ArrayList<>();
        HashSet<Long> existingEdgeKeys = new HashSet<>();
        if (existing != null) {
            for (Records.StructureConnection c : existing) {
                if (c == null) continue;
                if (inRect.contains(c.from()) && inRect.contains(c.to())) {
                    existingInRect.add(c);
                    existingEdgeKeys.add(PlanningUtils.edgeKey(c.from(), c.to()));
                }
            }
        }

        // 过滤掉与已有边冲突的新边（保持规划一致性）
        ArrayList<Records.StructureConnection> filteredPrimary = new ArrayList<>();
        for (Records.StructureConnection c : primaryEdges) {
            if (c == null) continue;
            long ek = PlanningUtils.edgeKey(c.from(), c.to());
            if (!existingEdgeKeys.contains(ek)) {
                filteredPrimary.add(c);
            }
        }

        // 将已有边和新边合并作为 base 进行连通性修复
        ArrayList<Records.StructureConnection> base = new ArrayList<>(existingInRect);
        base.addAll(filteredPrimary);

        List<Records.StructureConnection> bridges = KNNPlanner.connectComponents(points, base, 1536, 35.0, 3);

        ArrayList<Records.StructureConnection> incoming = new ArrayList<>(filteredPrimary);
        if (bridges != null && !bridges.isEmpty()) {
            incoming.addAll(bridges);
        }

        return incoming;
    }

    private static Records.StructureConnection buildSingleEntryEdge(ServerLevel level,
                                                                    ModConfig cfg,
                                                                    List<BlockPos> points,
                                                                    int minX, int minZ,
                                                                    int maxXExcl, int maxZExcl,
                                                                    BlockPos a, BlockPos b, BlockPos c, BlockPos d,
                                                                    Records.ConnectionStatus ab,
                                                                    Records.ConnectionStatus ac,
                                                                    Records.ConnectionStatus bd,
                                                                    Records.ConnectionStatus cd) {
        if (points == null || points.isEmpty()) return null;

        // 防止“服务器重启/回补”导致同一单元格重复添加入口边：
        // 若单元格内任意点已经存在一条跨出单元格边界的连接，视为该单元格已接入外部网络。
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> existing = provider.getStructureConnections(level);
        if (existing != null && !existing.isEmpty()) {
            for (Records.StructureConnection c0 : existing) {
                if (c0 == null) continue;
                boolean inA = isInCell(c0.from(), minX, minZ, maxXExcl, maxZExcl);
                boolean inB = isInCell(c0.to(), minX, minZ, maxXExcl, maxZExcl);
                if (inA ^ inB) {
                    return null;
                }
            }
        }

        // 选一个“离公路最近”的结构点作为入口（越贴近单元格边界越优先）
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

        // 尝试从已生成的 Highway 道路数据中取“边界附近”的锚点
        int band = Math.max(64, Math.min(512, Math.max(1, cfg.highwayRoadWidth()) * 16));
        List<BlockPos> highwayPoints = collectHighwayPointsNearCellBorder(level, minX, minZ, maxXExcl, maxZExcl, band);

        BlockPos anchor = null;
        if (!highwayPoints.isEmpty()) {
            anchor = findNearestLinear(root, highwayPoints);
        }

        // 如果没找到真实道路点，则回退到“已成功的边”的中点（至少保证连接到一个成功的 Highway 边）
        if (anchor == null) {
            ArrayList<BlockPos> mids = new ArrayList<>();
            if (ab == Records.ConnectionStatus.COMPLETED) mids.add(midpoint(a, b));
            if (ac == Records.ConnectionStatus.COMPLETED) mids.add(midpoint(a, c));
            if (bd == Records.ConnectionStatus.COMPLETED) mids.add(midpoint(b, d));
            if (cd == Records.ConnectionStatus.COMPLETED) mids.add(midpoint(c, d));
            if (!mids.isEmpty()) {
                anchor = findNearestLinear(root, mids);
            }
        }

        if (anchor == null) return null;
        if (PlanningUtils.pos2dKey(root) == PlanningUtils.pos2dKey(anchor)) return null;

        long ek = PlanningUtils.edgeKey(root, anchor);
        if (existing != null) {
            for (Records.StructureConnection c0 : existing) {
                if (c0 == null) continue;
                if (PlanningUtils.edgeKey(c0.from(), c0.to()) == ek) return null;
            }
        }

        return new Records.StructureConnection(root, anchor, Records.ConnectionStatus.PLANNED);
    }

    private static List<BlockPos> collectHighwayPointsNearCellBorder(ServerLevel level,
                                                                    int minX, int minZ,
                                                                    int maxXExcl, int maxZExcl,
                                                                    int band) {
        int maxX = maxXExcl - 1;
        int maxZ = maxZExcl - 1;

        List<Records.RoadData> roads = RoadShardStorage.queryRect(level,
                minX - band, minZ - band,
                maxX + band, maxZ + band);
        if (roads == null || roads.isEmpty()) return List.of();

        ArrayList<BlockPos> out = new ArrayList<>();
        for (Records.RoadData rd : roads) {
            if (rd == null) continue;
            if (rd.roadType() != 2) continue;
            if (rd.roadSegmentList() == null) continue;

            for (Records.RoadSegmentPlacement seg : rd.roadSegmentList()) {
                if (seg == null) continue;
                BlockPos p = seg.middlePos();
                if (p == null) continue;
                int x = p.getX();
                int z = p.getZ();

                // 只取靠近边界的点（允许一定偏移，因为 A* 可能绕行）
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

    private static boolean isTerminal(Records.ConnectionStatus st) {
        if (st == null) return false;
        return st == Records.ConnectionStatus.COMPLETED || st == Records.ConnectionStatus.FAILED;
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
            if (seen.add(k)) out.add(new Records.StructureConnection(c.from(), c.to(), Records.ConnectionStatus.PLANNED));
        }

        return out;
    }
}
