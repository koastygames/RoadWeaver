package net.shiroha233.roadweaver.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;
import net.shiroha233.roadweaver.client.map.MapViewportController;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图快照编解码器
 */
public final class MapSnapshotCodec {
    private MapSnapshotCodec() {}

    public static void write(FriendlyByteBuf buf, MapSnapshot s) {
        List<BlockPos> structures = s.structures();
        List<StructureConnection> conns = s.connections();
        
        buf.writeVarInt(structures.size());
        for (BlockPos p : structures) {
            buf.writeBlockPos(p);
        }
        
        for (BlockPos p : structures) {
            String name = s.structureName(p);
            boolean has = name != null;
            buf.writeBoolean(has);
            if (has) {
                buf.writeUtf(name);
            }
        }
        
        buf.writeVarInt(conns.size());
        for (StructureConnection c : conns) {
            buf.writeBlockPos(c.from());
            buf.writeBlockPos(c.to());
            buf.writeVarInt(c.status().ordinal());
        }
        
        List<List<BlockPos>> roads = s.roadPolylines();
        buf.writeVarInt(roads.size());
        for (List<BlockPos> pl : roads) {
            buf.writeVarInt(pl.size());
            for (BlockPos p : pl) {
                buf.writeBlockPos(p);
            }
        }
    }

    public static MapSnapshot read(FriendlyByteBuf buf) {
        int sc = buf.readVarInt();
        List<BlockPos> structures = new ArrayList<>(sc);
        for (int i = 0; i < sc; i++) {
            structures.add(buf.readBlockPos());
        }
        
        List<StructureInfo> infos = new ArrayList<>(sc);
        for (int i = 0; i < sc; i++) {
            boolean has = buf.readBoolean();
            if (has) {
                String id = buf.readUtf();
                infos.add(new StructureInfo(structures.get(i), id));
            }
        }
        
        int cc = buf.readVarInt();
        List<StructureConnection> conns = new ArrayList<>(cc);
        for (int i = 0; i < cc; i++) {
            BlockPos a = buf.readBlockPos();
            BlockPos b = buf.readBlockPos();
            int ord = buf.readVarInt();
            ConnectionStatus st = ConnectionStatus.values()[ord];
            conns.add(new StructureConnection(a, b, st));
        }
        
        int rp = buf.readVarInt();
        List<List<BlockPos>> roads = new ArrayList<>(rp);
        for (int i = 0; i < rp; i++) {
            int pc = buf.readVarInt();
            List<BlockPos> poly = new ArrayList<>(pc);
            for (int j = 0; j < pc; j++) {
                poly.add(buf.readBlockPos());
            }
            roads.add(poly);
        }

        return new MapSnapshot(structures, conns, infos, roads);
    }

    public static void writePatch(FriendlyByteBuf buf, MapSnapshotPatch patch) {
        if (patch == null) patch = MapSnapshotPatch.empty();

        buf.writeVarInt(patch.structures().size());
        for (StructureInfo info : patch.structures()) {
            buf.writeBlockPos(info.pos());
            String id = info.structureId();
            buf.writeBoolean(id != null);
            if (id != null) buf.writeUtf(id);
        }

        buf.writeVarInt(patch.connections().size());
        for (StructureConnection connection : patch.connections()) {
            buf.writeBlockPos(connection.from());
            buf.writeBlockPos(connection.to());
            buf.writeVarInt(connection.status().ordinal());
        }

        buf.writeVarInt(patch.roads().size());
        for (MapSnapshotPatch.RoadPolylinePatch road : patch.roads()) {
            buf.writeLong(road.key());
            buf.writeVarInt(road.points().size());
            for (BlockPos pos : road.points()) {
                buf.writeBlockPos(pos);
            }
        }

        buf.writeVarInt(patch.loadedRects().size());
        for (MapSnapshotPatch.LoadedRect loadedRect : patch.loadedRects()) {
            buf.writeUtf(loadedRect.phase().name());
            MapViewportController.RequestRect rect = loadedRect.rect();
            buf.writeVarInt(rect.minX());
            buf.writeVarInt(rect.minZ());
            buf.writeVarInt(rect.maxX());
            buf.writeVarInt(rect.maxZ());
        }
    }

    public static MapSnapshotPatch readPatch(FriendlyByteBuf buf) {
        int sc = buf.readVarInt();
        List<StructureInfo> structures = new ArrayList<>(sc);
        for (int i = 0; i < sc; i++) {
            BlockPos pos = buf.readBlockPos();
            boolean hasId = buf.readBoolean();
            String id = hasId ? buf.readUtf() : "unknown";
            structures.add(new StructureInfo(pos, id));
        }

        int cc = buf.readVarInt();
        List<StructureConnection> connections = new ArrayList<>(cc);
        for (int i = 0; i < cc; i++) {
            BlockPos from = buf.readBlockPos();
            BlockPos to = buf.readBlockPos();
            ConnectionStatus status = ConnectionStatus.values()[buf.readVarInt()];
            connections.add(new StructureConnection(from, to, status));
        }

        int rc = buf.readVarInt();
        List<MapSnapshotPatch.RoadPolylinePatch> roads = new ArrayList<>(rc);
        for (int i = 0; i < rc; i++) {
            long key = buf.readLong();
            int pc = buf.readVarInt();
            ArrayList<BlockPos> points = new ArrayList<>(pc);
            for (int j = 0; j < pc; j++) {
                points.add(buf.readBlockPos());
            }
            roads.add(new MapSnapshotPatch.RoadPolylinePatch(key, points));
        }

        int lc = buf.readVarInt();
        List<MapSnapshotPatch.LoadedRect> loadedRects = new ArrayList<>(lc);
        for (int i = 0; i < lc; i++) {
            MapLoadPhase phase = MapLoadPhase.valueOf(buf.readUtf());
            int minX = buf.readVarInt();
            int minZ = buf.readVarInt();
            int maxX = buf.readVarInt();
            int maxZ = buf.readVarInt();
            loadedRects.add(new MapSnapshotPatch.LoadedRect(
                    phase,
                    new MapViewportController.RequestRect(minX, minZ, maxX, maxZ)));
        }

        return new MapSnapshotPatch(structures, connections, roads, loadedRects);
    }
}
