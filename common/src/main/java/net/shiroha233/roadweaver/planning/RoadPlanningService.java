/* 文件职责：协调结构连接规划、区域级精确地形采样与预计算路径。 */
package net.shiroha233.roadweaver.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.sub.PathfindingCostConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressTracker;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationStage;
import net.shiroha233.roadweaver.map.MapPatchService;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileRect;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.map.tile.storage.ServerMapTileStorage;
import net.shiroha233.roadweaver.pathfinding.Pathfinder;
import net.shiroha233.roadweaver.pathfinding.PathfinderFactory;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainRegion;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainPngWriter;
import net.shiroha233.roadweaver.pathfinding.terrain.region.AccurateTerrainRegionSampler;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainPngWriter;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainRegion;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainRegionSampler;
import net.shiroha233.roadweaver.pathfinding.terrain.region.PlannedPathCache;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.LegacyRoadDataRepairService;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.persistence.sqlite.H2MigrationCoordinator;
import net.shiroha233.roadweaver.planning.impl.KNNPlanner;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.search.StructureIndexService;
import net.shiroha233.roadweaver.util.ComputeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static net.shiroha233.roadweaver.pathfinding.impl.PathfindingHelper.snapToGrid;

/**
 * 道路规划服务
 */
public final class RoadPlanningService {
    private RoadPlanningService() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final Set<Long> TERRAIN_REPAIR_TILES = ConcurrentHashMap.newKeySet();
    private static final Set<Long> ROAD_BACKFILL_PLAN_TILES = ConcurrentHashMap.newKeySet();
    private static final Set<Long> PLANNING_TILES = ConcurrentHashMap.newKeySet();

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
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        InitialGenerationProgressTracker.enterStage(InitialGenerationStage.PLANNING, "discovering_structures");
        ModConfig cfg = ConfigService.get();
        int radiusChunks = Math.max(1, cfg.planning().initialPlanRadiusChunks());
        BlockPos spawn = level.getSharedSpawnPos();
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
        ServerLevel level = player.serverLevel();
        if (!Level.OVERWORLD.equals(level.dimension())) return;
        ModConfig cfg = ConfigService.get();
        if (!cfg.planning().dynamicPlanEnabled()) return;
        if (H2MigrationCoordinator.hasPendingLegacyData(level)) return;
        int radiusChunks = Math.max(1, cfg.planning().dynamicPlanRadiusChunks());
        int stride = Math.max(1, cfg.planning().dynamicPlanStrideChunks());
        int tile = Math.max(RoadConstants.PLAN_TILE_MIN, Math.min(RoadConstants.PLAN_TILE_MAX, stride));
        int pcx = player.chunkPosition().x;
        int pcz = player.chunkPosition().z;
        int kx = floorDiv(pcx, tile);
        int kz = floorDiv(pcz, tile);
        long key = (((long) kx) << 32) ^ (kz & 0xffffffffL);
        int minX = (pcx - radiusChunks) * 16;
        int maxX = (pcx + radiusChunks) * 16;
        int minZ = (pcz - radiusChunks) * 16;
        int maxZ = (pcz + radiusChunks) * 16;
        WorldDataProvider provider0 = WorldDataProvider.getInstance();
        boolean alreadyPlanned = provider0.getPlannedTileKeys(level).contains(key);
        boolean hasExistingRoads = RoadShardStorage.hasRoadInRect(level, minX, minZ, maxX, maxZ);
        if (hasExistingRoads) {
            LegacyRoadDataRepairService.repairRoadMetadataInRect(level, minX, minZ, maxX, maxZ);
        }

