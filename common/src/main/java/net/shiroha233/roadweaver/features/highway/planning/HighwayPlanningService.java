package net.shiroha233.roadweaver.features.highway.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.highway.terrain.HighwayCellTerrainField;
import net.shiroha233.roadweaver.features.highway.terrain.HighwayTerrainSamplingService;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.HighwayCellPathPlanningService;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.planning.impl.KNNPlanner;
import net.shiroha233.roadweaver.planning.impl.RNGPlanner;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.util.ComputeService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highway 规划服务
 */
public final class HighwayPlanningService {
    private HighwayPlanningService() {}

    private static final class WindowCenter {
        private int gx;
        private int gz;
        private boolean dynamicActivated;

        private WindowCenter(int gx, int gz, boolean dynamicActivated) {
            this.gx = gx;
            this.gz = gz;
            this.dynamicActivated = dynamicActivated;
        }
    }

    private static final ConcurrentHashMap<Level, WindowCenter> WINDOW_CENTERS = new ConcurrentHashMap<>();

    public static void resetAll() {
        WINDOW_CENTERS.clear();
        HighwayTerrainSamplingService.resetAll();
    }

    public static void initialPlan(ServerLevel level) {
        if (level == null) return;
        ModConfig cfg = ConfigService.get();
        String dimId = level.dimension().location().toString();
        if (!cfg.highwayEnabledForDimension(dimId) || !cfg.highwayAutoPlanEnabled()) return;

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        BlockPos centerPos = level.getSharedSpawnPos();
        var server = level.getServer();
        if (server != null) {
            var p = server.getPlayerList().getPlayers().stream()
                    .filter(sp -> sp != null && sp.serverLevel() == level)
                    .findFirst()
                    .orElse(null);
            if (p != null) {
                centerPos = p.blockPosition();
            }
        }

        int cellGx = floorDiv(centerPos.getX(), gridBlocks);
        int cellGz = floorDiv(centerPos.getZ(), gridBlocks);
        WINDOW_CENTERS.put(level, new WindowCenter(cellGx, cellGz, false));

        if (cfg.highwayTerrainAwarePlanning()) {
            migrateToTerrainAware(level);
            refreshDynamicPlanSync(level, cfg, cellGx, cellGz);
        } else {
            refreshSingleCell(level, cfg, cellGx, cellGz);
        }
    }

    public static CompletableFuture<Void> initialPlanAsync(ServerLevel level) {
        if (level == null) return CompletableFuture.completedFuture(null);
        ModConfig cfg = ConfigService.get();
        String dimId = level.dimension().location().toString();
        if (!cfg.highwayEnabledForDimension(dimId) || !cfg.highwayAutoPlanEnabled())
            return CompletableFuture.completedFuture(null);

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        BlockPos centerPos = level.getSharedSpawnPos();
        var server = level.getServer();
        if (server != null) {
            var p = server.getPlayerList().getPlayers().stream()
                    .filter(sp -> sp != null && sp.serverLevel() == level)
                    .findFirst()
                    .orElse(null);
            if (p != null) {
                centerPos = p.blockPosition();
            }
        }

        int cellGx = floorDiv(centerPos.getX(), gridBlocks);
        int cellGz = floorDiv(centerPos.getZ(), gridBlocks);
        WINDOW_CENTERS.put(level, new WindowCenter(cellGx, cellGz, false));

        if (cfg.highwayTerrainAwarePlanning()) {
            migrateToTerrainAware(level);
            return refreshDynamicPlanAsync(level, cfg, cellGx, cellGz);
        } else {
            return refreshSingleCellAsync(level, cfg, cellGx, cellGz);
        }
    }

    public static void planAroundPlayer(ServerPlayer player) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();

        ModConfig cfg = ConfigService.get();
        String dimId = level.dimension().location().toString();
        if (!cfg.highwayEnabledForDimension(dimId) || !cfg.highwayAutoPlanEnabled()) return;

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        int playerCellGx = floorDiv(player.getBlockX(), gridBlocks);
        int playerCellGz = floorDiv(player.getBlockZ(), gridBlocks);

