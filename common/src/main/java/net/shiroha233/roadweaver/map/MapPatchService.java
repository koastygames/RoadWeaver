package net.shiroha233.roadweaver.map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.network.ServerMapPatchBridge;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图局部更新发布服务。
 */
public final class MapPatchService {
    private MapPatchService() {}

    public static void publishConnection(ServerLevel level, StructureConnection connection) {
        if (level == null || connection == null) return;
        MapSnapshotPatch patch = new MapSnapshotPatch(
                List.of(),
                List.of(normalize(connection)),
                List.of(),
                List.of());
        broadcast(level, patch);
    }

    public static void publishConnectionStatus(ServerLevel level, StructureConnection connection, ConnectionStatus status) {
        if (connection == null || status == null) return;
        publishConnection(level, new StructureConnection(connection.from(), connection.to(), status));
    }

    public static void publishRoadForConnectionAsync(ServerLevel level, StructureConnection connection) {
        if (level == null || connection == null) return;
        ThreadPoolManager.supplyAsync(ThreadPoolManager.TaskRole.MAP, () -> buildRoadPatch(level, connection))
                .thenAccept(patch -> broadcast(level, patch));
    }

    private static void broadcast(ServerLevel level, MapSnapshotPatch patch) {
        if (level == null || patch == null || patch.isEmpty()) return;
        var server = level.getServer();
        if (server != null) {
            server.execute(() -> ServerMapPatchBridge.broadcast(level, level.dimension().location(), patch));
        } else {
            ServerMapPatchBridge.broadcast(level, level.dimension().location(), patch);
        }
    }

    private static MapSnapshotPatch buildRoadPatch(ServerLevel level, StructureConnection connection) {
        int minX = Math.min(connection.from().getX(), connection.to().getX());
        int maxX = Math.max(connection.from().getX(), connection.to().getX());
        int minZ = Math.min(connection.from().getZ(), connection.to().getZ());
        int maxZ = Math.max(connection.from().getZ(), connection.to().getZ());
        int pad = 256;
        List<RoadData> roads = RoadShardStorage.queryRect(level, minX - pad, minZ - pad, maxX + pad, maxZ + pad);
        long edgeKey = PlanningUtils.edgeKey(connection.from(), connection.to());
        ArrayList<MapSnapshotPatch.RoadPolylinePatch> roadPatches = new ArrayList<>();
        for (RoadData road : roads) {
            if (road == null || !road.hasOwnerPair()) continue;
            long roadEdgeKey = ownerEdgeKey(road.ownerA2dKey(), road.ownerB2dKey());
            if (roadEdgeKey != edgeKey) continue;
            List<BlockPos> polyline = toPolyline(road);
            if (polyline.size() >= 2) {
                roadPatches.add(new MapSnapshotPatch.RoadPolylinePatch(polylineKey(polyline), polyline));
            }
        }
        if (roadPatches.isEmpty()) return MapSnapshotPatch.empty();
        return new MapSnapshotPatch(List.of(), List.of(), roadPatches, List.of());
    }

    private static List<BlockPos> toPolyline(RoadData road) {
        List<RoadSegmentPlacement> segments = road.roadSegmentList();
        if (segments == null || segments.isEmpty()) return List.of();
        ArrayList<BlockPos> out = new ArrayList<>(segments.size());
        for (RoadSegmentPlacement segment : segments) {
            if (segment != null && segment.middlePos() != null) {
                BlockPos pos = segment.middlePos();
                out.add(new BlockPos(pos.getX(), 0, pos.getZ()));
            }
        }
        return out;
    }

    private static StructureConnection normalize(StructureConnection connection) {
        return new StructureConnection(
                normalize(connection.from()),
                normalize(connection.to()),
                connection.status());
    }

    private static BlockPos normalize(BlockPos pos) {
        return new BlockPos(pos.getX(), 0, pos.getZ());
    }

    private static long ownerEdgeKey(long a, long b) {
        long lo = Math.min(a, b);
        long hi = Math.max(a, b);
        return (hi << 1) ^ lo;
    }

    private static long polylineKey(List<BlockPos> road) {
        long hash = 0xcbf29ce484222325L;
        for (BlockPos pos : road) {
            hash ^= pos.getX();
            hash *= 0x100000001b3L;
            hash ^= pos.getZ();
            hash *= 0x100000001b3L;
        }
        hash ^= road.size();
        hash *= 0x100000001b3L;
        return hash;
    }
}