package net.shiroha233.roadweaver.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.helpers.LevelCompat;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.sqlite.StructureSqliteStorage;
import net.shiroha233.roadweaver.planning.impl.KNNPlanner;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.search.StructureIndexService;
import net.shiroha233.roadweaver.util.ComputeService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 道路规划服务
 */
public final class RoadPlanningService {
    private RoadPlanningService() {}

    private static final ConcurrentHashMap<Level, Set<Long>> PLANNED_TILES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Level, ConcurrentHashMap<Long, Long>> PLANNED_TILE_CENTERS = new ConcurrentHashMap<>();

    private static void prunePlannedIfTooLarge(Level level) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        Set<Long> keys = new HashSet<>(provider.getPlannedTileKeys((ServerLevel) level));
        if (keys.size() > RoadConstants.MAX_PLANNED_KEYS) {
            int remove = keys.size() - RoadConstants.MAX_PLANNED_KEYS;
            Iterator<Long> it = keys.iterator();
            while (remove > 0 && it.hasNext()) { it.next(); it.remove(); remove--; }
            provider.setPlannedTileKeys((ServerLevel) level, keys);
        }
        Map<Long, Long> centers = new HashMap<>(provider.getPlannedTileCenters((ServerLevel) level));
        if (centers.size() > RoadConstants.MAX_PLANNED_KEYS) {
            int remove2 = centers.size() - RoadConstants.MAX_PLANNED_KEYS;
            Iterator<Long> it2 = centers.keySet().iterator();
            while (remove2 > 0 && it2.hasNext()) { it2.next(); it2.remove(); remove2--; }
            provider.setPlannedTileCenters((ServerLevel) level, centers);
        }
    }

    public static void initialPlan(ServerLevel level) {
        if (level == null) return;
        ModConfig cfg = ConfigService.get();
        int radiusChunks = Math.max(1, cfg.planning().initialPlanRadiusChunks());
        BlockPos spawn = LevelCompat.getWorldSpawnPos(level);
        int cx = spawn.getX() >> 4;
        int cz = spawn.getZ() >> 4;
        int minX = (cx - radiusChunks) * 16;
        int maxX = (cx + radiusChunks) * 16;
        int minZ = (cz - radiusChunks) * 16;
        int maxZ = (cz + radiusChunks) * 16;
        planRect(level, minX, minZ, maxX, maxZ);
    }

    public static void planAroundPlayer(ServerPlayer player) {
        if (player == null) return;
        ServerLevel level = player.level();
        ModConfig cfg = ConfigService.get();
        if (!cfg.planning().dynamicPlanEnabled()) return;
        int radiusChunks = Math.max(1, cfg.planning().dynamicPlanRadiusChunks());
        int stride = Math.max(1, cfg.planning().dynamicPlanStrideChunks());
        int tile = Math.max(RoadConstants.PLAN_TILE_MIN, Math.min(RoadConstants.PLAN_TILE_MAX, stride));
        int pcx = player.chunkPosition().x;
        int pcz = player.chunkPosition().z;
        int kx = floorDiv(pcx, tile);
        int kz = floorDiv(pcz, tile);
        long key = (((long) kx) << 32) ^ (kz & 0xffffffffL);
        WorldDataProvider provider0 = WorldDataProvider.getInstance();
        Set<Long> set = new HashSet<>(provider0.getPlannedTileKeys(level));
        boolean isNewTile = set.add(key);
        Map<Long, Long> centers = new HashMap<>(provider0.getPlannedTileCenters(level));
        centers.putIfAbsent(key, (((long) pcx) << 32) ^ (pcz & 0xffffffffL));
        provider0.setPlannedTileKeys(level, set);
        provider0.setPlannedTileCenters(level, centers);
        if (!isNewTile) return;
        int minX = (pcx - radiusChunks) * 16;
        int maxX = (pcx + radiusChunks) * 16;
        int minZ = (pcz - radiusChunks) * 16;
        int maxZ = (pcz + radiusChunks) * 16;
        prunePlannedIfTooLarge(level);
        planRectAsync(level, minX, minZ, maxX, maxZ);
    }

    private static void planRect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        List<BlockPos> points = new ArrayList<>();
        HashSet<Long> seenPos = new HashSet<>();
        collectStructurePointsInto(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, points, seenPos);
        if (points.size() < 2) return;

        ModConfig cfg0 = ConfigService.get();
        NetworkPlanner planner = NetworkPlannerFactory.create(cfg0.planning().planningAlgorithm());
        List<StructureConnection> primaryEdges = planner.plan(points, RoadConstants.DEFAULT_PLAN_MAX_EDGE_LEN_BLOCKS);
        if (primaryEdges.isEmpty()) return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> existing = provider.getStructureConnections(level);

        ArrayList<StructureConnection> existingInRect = new ArrayList<>();
        if (existing != null) {
            for (StructureConnection c : existing) {
                if (inRect2d(c.from(), minBlockX, minBlockZ, maxBlockX, maxBlockZ)
                        && inRect2d(c.to(), minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
                    existingInRect.add(c);
                }
            }
        }

        ArrayList<StructureConnection> base = new ArrayList<>(existingInRect);
        base.addAll(primaryEdges);
        ArrayList<BlockPos> componentPoints = collectComponentPoints(points, existingInRect, primaryEdges);

        List<StructureConnection> bridges = KNNPlanner.connectComponents(
                componentPoints, base,
                RoadConstants.DEFAULT_BRIDGE_JOIN_LEN_BLOCKS,
                RoadConstants.DEFAULT_COMPONENT_MIN_ANGLE_DEG,
                RoadConstants.DEFAULT_COMPONENT_DEGREE_CAP
        );

        ArrayList<StructureConnection> incoming = new ArrayList<>(primaryEdges);
        incoming.addAll(bridges);
        List<StructureConnection> merged = mergeConnections(existing, incoming);
        if (merged.size() != existing.size()) {
            provider.setStructureConnections(level, merged);
        }
    }

    private static void collectStructurePointsInto(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ, List<BlockPos> out, Set<Long> seenPos) {
        StructureIndexService.predictAndVerifyInRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        List<StructureInfo> cached = StructureSqliteStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        if (cached == null || cached.isEmpty()) return;
        for (StructureInfo info : cached) {
            if (info == null || info.pos() == null) continue;
            BlockPos p = info.pos();
            int x = p.getX(), z = p.getZ();
            if (x < minBlockX || x > maxBlockX || z < minBlockZ || z > maxBlockZ) continue;
            BlockPos q = new BlockPos(x, 0, z);
            long key = PlanningUtils.pos2dKey(q);
            if (seenPos.add(key)) out.add(q);
        }
    }

    public static CompletableFuture<Void> initialPlanAsync(ServerLevel level) {
        if (level == null) return CompletableFuture.completedFuture(null);
        ModConfig cfg = ConfigService.get();
        int radiusChunks = Math.max(1, cfg.planning().initialPlanRadiusChunks());
        BlockPos spawn = LevelCompat.getWorldSpawnPos(level);
        int cx = spawn.getX() >> 4;
        int cz = spawn.getZ() >> 4;
        int minX = (cx - radiusChunks) * 16;
        int maxX = (cx + radiusChunks) * 16;
        int minZ = (cz - radiusChunks) * 16;
        int maxZ = (cz + radiusChunks) * 16;
        return planRectAsync(level, minX, minZ, maxX, maxZ);
    }

    public static CompletableFuture<Void> planRectAsync(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        final long epoch = ThreadPoolManager.currentEpoch();
        final List<StructureConnection> existingSnapshot;
        {
            WorldDataProvider prov = WorldDataProvider.getInstance();
            List<StructureConnection> ex = prov.getStructureConnections(level);
            existingSnapshot = ex != null ? new ArrayList<>(ex) : new ArrayList<>();
        }
        return ComputeService.supplyAsync(() -> {
            if (Thread.currentThread().isInterrupted()) return new ArrayList<StructureConnection>();
            if (!ThreadPoolManager.isEpoch(epoch)) return new ArrayList<StructureConnection>();
            ArrayList<BlockPos> points = new ArrayList<>();
            HashSet<Long> seenPos = new HashSet<>();
            collectStructurePointsInto(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, points, seenPos);
            for (StructureConnection c : existingSnapshot) {
                BlockPos f = new BlockPos(c.from().getX(), 0, c.from().getZ());
                BlockPos t = new BlockPos(c.to().getX(), 0, c.to().getZ());
                long kf = PlanningUtils.pos2dKey(f);
                long kt = PlanningUtils.pos2dKey(t);
                if (seenPos.add(kf)) points.add(f);
                if (seenPos.add(kt)) points.add(t);
            }
            if (points.size() < 2) return new ArrayList<StructureConnection>();

            ArrayList<StructureConnection> existingInRect = new ArrayList<>();
            HashSet<Long> existingEdgeKeys = new HashSet<>();
            for (StructureConnection c : existingSnapshot) {
                if (inRect2d(c.from(), minBlockX, minBlockZ, maxBlockX, maxBlockZ) &&
                        inRect2d(c.to(), minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
                    existingInRect.add(c);
                    existingEdgeKeys.add(PlanningUtils.edgeKey(c.from(), c.to()));
                }
            }

            ModConfig cfg0 = ConfigService.get();
            NetworkPlanner planner = NetworkPlannerFactory.create(cfg0.planning().planningAlgorithm());
            List<StructureConnection> primaryEdges = planner.plan(points, RoadConstants.DEFAULT_PLAN_MAX_EDGE_LEN_BLOCKS);
            if (primaryEdges.isEmpty() && existingInRect.isEmpty()) return new ArrayList<StructureConnection>();

            ArrayList<StructureConnection> filteredPrimary = new ArrayList<>();
            for (StructureConnection c : primaryEdges) {
                long ek = PlanningUtils.edgeKey(c.from(), c.to());
                if (!existingEdgeKeys.contains(ek)) filteredPrimary.add(c);
            }

            ArrayList<StructureConnection> base = new ArrayList<>(existingInRect);
            base.addAll(filteredPrimary);
            ArrayList<BlockPos> componentPoints = collectComponentPoints(points, existingInRect, filteredPrimary);
            List<StructureConnection> bridges = KNNPlanner.connectComponents(
                    componentPoints, base,
                    RoadConstants.DEFAULT_BRIDGE_JOIN_LEN_BLOCKS,
                    RoadConstants.DEFAULT_COMPONENT_MIN_ANGLE_DEG,
                    RoadConstants.DEFAULT_COMPONENT_DEGREE_CAP
            );

            ArrayList<StructureConnection> incoming = new ArrayList<>(filteredPrimary);
            incoming.addAll(bridges);
            return incoming;
        }).thenAccept(incoming -> {
            if (incoming == null || incoming.isEmpty()) return;
            if (!ThreadPoolManager.isEpoch(epoch)) return;
            var server = level.getServer();
            if (server == null) return;
            server.execute(() -> {
                if (!ThreadPoolManager.isEpoch(epoch)) return;
                WorldDataProvider provider = WorldDataProvider.getInstance();
                List<StructureConnection> existing = provider.getStructureConnections(level);
                List<StructureConnection> merged = mergeConnections(existing, incoming);
                if (merged.size() != (existing == null ? 0 : existing.size())) {
                    provider.setStructureConnections(level, merged);
                }
            });
        });
    }

    private static List<StructureConnection> mergeConnections(List<StructureConnection> existing, List<StructureConnection> incoming) {
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
            if (seen.add(k)) out.add(new StructureConnection(c.from(), c.to(), ConnectionStatus.PLANNED));
        }
        return out;
    }

    private static ArrayList<BlockPos> collectComponentPoints(List<BlockPos> seed,
                                                              List<StructureConnection> existingInRect,
                                                              List<StructureConnection> incomingEdges) {
        ArrayList<BlockPos> out = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        if (seed != null) {
            for (BlockPos p : seed) addUnique2d(out, seen, p);
        }
        if (existingInRect != null) {
            for (StructureConnection c : existingInRect) {
                if (c == null) continue;
                addUnique2d(out, seen, c.from());
                addUnique2d(out, seen, c.to());
            }
        }
        if (incomingEdges != null) {
            for (StructureConnection c : incomingEdges) {
                if (c == null) continue;
                addUnique2d(out, seen, c.from());
                addUnique2d(out, seen, c.to());
            }
        }
        return out;
    }

    private static void addUnique2d(List<BlockPos> out, Set<Long> seen, BlockPos p) {
        if (p == null) return;
        BlockPos q = new BlockPos(p.getX(), 0, p.getZ());
        long k = PlanningUtils.pos2dKey(q);
        if (seen.add(k)) out.add(q);
    }

    private static boolean inRect2d(BlockPos p, int minX, int minZ, int maxX, int maxZ) {
        if (p == null) return false;
        int x = p.getX();
        int z = p.getZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    public static Set<Long> getPlannedTiles(ServerLevel level) {
        Set<Long> s = WorldDataProvider.getInstance().getPlannedTileKeys(level);
        return s == null ? Set.of() : Set.copyOf(s);
    }

    public static Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        Map<Long, Long> m = WorldDataProvider.getInstance().getPlannedTileCenters(level);
        if (m == null || m.isEmpty()) return Map.of();
        return Map.copyOf(m);
    }

    public static int getStrideTileSizeChunks() {
        ModConfig cfg = ConfigService.get();
        int stride = Math.max(1, cfg.planning().dynamicPlanStrideChunks());
        return Math.max(RoadConstants.PLAN_TILE_MIN, Math.min(RoadConstants.PLAN_TILE_MAX, stride));
    }

    public static int getDynamicPlanRadiusChunks() {
        ModConfig cfg = ConfigService.get();
        return Math.max(1, cfg.planning().dynamicPlanRadiusChunks());
    }

    public static void resetAll() {
        PLANNED_TILES.clear();
        PLANNED_TILE_CENTERS.clear();
    }
}