        WindowCenter center = WINDOW_CENTERS.get(level);
        if (center == null) {
            center = new WindowCenter(playerCellGx, playerCellGz, cfg.highwayDynamicPlanEnabled());
            WINDOW_CENTERS.put(level, center);
            if (cfg.highwayTerrainAwarePlanning()) {
                refreshDynamicPlanAsync(level, cfg, playerCellGx, playerCellGz);
            } else if (cfg.highwayDynamicPlanEnabled()) {
                refreshWindowAsync(level, cfg, playerCellGx, playerCellGz);
            } else {
                refreshSingleCellAsync(level, cfg, playerCellGx, playerCellGz);
            }
            return;
        }

        if (playerCellGx == center.gx && playerCellGz == center.gz) return;

        center.gx = playerCellGx;
        center.gz = playerCellGz;

        if (cfg.highwayTerrainAwarePlanning()) {
            refreshDynamicPlanAsync(level, cfg, playerCellGx, playerCellGz);
        } else if (cfg.highwayDynamicPlanEnabled()) {
            refreshWindowAsync(level, cfg, playerCellGx, playerCellGz);
        } else {
            refreshSingleCellAsync(level, cfg, playerCellGx, playerCellGz);
        }
    }

    // ==================== 地形感知动态规划 ====================

    private static void refreshDynamicPlanSync(ServerLevel level, ModConfig cfg, int centerGx, int centerGz) {
        if (level == null || cfg == null) return;
        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());

        var cache = new net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache();
        Map<Long, HighwayCellTerrainField> terrainMap = HighwayTerrainSamplingService.sampleNineGrid(
                level, cache, centerGx, centerGz, gridBlocks);

        Map<Long, Long> intersections = selectIntersections(level, terrainMap, cfg, gridBlocks);
        List<StructureConnection> connections = buildDynamicConnections(intersections, gridBlocks);
        applyDynamicPlan(level, intersections, connections, centerGx, centerGz, gridBlocks);
    }

    private static CompletableFuture<Void> refreshDynamicPlanAsync(ServerLevel level, ModConfig cfg, int centerGx, int centerGz) {
        if (level == null || cfg == null) return CompletableFuture.completedFuture(null);
        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        final long epoch = ThreadPoolManager.currentEpoch();
        final ModConfig cfgSnap = cfg;

        var cache = new net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache();

        return HighwayTerrainSamplingService.sampleNineGridAsync(level, cache, centerGx, centerGz, gridBlocks)
                .thenApply(terrainMap -> {
                    if (!ThreadPoolManager.isEpoch(epoch)) return null;
                    Map<Long, Long> intersections = selectIntersections(level, terrainMap, cfgSnap, gridBlocks);
                    List<StructureConnection> connections = buildDynamicConnections(intersections, gridBlocks);
                    return new PlanResult(intersections, connections);
                })
                .thenAccept(result -> {
                    if (result == null) return;
                    if (!ThreadPoolManager.isEpoch(epoch)) return;
                    var server = level.getServer();
                    if (server == null) return;
                    server.execute(() -> {
                        if (!ThreadPoolManager.isEpoch(epoch)) return;
                        applyDynamicPlan(level, result.intersections, result.connections, centerGx, centerGz, gridBlocks);
                    });
                });
    }

    private static Map<Long, Long> selectIntersections(ServerLevel level,
                                                       Map<Long, HighwayCellTerrainField> terrainMap,
                                                       ModConfig cfg,
                                                       int gridBlocks) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        Map<Long, Long> existing = provider.getHighwayIntersections(level);
        Map<Long, Long> result = new HashMap<>(existing != null ? existing : Map.of());

        int windowSize = cfg.highwayIntersectionWindowSize();
        double edgeMargin = cfg.highwayIntersectionEdgeMargin();

        for (Map.Entry<Long, HighwayCellTerrainField> entry : terrainMap.entrySet()) {
            long cellKey = entry.getKey();
            if (result.containsKey(cellKey)) continue;

            HighwayCellTerrainField terrain = entry.getValue();
            int gx = HighwayTerrainSamplingService.cellKeyGx(cellKey);
            int gz = HighwayTerrainSamplingService.cellKeyGz(cellKey);
            int cellMinX = gx * gridBlocks;
            int cellMinZ = gz * gridBlocks;

            long pos = IntersectionSelector.selectIntersection(terrain, cellMinX, cellMinZ, gridBlocks, windowSize, edgeMargin);
            result.put(cellKey, pos);
        }

        return result;
    }

    private static List<StructureConnection> buildDynamicConnections(Map<Long, Long> intersections, int gridBlocks) {
        return buildBackboneConnections(collectIntersectionPoints(intersections), gridBlocks);
    }

    private static void applyDynamicPlan(ServerLevel level,
                                         Map<Long, Long> intersections,
                                         List<StructureConnection> newConnections,
                                         int centerGx, int centerGz,
                                         int gridBlocks) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        provider.setHighwayIntersections(level, intersections);

        List<StructureConnection> existing = provider.getHighwayConnections(level);
        List<StructureConnection> merged = mergeConnections(existing, newConnections);
        if (merged.size() != (existing == null ? 0 : existing.size())) {
            provider.setHighwayConnections(level, merged);
        }

        int minCellGx = centerGx - 1;
        int maxCellGx = centerGx + 1;
        int minCellGz = centerGz - 1;
        int maxCellGz = centerGz + 1;
        HighwayCellPathPlanningService.retainPlannedCellsInRect(level, minCellGx, minCellGz, maxCellGx, maxCellGz);

        int cellMinX = minCellGx * gridBlocks;
        int cellMaxX = (maxCellGx + 1) * gridBlocks;
        int cellMinZ = minCellGz * gridBlocks;
        int cellMaxZ = (maxCellGz + 1) * gridBlocks;
        HighwayCellPathPlanningService.planCompletedCellsInRect(level, cellMinX, cellMinZ, cellMaxX, cellMaxZ);
    }

    private static void migrateToTerrainAware(ServerLevel level) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> existing = provider.getHighwayConnections(level);
        if (existing == null || existing.isEmpty()) return;

        List<StructureConnection> retained = existing.stream()
                .filter(c -> c != null && c.status() != ConnectionStatus.PLANNED)
                .toList();
        if (retained.size() != existing.size()) {
            provider.setHighwayConnections(level, new ArrayList<>(retained));
        }
    }

    // ==================== 旧版固定网格规划（terrainAwarePlanning=false 时使用） ====================

    private static void refreshSingleCell(ServerLevel level, ModConfig cfg, int cellGx, int cellGz) {
        if (level == null || cfg == null) return;
        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());

        int minPointGx = cellGx;
        int maxPointGx = cellGx + 1;
        int minPointGz = cellGz;
        int maxPointGz = cellGz + 1;

        int minX = minPointGx * gridBlocks;
        int maxX = maxPointGx * gridBlocks;
        int minZ = minPointGz * gridBlocks;
        int maxZ = maxPointGz * gridBlocks;
        planRect(level, minX, minZ, maxX, maxZ);

        HighwayCellPathPlanningService.retainPlannedCellsInRect(level, cellGx, cellGz, cellGx, cellGz);
        int cellMinX = cellGx * gridBlocks;
        int cellMaxX = (cellGx + 1) * gridBlocks;
        int cellMinZ = cellGz * gridBlocks;
        int cellMaxZ = (cellGz + 1) * gridBlocks;
        HighwayCellPathPlanningService.planCompletedCellsInRect(level, cellMinX, cellMinZ, cellMaxX, cellMaxZ);
    }

    private static CompletableFuture<Void> refreshSingleCellAsync(ServerLevel level, ModConfig cfg, int cellGx, int cellGz) {
        if (level == null || cfg == null) return CompletableFuture.completedFuture(null);
        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());

        int minPointGx = cellGx;
        int maxPointGx = cellGx + 1;
        int minPointGz = cellGz;
        int maxPointGz = cellGz + 1;

        int minX = minPointGx * gridBlocks;
        int maxX = maxPointGx * gridBlocks;
        int minZ = minPointGz * gridBlocks;
        int maxZ = maxPointGz * gridBlocks;

        HighwayCellPathPlanningService.retainPlannedCellsInRect(level, cellGx, cellGz, cellGx, cellGz);

        int cellMinX = cellGx * gridBlocks;
        int cellMaxX = (cellGx + 1) * gridBlocks;
        int cellMinZ = cellGz * gridBlocks;
        int cellMaxZ = (cellGz + 1) * gridBlocks;

        return planRectAsync(level, minX, minZ, maxX, maxZ).thenRun(() -> {
            var server = level.getServer();
            if (server == null) return;
            server.execute(() -> HighwayCellPathPlanningService.planCompletedCellsInRect(level, cellMinX, cellMinZ, cellMaxX, cellMaxZ));
        });
    }

    private static CompletableFuture<Void> refreshWindowAsync(ServerLevel level, ModConfig cfg, int centerCellGx, int centerCellGz) {
        if (level == null || cfg == null) return CompletableFuture.completedFuture(null);

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        int minPointGx = centerCellGx - 1;
        int maxPointGx = centerCellGx + 2;
        int minPointGz = centerCellGz - 1;
        int maxPointGz = centerCellGz + 2;

        int minX = minPointGx * gridBlocks;
        int maxX = maxPointGx * gridBlocks;
        int minZ = minPointGz * gridBlocks;
        int maxZ = maxPointGz * gridBlocks;

        int minCellGx = centerCellGx - 1;
        int maxCellGx = centerCellGx + 1;
        int minCellGz = centerCellGz - 1;
        int maxCellGz = centerCellGz + 1;
        HighwayCellPathPlanningService.retainPlannedCellsInRect(level, minCellGx, minCellGz, maxCellGx, maxCellGz);

        int cellMinX = minCellGx * gridBlocks;
        int cellMaxX = (maxCellGx + 1) * gridBlocks;
        int cellMinZ = minCellGz * gridBlocks;
        int cellMaxZ = (maxCellGz + 1) * gridBlocks;

        return planRectAsync(level, minX, minZ, maxX, maxZ).thenRun(() -> {
            var server = level.getServer();
            if (server == null) return;
            server.execute(() -> HighwayCellPathPlanningService.planCompletedCellsInRect(level, cellMinX, cellMinZ, cellMaxX, cellMaxZ));
        });
    }

    private static void planRect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        int gridBlocks = Math.max(1, ConfigService.get().highwayGridBlocks());
        List<StructureConnection> planned = buildGridConnections(gridBlocks, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        if (planned.isEmpty()) return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> existing = provider.getHighwayConnections(level);
        List<StructureConnection> merged = mergeConnections(existing, planned);
        if (merged.size() != (existing == null ? 0 : existing.size())) {
            provider.setHighwayConnections(level, merged);
        }
    }

    public static CompletableFuture<Void> planRectAsync(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        final long epoch = ThreadPoolManager.currentEpoch();
        final ModConfig cfgSnap = ConfigService.get();
        final int gridBlocks = Math.max(1, cfgSnap.highwayGridBlocks());

        return ComputeService.supplyAsync(() -> {
            if (Thread.currentThread().isInterrupted()) return new ArrayList<StructureConnection>();
            if (!ThreadPoolManager.isEpoch(epoch)) return new ArrayList<StructureConnection>();
            if (cfgSnap == null || !cfgSnap.highwayEnabled() || !cfgSnap.highwayAutoPlanEnabled()) {
                return new ArrayList<StructureConnection>();
            }
            return new ArrayList<>(buildGridConnections(gridBlocks, minBlockX, minBlockZ, maxBlockX, maxBlockZ));
        }).thenAccept(incoming -> {
            if (incoming == null || incoming.isEmpty()) return;
            if (!ThreadPoolManager.isEpoch(epoch)) return;
            var server = level.getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!ThreadPoolManager.isEpoch(epoch)) return;
                WorldDataProvider provider = WorldDataProvider.getInstance();
                List<StructureConnection> existing = provider.getHighwayConnections(level);
                List<StructureConnection> merged = mergeConnections(existing, incoming);
                if (merged.size() != (existing == null ? 0 : existing.size())) {
                    provider.setHighwayConnections(level, merged);
                }
            });
        });
    }

    private static List<StructureConnection> buildGridConnections(int gridBlocks,
                                                                  int minBlockX,
                                                                  int minBlockZ,
                                                                  int maxBlockX,
                                                                  int maxBlockZ) {
        int gx0 = floorDiv(minBlockX, gridBlocks);
        int gz0 = floorDiv(minBlockZ, gridBlocks);
        int gx1 = floorDiv(maxBlockX, gridBlocks);
        int gz1 = floorDiv(maxBlockZ, gridBlocks);

        ArrayList<BlockPos> points = new ArrayList<>();

        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                points.add(new BlockPos(gx * gridBlocks, 0, gz * gridBlocks));
            }
        }

        return buildBackboneConnections(points, gridBlocks);
    }

    // ==================== 工具方法 ====================

    private static List<BlockPos> collectIntersectionPoints(Map<Long, Long> intersections) {
        if (intersections == null || intersections.isEmpty()) return List.of();

        ArrayList<BlockPos> points = new ArrayList<>(intersections.size());
        for (Long encoded : intersections.values()) {
            if (encoded == null || IntersectionSelector.isOceanSkip(encoded)) continue;
            points.add(IntersectionSelector.decodePos(encoded));
        }
        return points;
    }

    private static List<StructureConnection> buildBackboneConnections(List<BlockPos> rawPoints, int gridBlocks) {
        List<BlockPos> points = normalizeBackbonePoints(rawPoints);
        if (points.size() < 2) return List.of();

        int maxEdgeLenBlocks = resolveHighwayBackboneMaxEdgeLen(gridBlocks);
        int maxJoinLenBlocks = resolveHighwayBackboneJoinLen(gridBlocks, maxEdgeLenBlocks);

        List<StructureConnection> primaryEdges = RNGPlanner.planRNG(points, maxEdgeLenBlocks);
        List<StructureConnection> bridges = KNNPlanner.connectComponents(
                points,
                primaryEdges,
                maxJoinLenBlocks,
                RoadConstants.DEFAULT_COMPONENT_MIN_ANGLE_DEG,
                RoadConstants.DEFAULT_COMPONENT_DEGREE_CAP);

        ArrayList<StructureConnection> incoming = new ArrayList<>(primaryEdges.size() + bridges.size());
        incoming.addAll(primaryEdges);
        incoming.addAll(bridges);
        return incoming;
    }

    private static List<BlockPos> normalizeBackbonePoints(List<BlockPos> rawPoints) {
        if (rawPoints == null || rawPoints.isEmpty()) return List.of();

        HashSet<Long> seen = new HashSet<>();
        ArrayList<BlockPos> points = new ArrayList<>(rawPoints.size());
        for (BlockPos point : rawPoints) {
            if (point == null) continue;
            BlockPos flat = new BlockPos(point.getX(), 0, point.getZ());
            long key = PlanningUtils.pos2dKey(flat);
            if (seen.add(key)) points.add(flat);
        }
        points.sort(Comparator.comparingInt((BlockPos pos) -> pos.getX()).thenComparingInt(pos -> pos.getZ()));
        return points;
    }

    private static int resolveHighwayBackboneMaxEdgeLen(int gridBlocks) {
        int diagonalLimit = (int) Math.ceil(gridBlocks * RoadConstants.DIAGONAL_DISTANCE_FACTOR);
        return Math.max(RoadConstants.DEFAULT_PLAN_MAX_EDGE_LEN_BLOCKS, diagonalLimit);
    }

    private static int resolveHighwayBackboneJoinLen(int gridBlocks, int maxEdgeLenBlocks) {
        int diagonalLimit = (int) Math.ceil(gridBlocks * RoadConstants.DIAGONAL_DISTANCE_FACTOR);
        return Math.max(Math.max(RoadConstants.DEFAULT_BRIDGE_JOIN_LEN_BLOCKS, maxEdgeLenBlocks), diagonalLimit);
    }

    private static List<StructureConnection> mergeConnections(List<StructureConnection> existing,
                                                              List<StructureConnection> incoming) {
        HashSet<Long> seen = new HashSet<>();
        ArrayList<StructureConnection> out = new ArrayList<>();

        if (existing != null) {
            for (StructureConnection c : existing) {
                long k = PlanningUtils.edgeKey(c.from(), c.to());
                if (seen.add(k)) out.add(c);
            }
        }

        for (StructureConnection c : incoming) {
            long k = PlanningUtils.edgeKey(c.from(), c.to());
            if (seen.add(k)) {
                out.add(new StructureConnection(c.from(), c.to(), ConnectionStatus.PLANNED));
            }
        }

        return out;
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    private record PlanResult(Map<Long, Long> intersections, List<StructureConnection> connections) {}
}
