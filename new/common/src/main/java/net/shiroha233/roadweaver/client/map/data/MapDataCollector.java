package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.persistence.sqlite.StructureSqliteStorage;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.planning.RoadPlanningService;
import net.shiroha233.roadweaver.search.StructureIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 地图数据收集器 - 从服务端收集道路网络数据
 */
public final class MapDataCollector {
    private MapDataCollector() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final boolean PERF_DEBUG = Boolean.getBoolean("roadweaver.mapPerfDebug");

    public static final int MAX_DETAILED_ROAD_SPAN_BLOCKS = 7680;

    public static MapSnapshot build(ServerLevel level) {
        final long tAll0 = PERF_DEBUG ? System.nanoTime() : 0L;
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> connections = provider.getStructureConnections(level);
        List<StructureConnection> highwayConnections = provider.getHighwayConnections(level);
        List<StructureConnection> conns = (connections != null) ? new ArrayList<>(connections) : new ArrayList<>();
        List<List<BlockPos>> roads = new ArrayList<>();
        ModConfig cfg = ConfigService.get();
        BlockPos spawn = level.getSharedSpawnPos();
        int radiusBlocks;
        if (cfg.highway().enabled()) {
            radiusBlocks = cfg.highway().planningRadiusBlocks();
        } else {
            int radiusChunks = cfg.planning().dynamicPlanEnabled()
                    ? cfg.planning().dynamicPlanRadiusChunks()
                    : cfg.planning().initialPlanRadiusChunks();
            radiusBlocks = Math.max(1, radiusChunks) * RoadConstants.CHUNK_SIZE_BLOCKS;
        }
        radiusBlocks = Math.max(RoadConstants.CHUNK_SIZE_BLOCKS, radiusBlocks);
        int minX = spawn.getX() - radiusBlocks;
        int maxX = spawn.getX() + radiusBlocks;
        int minZ = spawn.getZ() - radiusBlocks;
        int maxZ = spawn.getZ() + radiusBlocks;

        boolean allowPredicted = cfg != null
                && cfg.structurePrediction().enabled()
                && cfg.structurePrediction().isEnabledForDimension(level.dimension().location().toString());
        int[] src = allowPredicted
                ? new int[]{StructureSqliteStorage.SOURCE_MANUAL, StructureSqliteStorage.SOURCE_PREDICTED}
                : new int[]{StructureSqliteStorage.SOURCE_MANUAL};
        List<StructureInfo> cachedInfos = StructureSqliteStorage.queryRect(level, minX, minZ, maxX, maxZ, src);
        java.util.HashMap<Long, StructureInfo> bestInfoByPos = new java.util.HashMap<>();
        java.util.HashSet<BlockPos> structuresSet = new java.util.HashSet<>();
        for (StructureInfo info : cachedInfos) {
            if (info == null || info.pos() == null) continue;
            mergeBestStructureInfo(bestInfoByPos, info);
            BlockPos p = info.pos();
            structuresSet.add(new BlockPos(p.getX(), 0, p.getZ()));
        }
        List<BlockPos> structures = new ArrayList<>(structuresSet);
        List<StructureInfo> infos = new ArrayList<>(bestInfoByPos.values());
        List<RoadData> roadDataList = RoadShardStorage.queryRect(level, minX, minZ, maxX, maxZ);
        for (RoadData rd : roadDataList) {
            List<RoadSegmentPlacement> segs = rd.roadSegmentList();
            if (segs == null || segs.isEmpty()) continue;
            ArrayList<BlockPos> poly = new ArrayList<>(segs.size());
            for (RoadSegmentPlacement sp : segs) poly.add(sp.middlePos());
            if (poly.size() >= 2) roads.add(poly);
        }

        if (highwayConnections != null && !highwayConnections.isEmpty()) {
            HashSet<Long> seenEdges = new HashSet<>();
            for (StructureConnection c : conns) seenEdges.add(PlanningUtils.edgeKey(c.from(), c.to()));
            HashSet<BlockPos> existingStructures = new HashSet<>(structures);
            for (StructureConnection hc : highwayConnections) {
                long k = PlanningUtils.edgeKey(hc.from(), hc.to());
                if (seenEdges.add(k)) conns.add(hc);
                BlockPos f = new BlockPos(hc.from().getX(), 0, hc.from().getZ());
                BlockPos t = new BlockPos(hc.to().getX(), 0, hc.to().getZ());
                if (existingStructures.add(f)) structures.add(f);
                if (existingStructures.add(t)) structures.add(t);
            }
        }

        final long tPred0 = PERF_DEBUG ? System.nanoTime() : 0L;
        List<StructureInfo> verified = StructureIndexService.predictAndVerifyAroundSpawn(level);
        final long predMs = PERF_DEBUG ? (System.nanoTime() - tPred0) / 1_000_000L : 0L;
        if (!verified.isEmpty()) {
            Set<BlockPos> existing = new HashSet<>(structures);
            for (StructureInfo info : verified) {
                BlockPos p0 = info.pos();
                BlockPos p = new BlockPos(p0.getX(), 0, p0.getZ());
                if (!existing.contains(p)) {
                    structures.add(p);
                    mergeBestStructureInfo(bestInfoByPos, info);
                    existing.add(p);
                }
            }
            infos = new ArrayList<>(bestInfoByPos.values());
        }

        if (PERF_DEBUG) {
            int cachedCount = cachedInfos != null ? cachedInfos.size() : 0;
            int conCount = connections != null ? connections.size() : 0;
            int hwCount = highwayConnections != null ? highwayConnections.size() : 0;
            int outStructures = structures.size();
            int outInfos = infos.size();
            int predictedCount = verified.size();
            long totalMs = (System.nanoTime() - tAll0) / 1_000_000L;
            LOGGER.info("MapDataCollector.build(spawn) rect=[{},{}..{},{}] spawn=[{},{}] predictR={} cachedInfos={} conns={} hwConns={} out(struct={},info={}) predicted={} timeMs={} predMs={}",
                    minX, minZ, maxX, maxZ,
                    spawn.getX(), spawn.getZ(), radiusBlocks,
                    cachedCount,
                    conCount, hwCount,
                    outStructures, outInfos,
                    predictedCount,
                    totalMs, predMs);
        }

        return new MapSnapshot(structures, conns, infos, roads);
    }