        boolean backfillPlan = hasExistingRoads && markRoadBackfillPlanTile(level, key);
        boolean shouldPlan = !alreadyPlanned || backfillPlan;
        if (!shouldPlan) {
            ensureTerrainMapTilesAsync(level, key, minX, minZ, maxX, maxZ);
            return;
        }
        if (!markPlanningTile(level, key)) return;
        planRectAsync(level, minX, minZ, maxX, maxZ).whenComplete((ignored, error) -> {
            unmarkPlanningTile(level, key);
            if (hasExistingRoads) {
                ensureTerrainMapTilesAsync(level, key, minX, minZ, maxX, maxZ);
            }
            if (error != null) {
                if (backfillPlan) unmarkRoadBackfillPlanTile(level, key);
                LOGGER.warn("动态规划 tile 失败，保留重试机会 dimension={} tile=[{},{}]", level.dimension().location(), kx, kz, error);
                return;
            }
            markPlannedTile(level, key, pcx, pcz);
            prunePlannedIfTooLarge(level);
        });
    }

    private static boolean markRoadBackfillPlanTile(ServerLevel level, long tileKey) {
        return ROAD_BACKFILL_PLAN_TILES.add(tileKey);
    }

    private static void unmarkRoadBackfillPlanTile(ServerLevel level, long tileKey) {
        ROAD_BACKFILL_PLAN_TILES.remove(tileKey);
    }

    private static boolean markPlanningTile(ServerLevel level, long tileKey) {
        return PLANNING_TILES.add(tileKey);
    }

    private static void unmarkPlanningTile(ServerLevel level, long tileKey) {
        PLANNING_TILES.remove(tileKey);
    }

    private static void markPlannedTile(ServerLevel level, long tileKey, int centerChunkX, int centerChunkZ) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        Set<Long> set = new HashSet<>(provider.getPlannedTileKeys(level));
        set.add(tileKey);
        Map<Long, Long> centers = new HashMap<>(provider.getPlannedTileCenters(level));
        centers.putIfAbsent(tileKey, (((long) centerChunkX) << 32) ^ (centerChunkZ & 0xffffffffL));
        provider.setPlannedTileKeys(level, set);
        provider.setPlannedTileCenters(level, centers);
    }

    private static void ensureTerrainMapTilesAsync(ServerLevel level,
                                                   long tileKey,
                                                   int minBlockX,
                                                   int minBlockZ,
                                                   int maxBlockX,
                                                   int maxBlockZ) {
        if (TERRAIN_REPAIR_TILES.contains(tileKey)) return;
        if (hasCompleteTerrainTiles(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
            TERRAIN_REPAIR_TILES.add(tileKey);
            return;
        }
        if (!TERRAIN_REPAIR_TILES.add(tileKey)) return;
        ComputeService.runAsync(ThreadPoolManager.TaskRole.COARSE, () -> {
            CoarseTerrainRegion region = null;
            boolean completed = false;
            try {
                ModConfig cfg = ConfigService.get();
                int step = cfg.pathfindingCost().effectiveAStarStep();
                region = CoarseTerrainRegionSampler.sample(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, step);
                CoarseTerrainPngWriter.writeTerrainTiles(level, region);
                completed = true;
                LOGGER.info("已补全规划区域地图回退地形瓦片 dimension={} min=({}, {}) max=({}, {})",
                        level.dimension().location(), minBlockX, minBlockZ, maxBlockX, maxBlockZ);
            } catch (RuntimeException e) {
                LOGGER.warn("补全规划区域地图回退地形瓦片失败 dimension={}", level.dimension().location(), e);
            } finally {
                if (region != null) region.dispose();
                if (!completed) {
                    TERRAIN_REPAIR_TILES.remove(tileKey);
                }
            }
        });
    }

    private static boolean hasCompleteTerrainTiles(ServerLevel level,
                                                   int minBlockX,
                                                   int minBlockZ,
                                                   int maxBlockX,
                                                   int maxBlockZ) {
        for (int zoom = MapTileScheme.MIN_ZOOM; zoom <= MapTileScheme.MAX_ZOOM; zoom++) {
            MapTileRect rect = MapTileScheme.tileRectForBlockRect(zoom, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
            for (MapTileCoord coord : rect.coords()) {
                if (!ServerMapTileStorage.exists(level, MapTileLayer.TERRAIN, coord)) return false;
            }
        }
        return true;
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
        if (merged.size() != (existing == null ? 0 : existing.size())) {
            provider.setStructureConnections(level, merged);
            publishNewConnections(level, existing, incoming);
        }
        prepareAccurateRegionForConnections(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, incoming);
    }

    private static void collectStructurePointsInto(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ, List<BlockPos> out, Set<Long> seenPos) {
        StructureIndexService.predictAndVerifyInRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        List<StructureInfo> cached = StructureFileStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
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
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return CompletableFuture.completedFuture(null);
        ModConfig cfg = ConfigService.get();
        int radiusChunks = Math.max(1, cfg.planning().initialPlanRadiusChunks());
        BlockPos spawn = level.getSharedSpawnPos();
        int cx = spawn.getX() >> 4;
        int cz = spawn.getZ() >> 4;
        int minX = (cx - radiusChunks) * 16;
        int maxX = (cx + radiusChunks) * 16;
        int minZ = (cz - radiusChunks) * 16;
        int maxZ = (cz + radiusChunks) * 16;
        return planRectAsync(level, minX, minZ, maxX, maxZ);
    }

    private record PlannedRegionResult(List<StructureConnection> incoming) {}

    public static CompletableFuture<Void> planRectAsync(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return CompletableFuture.completedFuture(null);
        final long epoch = ThreadPoolManager.currentEpoch();
        final List<StructureConnection> existingSnapshot;
        {
            WorldDataProvider prov = WorldDataProvider.getInstance();
            List<StructureConnection> ex = prov.getStructureConnections(level);
            existingSnapshot = ex != null ? new ArrayList<>(ex) : new ArrayList<>();
        }
        return ComputeService.supplyAsync(() -> {
            if (Thread.currentThread().isInterrupted()) return new PlannedRegionResult(List.of());
            if (!ThreadPoolManager.isEpoch(epoch)) return new PlannedRegionResult(List.of());
            ArrayList<BlockPos> points = new ArrayList<>();
            HashSet<Long> seenPos = new HashSet<>();
            collectStructurePointsInto(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, points, seenPos);
            for (StructureConnection c : existingSnapshot) {
                BlockPos f = new BlockPos(c.from().getX(), 0, c.from().getZ());
                BlockPos t = new BlockPos(c.to().getX(), 0, c.to().getZ());
                if (inRect2d(f, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
                    long kf = PlanningUtils.pos2dKey(f);
                    if (seenPos.add(kf)) points.add(f);
                }
                if (inRect2d(t, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
                    long kt = PlanningUtils.pos2dKey(t);
                    if (seenPos.add(kt)) points.add(t);
                }
            }
            if (points.size() < 2) return new PlannedRegionResult(List.of());

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
            if (primaryEdges.isEmpty() && existingInRect.isEmpty()) return new PlannedRegionResult(List.of());

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
            AccurateTerrainRegion region = prepareAccurateRegion(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, incoming);
            if (region != null) {
                computeExactPathsAndRetain(level, region, incoming);
            }
            return new PlannedRegionResult(incoming);
        }).thenAccept((PlannedRegionResult result) -> {
            if (result == null || result.incoming().isEmpty()) return;
            publishIncomingConnections(level, epoch, result.incoming());
        });
    }

    private static void publishIncomingConnections(ServerLevel level, long epoch, List<StructureConnection> incoming) {
        if (level == null || incoming == null || incoming.isEmpty()) return;
        if (!ThreadPoolManager.isEpoch(epoch)) return;
        var server = level.getServer();
        if (server == null) return;
        ArrayList<StructureConnection> snapshot = new ArrayList<>(incoming);
        server.execute(() -> {
            if (!ThreadPoolManager.isEpoch(epoch)) return;
            WorldDataProvider provider = WorldDataProvider.getInstance();
            List<StructureConnection> existing = provider.getStructureConnections(level);
            List<StructureConnection> merged = mergeConnections(existing, snapshot);
            if (merged.size() != (existing == null ? 0 : existing.size())) {
                provider.setStructureConnections(level, merged);
                publishNewConnections(level, existing, snapshot);
            }
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

    private static void publishNewConnections(ServerLevel level,
                                              List<StructureConnection> existing,
                                              List<StructureConnection> incoming) {
        if (level == null || incoming == null || incoming.isEmpty()) return;
        HashSet<Long> existingKeys = new HashSet<>();
        if (existing != null) {
            for (StructureConnection connection : existing) {
                if (connection != null) {
                    existingKeys.add(PlanningUtils.edgeKey(connection.from(), connection.to()));
                }
            }
        }
        for (StructureConnection connection : incoming) {
            if (connection == null) continue;
            long key = PlanningUtils.edgeKey(connection.from(), connection.to());
            if (existingKeys.add(key)) {
                MapPatchService.publishConnection(level,
                        new StructureConnection(connection.from(), connection.to(), ConnectionStatus.PLANNED));
            }
        }
    }

    private static void prepareAccurateRegionForConnections(ServerLevel level,
                                                            int minBlockX,
                                                            int minBlockZ,
                                                            int maxBlockX,
                                                            int maxBlockZ,
                                                            List<StructureConnection> incoming) {
        AccurateTerrainRegion region = prepareAccurateRegion(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, incoming);
        if (region != null) {
            computeExactPathsAndRetain(level, region, incoming);
        }
    }

    private static AccurateTerrainRegion prepareAccurateRegion(ServerLevel level,
                                                               int minBlockX,
                                                               int minBlockZ,
                                                               int maxBlockX,
                                                               int maxBlockZ,
                                                               List<StructureConnection> incoming) {
        if (level == null || incoming == null || incoming.isEmpty()) return null;
        ModConfig cfg = ConfigService.get();
        int step = cfg.pathfindingCost().effectiveAStarStep();
        TerrainSamplingCache cache = new TerrainSamplingCache();
        try {
            return AccurateTerrainRegionSampler.sample(level, cache,
                    minBlockX, minBlockZ, maxBlockX, maxBlockZ, step);
        } catch (RuntimeException failure) {
            LOGGER.warn("精确规划区域采样被拒绝 dimension={} min=({}, {}) max=({}, {}) reason={}",
                    level.dimension().location(), minBlockX, minBlockZ, maxBlockX, maxBlockZ, failure.getMessage());
            return null;
        } finally {
            cache.clear();
        }
    }

    /**
     * 对精确量化区域内的连接预计算路径，并把区域保留到对应道路全部生成完成。
     */
    private static void computeExactPathsAndRetain(ServerLevel level,
                                                   AccurateTerrainRegion region,
                                                   List<StructureConnection> connections) {
        ModConfig cfg = ConfigService.get();
        int dGrid = cfg.pathfindingCost().effectiveAStarStep();
        int maxSteps = cfg.pathfindingCost().aStarMaxSteps();
        InitialGenerationProgressTracker.enterStage(InitialGenerationStage.EXACT_PATHING, "computing_accurate_paths");
        int totalPaths = 0;
        for (StructureConnection connection : connections) {
            if (connection == null) continue;
            if (!region.contains(connection.from().getX(), connection.from().getZ())) continue;
            if (!region.contains(connection.to().getX(), connection.to().getZ())) continue;
            totalPaths++;
        }
        InitialGenerationProgressTracker.setExactPathPlan(totalPaths, "computing_accurate_paths");
        TerrainSamplingCache cache = new TerrainSamplingCache();
        LinkedHashMap<StructureConnection, List<BlockPos>> plannedPaths = new LinkedHashMap<>();
        try {
            for (StructureConnection conn : connections) {
                if (conn == null) continue;
                if (!region.contains(conn.from().getX(), conn.from().getZ())) continue;
                if (!region.contains(conn.to().getX(), conn.to().getZ())) continue;
                if (Thread.currentThread().isInterrupted()) break;

                try {
                    List<BlockPos> path = searchExactPath(level, conn, dGrid, maxSteps, cache, region);
                    if (path != null && !path.isEmpty()) {
                        plannedPaths.put(conn, path);
                    }
                } finally {
                    InitialGenerationProgressTracker.recordExactPathDone();
                }
            }
        } finally {
            cache.clear();
        }

        boolean transferred = false;
        try {
            try {
                AccurateTerrainPngWriter.writeTerrainTiles(level, region);
            } catch (RuntimeException failure) {
                LOGGER.warn("精确地形地图瓦片写入失败 dimension={}", level.dimension().location(), failure);
            }
            PlannedPathCache.register(level, region, plannedPaths);
            transferred = true;
        } finally {
            if (!transferred && !region.isDisposed()) {
                region.dispose();
            }
        }
    }

    private static List<BlockPos> searchExactPath(ServerLevel level,
                                                   StructureConnection conn,
                                                   int dGrid,
                                                   int maxSteps,
                                                   TerrainSamplingCache cache,
                                                   AccurateTerrainRegion region) {
        int sx = snapToGrid(conn.from().getX(), dGrid);
        int sz = snapToGrid(conn.from().getZ(), dGrid);
        int ex = snapToGrid(conn.to().getX(), dGrid);
        int ez = snapToGrid(conn.to().getZ(), dGrid);
        int startY = region.height(sx, sz);
        int endY = region.height(ex, ez);
        BlockPos startGround = new BlockPos(sx, startY, sz);
        BlockPos endGround = new BlockPos(ex, endY, ez);

        if (!region.contains(startGround.getX(), startGround.getZ())) return null;
        if (!region.contains(endGround.getX(), endGround.getZ())) return null;

        PathfindingCostConfig pathCfg = ConfigService.get().pathfindingCost();
        var algo = pathCfg.pathfindingAlgorithm();
        Pathfinder pathfinder = PathfinderFactory.create(algo);
        var result = pathfinder.findRawPath(startGround, endGround, level, maxSteps, cache, region, pathCfg);
        if (!result.success() || !result.hasRawPath()) return null;
        return result.rawPath();
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
        TERRAIN_REPAIR_TILES.clear();
        ROAD_BACKFILL_PLAN_TILES.clear();
        PLANNING_TILES.clear();
    }
}
