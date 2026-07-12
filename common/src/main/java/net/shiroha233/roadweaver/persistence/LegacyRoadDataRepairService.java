package net.shiroha233.roadweaver.persistence;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧道路文件反推结构点与连接状态。
 */
public final class LegacyRoadDataRepairService {
    private LegacyRoadDataRepairService() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    public static void repairServerMetadata(MinecraftServer server) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            repairRoadMetadata(level);
        }
    }

    public static int repairRoadMetadata(ServerLevel level) {
        if (level == null || !RoadShardStorage.hasAnyRoad(level)) return 0;
        WorldDataProvider provider = WorldDataProvider.getInstance();
        provider.getStructureLocations(level);
        List<StructureConnection> existing = provider.getStructureConnections(level);
        return repairFromRoads(level, RoadShardStorage.loadAll(level), existing);
    }

    public static int repairRoadMetadataInRect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (level == null) return 0;
        WorldDataProvider provider = WorldDataProvider.getInstance();
        provider.getStructureLocations(level);
        List<StructureConnection> existing = provider.getStructureConnections(level);
        return repairFromRoads(level, RoadShardStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ), existing);
    }

    private static int repairFromRoads(ServerLevel level, List<RoadData> roads, List<StructureConnection> existing) {
        if (roads == null || roads.isEmpty()) return 0;
        ArrayList<StructureInfo> structures = new ArrayList<>();
        ArrayList<StructureConnection> completed = new ArrayList<>();
        LinkedHashMap<Long, StructureInfo> knownStructures = collectKnownStructures(StructureFileStorage.getStructureLocations(level));
        HashSet<Long> manualStructures = new HashSet<>(StructureFileStorage.manualStructureKeys(level));
        Map<Long, StructureConnection> existingEdges = collectExistingEdges(existing);
        HashSet<Long> seenStructures = new HashSet<>();
        HashSet<Long> seenEdges = new HashSet<>();

        for (RoadData road : roads) {
            EndpointPair endpoints = endpointsOf(road);
            if (endpoints == null) continue;
            addMissingManualStructure(structures, seenStructures, manualStructures, knownStructures, endpoints.from());
            addMissingManualStructure(structures, seenStructures, manualStructures, knownStructures, endpoints.to());
            long edgeKey = PlanningUtils.edgeKey(endpoints.from(), endpoints.to());
            StructureConnection existingConnection = existingEdges.get(edgeKey);
            if (seenEdges.add(edgeKey) && (existingConnection == null || existingConnection.status() != ConnectionStatus.COMPLETED)) {
                completed.add(new StructureConnection(endpoints.from(), endpoints.to(), ConnectionStatus.COMPLETED));
            }
        }

        if (structures.isEmpty() && completed.isEmpty()) return 0;
        StructureFileStorage.addStructures(level, structures, StructureFileStorage.SOURCE_MANUAL);
        StructureFileStorage.mergeStructureConnections(level, completed);
        LOGGER.info("已从旧道路数据补全结构状态 dimension={} structures={} connections={}",
                level.dimension().location(), structures.size(), completed.size());
        return structures.size() + completed.size();
    }

    private static void addMissingManualStructure(List<StructureInfo> out,
                                                  HashSet<Long> seen,
                                                  HashSet<Long> manualStructures,
                                                  LinkedHashMap<Long, StructureInfo> known,
                                                  BlockPos pos) {
        if (pos == null) return;
        long key = PlanningUtils.pos2dKey(pos);
        if (manualStructures.contains(key)) return;
        if (!seen.add(key)) return;
        StructureInfo existing = known.get(key);
        out.add(existing != null ? existing : new StructureInfo(pos, "unknown"));
        manualStructures.add(key);
    }

    private static Map<Long, StructureConnection> collectExistingEdges(List<StructureConnection> existing) {
        LinkedHashMap<Long, StructureConnection> out = new LinkedHashMap<>();
        if (existing == null) return out;
        for (StructureConnection connection : existing) {
            if (!validConnection(connection)) continue;
            out.put(PlanningUtils.edgeKey(connection.from(), connection.to()), connection);
        }
        return out;
    }

    private static LinkedHashMap<Long, StructureInfo> collectKnownStructures(StructureLocationData data) {
        LinkedHashMap<Long, StructureInfo> out = new LinkedHashMap<>();
        if (data == null) return out;
        for (StructureInfo info : data.structureInfos()) {
            if (info != null && info.pos() != null) out.put(PlanningUtils.pos2dKey(info.pos()), info);
        }
        for (BlockPos pos : data.structureLocations()) {
            if (pos != null) out.putIfAbsent(PlanningUtils.pos2dKey(pos), new StructureInfo(pos, "unknown"));
        }
        return out;
    }

    private static boolean validConnection(StructureConnection connection) {
        return connection != null && connection.from() != null && connection.to() != null;
    }

    private static EndpointPair endpointsOf(RoadData road) {
        if (road == null) return null;
        if (road.hasOwnerPair()) {
            BlockPos from = PlanningUtils.posFrom2dKey(road.ownerA2dKey());
            BlockPos to = PlanningUtils.posFrom2dKey(road.ownerB2dKey());
            if (!from.equals(to)) return new EndpointPair(from, to);
        }
        BlockPos first = firstRoadPos(road);
        BlockPos last = lastRoadPos(road);
        if (first == null || last == null || first.equals(last)) return null;
        return new EndpointPair(first, last);
    }

    private static BlockPos firstRoadPos(RoadData road) {
        List<RoadSegmentPlacement> segments = road.roadSegmentList();
        if (segments == null) return null;
        for (RoadSegmentPlacement segment : segments) {
            BlockPos pos = segment == null ? null : segment.middlePos();
            if (pos != null) return new BlockPos(pos.getX(), 0, pos.getZ());
        }
        return null;
    }

    private static BlockPos lastRoadPos(RoadData road) {
        List<RoadSegmentPlacement> segments = road.roadSegmentList();
        if (segments == null) return null;
        for (int i = segments.size() - 1; i >= 0; i--) {
            RoadSegmentPlacement segment = segments.get(i);
            BlockPos pos = segment == null ? null : segment.middlePos();
            if (pos != null) return new BlockPos(pos.getX(), 0, pos.getZ());
        }
        return null;
    }

    private record EndpointPair(BlockPos from, BlockPos to) {}
}