    public static MapSnapshot build(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> connections = provider.getStructureConnections(level);
        List<StructureConnection> highwayConnections = provider.getHighwayConnections(level);

        StructureIndexService.predictAndVerifyInRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);

        ModConfig cfg = ConfigService.get();
        boolean allowPredicted = cfg != null
                && cfg.structurePrediction().enabled()
                && cfg.structurePrediction().isEnabledForDimension(level.dimension().location().toString());
        int[] src = allowPredicted
                ? new int[]{StructureSqliteStorage.SOURCE_MANUAL, StructureSqliteStorage.SOURCE_PREDICTED}
                : new int[]{StructureSqliteStorage.SOURCE_MANUAL};

        java.util.List<StructureInfo> cached = StructureSqliteStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, src);
        java.util.HashMap<Long, StructureInfo> bestInfoByPos = new java.util.HashMap<>();
        java.util.HashSet<BlockPos> structuresSet = new java.util.HashSet<>();
        for (StructureInfo info : cached) {
            if (info == null || info.pos() == null) continue;
            mergeBestStructureInfo(bestInfoByPos, info);
            BlockPos p = info.pos();
            structuresSet.add(new BlockPos(p.getX(), 0, p.getZ()));
        }
        List<BlockPos> structures = new ArrayList<>(structuresSet);

        List<StructureConnection> conns = new ArrayList<>();
        if (connections != null) {
            for (StructureConnection c : connections) {
                BlockPos a = c.from();
                BlockPos b = c.to();
                boolean ina = a.getX() >= minBlockX && a.getX() <= maxBlockX && a.getZ() >= minBlockZ && a.getZ() <= maxBlockZ;
                boolean inb = b.getX() >= minBlockX && b.getX() <= maxBlockX && b.getZ() >= minBlockZ && b.getZ() <= maxBlockZ;
                if (ina || inb) conns.add(c);
            }
        }

        if (highwayConnections != null && !highwayConnections.isEmpty()) {
            HashSet<Long> seenEdges = new HashSet<>();
            for (StructureConnection c : conns) seenEdges.add(PlanningUtils.edgeKey(c.from(), c.to()));
            for (StructureConnection hc : highwayConnections) {
                BlockPos a = hc.from();
                BlockPos b = hc.to();
                boolean ina = a.getX() >= minBlockX && a.getX() <= maxBlockX && a.getZ() >= minBlockZ && a.getZ() <= maxBlockZ;
                boolean inb = b.getX() >= minBlockX && b.getX() <= maxBlockX && b.getZ() >= minBlockZ && b.getZ() <= maxBlockZ;
                if (ina || inb) {
                    long k = PlanningUtils.edgeKey(a, b);
                    if (seenEdges.add(k)) conns.add(hc);
                }
            }
        }

        List<StructureInfo> infos = new ArrayList<>(bestInfoByPos.values());

        List<List<BlockPos>> roads = new ArrayList<>();
        List<RoadData> roadDataList = RoadShardStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        for (RoadData rd : roadDataList) {
            List<RoadSegmentPlacement> segs = rd.roadSegmentList();
            if (segs == null || segs.isEmpty()) continue;
            ArrayList<BlockPos> poly = new ArrayList<>(segs.size());
            for (RoadSegmentPlacement sp : segs) {
                BlockPos p = sp.middlePos();
                int x = p.getX(), z = p.getZ();
                if (x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ) poly.add(p);
            }
            if (poly.size() >= 2) roads.add(poly);
        }

        HashSet<BlockPos> existing = new HashSet<>(structures);
        for (StructureConnection c : conns) {
            BlockPos[] eps = new BlockPos[]{c.from(), c.to()};
            for (BlockPos ep : eps) {
                int x = ep.getX(), z = ep.getZ();
                BlockPos ep2d = new BlockPos(x, 0, z);
                boolean inRect = x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ;
                if (inRect && existing.add(ep2d)) {
                    structures.add(ep2d);
                }
            }
        }

        return new MapSnapshot(structures, conns, infos, roads);
    }

    public static MapSnapshot build(ServerLevel level,
                                    int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ,
                                    int centerX, int centerZ, int radiusBlocks) {
        final long tAll0 = PERF_DEBUG ? System.nanoTime() : 0L;
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> connections = provider.getStructureConnections(level);
        List<StructureConnection> highwayConnections = provider.getHighwayConnections(level);

        ModConfig cfg = ConfigService.get();
        boolean allowPredicted = cfg != null
                && cfg.structurePrediction().enabled()
                && cfg.structurePrediction().isEnabledForDimension(level.dimension().location().toString());
        int[] src = allowPredicted
                ? new int[]{StructureSqliteStorage.SOURCE_MANUAL, StructureSqliteStorage.SOURCE_PREDICTED}
                : new int[]{StructureSqliteStorage.SOURCE_MANUAL};

        final long r2 = (long) Math.max(0, radiusBlocks) * (long) Math.max(0, radiusBlocks);
        java.util.function.BiPredicate<Integer, Integer> inAOI = (x, z) -> {
            if (r2 <= 0) return true;
            long dx = (long) x - centerX;
            long dz = (long) z - centerZ;
            return dx * dx + dz * dz <= r2;
        };

        int spanX = Math.abs(maxBlockX - minBlockX);
        int spanZ = Math.abs(maxBlockZ - minBlockZ);

        java.util.Set<BlockPos> plannedEndpoints = new java.util.HashSet<>();
        if (connections != null) {
            for (StructureConnection c : connections) {
                plannedEndpoints.add(new BlockPos(c.from().getX(), 0, c.from().getZ()));
                plannedEndpoints.add(new BlockPos(c.to().getX(), 0, c.to().getZ()));
            }
        }
        if (highwayConnections != null) {
            for (StructureConnection c : highwayConnections) {
                plannedEndpoints.add(new BlockPos(c.from().getX(), 0, c.from().getZ()));
                plannedEndpoints.add(new BlockPos(c.to().getX(), 0, c.to().getZ()));
            }
        }

        ModConfig cfgAll = ConfigService.get();
        int initialRadiusBlocks = Math.max(1, cfgAll.planning().initialPlanRadiusChunks()) * RoadConstants.CHUNK_SIZE_BLOCKS;
        int strideChunks = RoadPlanningService.getStrideTileSizeChunks();
        int dynRadiusChunks = RoadPlanningService.getDynamicPlanRadiusChunks();
        int dynRadiusBlocks = Math.max(1, dynRadiusChunks) * RoadConstants.CHUNK_SIZE_BLOCKS;
        long initialR2 = (long) initialRadiusBlocks * (long) initialRadiusBlocks;
        BlockPos spawn = level.getSharedSpawnPos();

        java.util.Set<Long> plannedTiles = RoadPlanningService.getPlannedTiles(level);
        java.util.Map<Long, Long> centersMap = RoadPlanningService.getPlannedTileCenters(level);
        int safeStrideChunks = Math.max(1, strideChunks);
        int safeDynRadiusChunks = Math.max(1, dynRadiusChunks);
        int neighborTiles = Math.floorDiv(safeDynRadiusChunks, safeStrideChunks) + 2;
        java.util.function.BiPredicate<Integer, Integer> inPlannedCoverage = (x, z) -> {
            if (Level.OVERWORLD.equals(level.dimension())) {
                long dxs = (long) x - spawn.getX();
                long dzs = (long) z - spawn.getZ();
                if (dxs * dxs + dzs * dzs <= initialR2) return true;
            }

            if (plannedTiles == null || plannedTiles.isEmpty()) return false;

            int chunkX = Math.floorDiv(x, RoadConstants.CHUNK_SIZE_BLOCKS);
            int chunkZ = Math.floorDiv(z, RoadConstants.CHUNK_SIZE_BLOCKS);
            int baseTileX = Math.floorDiv(chunkX, safeStrideChunks);
            int baseTileZ = Math.floorDiv(chunkZ, safeStrideChunks);

            for (int dx = -neighborTiles; dx <= neighborTiles; dx++) {
                for (int dz = -neighborTiles; dz <= neighborTiles; dz++) {
                    int kx = baseTileX + dx;
                    int kz = baseTileZ + dz;
                    long key = (((long) kx) << 32) ^ (kz & 0xffffffffL);
                    if (!plannedTiles.contains(key)) continue;

                    int centerChunkX;
                    int centerChunkZ;
                    Long cval = centersMap.get(key);
                    if (cval != null) {
                        centerChunkX = (int) (cval >> 32);
                        centerChunkZ = (int) (cval & 0xffffffffL);
                    } else {
                        centerChunkX = kx * safeStrideChunks + safeStrideChunks / 2;
                        centerChunkZ = kz * safeStrideChunks + safeStrideChunks / 2;
                    }

                    int cxBlocks = centerChunkX * RoadConstants.CHUNK_SIZE_BLOCKS;
                    int czBlocks = centerChunkZ * RoadConstants.CHUNK_SIZE_BLOCKS;
                    if (x >= cxBlocks - dynRadiusBlocks && x <= cxBlocks + dynRadiusBlocks
                            && z >= czBlocks - dynRadiusBlocks && z <= czBlocks + dynRadiusBlocks) {
                        return true;
                    }
                }
            }
            return false;
        };

        java.util.List<StructureInfo> cached = StructureSqliteStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, src);
        java.util.HashMap<Long, StructureInfo> bestInfoByPos = new java.util.HashMap<>();
        java.util.HashSet<BlockPos> structuresSet = new java.util.HashSet<>();
        for (StructureInfo info : cached) {
            if (info == null || info.pos() == null) continue;
            BlockPos p = info.pos();
            int x = p.getX();
            int z = p.getZ();
            boolean inRect = x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ;
            if (!inRect) continue;
            if (!(plannedEndpoints.contains(p) || inPlannedCoverage.test(x, z) || inAOI.test(x, z))) continue;
            mergeBestStructureInfo(bestInfoByPos, info);
            structuresSet.add(new BlockPos(x, 0, z));
        }
        List<BlockPos> structures = new ArrayList<>(structuresSet);

        if (connections != null) {
            java.util.HashSet<BlockPos> existing = new HashSet<>(structures);
            for (StructureConnection c : connections) {
                BlockPos[] eps = new BlockPos[]{c.from(), c.to()};
                for (BlockPos ep : eps) {
                    int x = ep.getX(), z = ep.getZ();
                    BlockPos ep2d = new BlockPos(x, 0, z);
                    boolean inRect = x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ;
                    if (inRect && !existing.contains(ep2d)) {
                        structures.add(ep2d);
                        existing.add(ep2d);
                    }
                }
            }
        }

        if (highwayConnections != null) {
            java.util.HashSet<BlockPos> existing = new HashSet<>(structures);
            for (StructureConnection c : highwayConnections) {
                BlockPos[] eps = new BlockPos[]{c.from(), c.to()};
                for (BlockPos ep : eps) {
                    int x = ep.getX(), z = ep.getZ();
                    BlockPos ep2d = new BlockPos(x, 0, z);
                    boolean inRect = x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ;
                    if (inRect && !existing.contains(ep2d)) {
                        structures.add(ep2d);
                        existing.add(ep2d);
                    }
                }
            }
        }

        List<StructureConnection> conns = new ArrayList<>();
        java.util.HashSet<Long> seenEdges = new HashSet<>();
        if (connections != null) {
            for (StructureConnection c : connections) {
                BlockPos a = c.from();
                BlockPos b = c.to();
                boolean ina = a.getX() >= minBlockX && a.getX() <= maxBlockX && a.getZ() >= minBlockZ && a.getZ() <= maxBlockZ;
                boolean inb = b.getX() >= minBlockX && b.getX() <= maxBlockX && b.getZ() >= minBlockZ && b.getZ() <= maxBlockZ;
                if (ina || inb) {
                    long k = PlanningUtils.edgeKey(a, b);
                    if (seenEdges.add(k)) conns.add(c);
                }
            }
        }

        if (highwayConnections != null) {
            for (StructureConnection c : highwayConnections) {
                BlockPos a = c.from();
                BlockPos b = c.to();
                boolean ina = a.getX() >= minBlockX && a.getX() <= maxBlockX && a.getZ() >= minBlockZ && a.getZ() <= maxBlockZ;
                boolean inb = b.getX() >= minBlockX && b.getX() <= maxBlockX && b.getZ() >= minBlockZ && b.getZ() <= maxBlockZ;
                if (ina || inb) {
                    long k = PlanningUtils.edgeKey(a, b);
                    if (seenEdges.add(k)) conns.add(c);
                }
            }
        }

        List<StructureInfo> infos = new ArrayList<>(bestInfoByPos.values());

        int predMinX = Math.max(minBlockX, centerX - radiusBlocks);
        int predMaxX = Math.min(maxBlockX, centerX + radiusBlocks);
        int predMinZ = Math.max(minBlockZ, centerZ - radiusBlocks);
        int predMaxZ = Math.min(maxBlockZ, centerZ + radiusBlocks);
        final long tPred0 = PERF_DEBUG ? System.nanoTime() : 0L;
        List<StructureInfo> predictedInfos = (predMinX <= predMaxX && predMinZ <= predMaxZ)
                ? StructureIndexService.predictAndVerifyInRect(level, predMinX, predMinZ, predMaxX, predMaxZ)
                : List.of();
        final long predMs = PERF_DEBUG ? (System.nanoTime() - tPred0) / 1_000_000L : 0L;
        if (!predictedInfos.isEmpty()) {
            java.util.HashSet<BlockPos> existingStructures = new java.util.HashSet<>(structures);
            for (StructureInfo info : predictedInfos) {
                BlockPos p0 = info.pos();
                int x = p0.getX(), z = p0.getZ();
                BlockPos p = new BlockPos(x, 0, z);
                boolean inRect = x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ;
                if (!inRect) continue;
                if (!(plannedEndpoints.contains(p) || inPlannedCoverage.test(x, z) || inAOI.test(x, z))) continue;
                if (existingStructures.add(p)) {
                    structures.add(p);
                }
                mergeBestStructureInfo(bestInfoByPos, info);
            }
            infos = new ArrayList<>(bestInfoByPos.values());
        }

        List<List<BlockPos>> roads = new ArrayList<>();
        boolean loadDetailedRoads = spanX <= MAX_DETAILED_ROAD_SPAN_BLOCKS && spanZ <= MAX_DETAILED_ROAD_SPAN_BLOCKS;
        if (loadDetailedRoads) {
            List<RoadData> roadDataList = RoadShardStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
            for (RoadData rd : roadDataList) {
                List<RoadSegmentPlacement> segs = rd.roadSegmentList();
                if (segs == null || segs.isEmpty()) continue;
                ArrayList<BlockPos> poly = new ArrayList<>(segs.size());
                for (RoadSegmentPlacement sp : segs) {
                    BlockPos p = sp.middlePos();
                    int x = p.getX(), z = p.getZ();
                    if (x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ) {
                        poly.add(p);
                    }
                }
                if (poly.size() >= 2) roads.add(poly);
            }
        }

        if (PERF_DEBUG) {
            int cachedCount = cached != null ? cached.size() : 0;
            int conCount = connections != null ? connections.size() : 0;
            int hwCount = highwayConnections != null ? highwayConnections.size() : 0;
            int outStructures = structures.size();
            int outInfos = infos.size();
            int predictedCount = predictedInfos.size();
            int plannedTileCount = plannedTiles != null ? plannedTiles.size() : 0;
            long totalMs = (System.nanoTime() - tAll0) / 1_000_000L;
            LOGGER.info("MapDataCollector.build rect=[{},{}..{},{}] aoiCenter=[{},{}] aoiR={} span=[{},{}] plannedTiles={} loadRoads={} roads={} cachedInfos={} conns={} hwConns={} out(struct={},info={}) predicted={} timeMs={} predMs={}",
                    minBlockX, minBlockZ, maxBlockX, maxBlockZ,
                    centerX, centerZ, radiusBlocks,
                    spanX, spanZ,
                    plannedTileCount,
                    loadDetailedRoads, roads.size(),
                    cachedCount,
                    conCount, hwCount,
                    outStructures, outInfos,
                    predictedCount,
                    totalMs, predMs);
        }

        return new MapSnapshot(structures, conns, infos, roads);
    }

    private static void mergeBestStructureInfo(java.util.Map<Long, StructureInfo> out, StructureInfo info) {
        if (out == null || info == null || info.pos() == null) return;
        BlockPos p = info.pos();
        int x = p.getX();
        int z = p.getZ();
        long key = (((long) x) << 32) ^ (z & 0xffffffffL);
        StructureInfo prev = out.get(key);
        if (prev == null) {
            out.put(key, new StructureInfo(new BlockPos(x, 0, z), info.structureId()));
            return;
        }
        String prevId = prev.structureId();
        String nextId = info.structureId();
        if (prevId == null) prevId = "unknown";
        if (nextId == null) nextId = "unknown";
        if ("unknown".equals(prevId) && !"unknown".equals(nextId)) {
            out.put(key, new StructureInfo(new BlockPos(x, 0, z), nextId));
        }
    }
}
