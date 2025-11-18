package net.shiroha233.roadweaver.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.shiroha233.roadweaver.client.map.MapSnapshot;
import net.shiroha233.roadweaver.helpers.Records;

import java.util.ArrayList;
import java.util.List;

public final class MapSnapshotCodec {
    private MapSnapshotCodec() {}

    public static void write(FriendlyByteBuf buf, MapSnapshot s) {
        List<BlockPos> structures = s.structures();
        List<Records.StructureConnection> conns = s.connections();
        buf.writeVarInt(structures.size());
        for (BlockPos p : structures) buf.writeBlockPos(p);
        for (BlockPos p : structures) {
            String name = s.structureName(p);
            boolean has = name != null;
            buf.writeBoolean(has);
            if (has) buf.writeUtf(name);
        }
        buf.writeVarInt(conns.size());
        for (Records.StructureConnection c : conns) {
            buf.writeBlockPos(c.from());
            buf.writeBlockPos(c.to());
            buf.writeVarInt(c.status().ordinal());
        }
        List<java.util.List<BlockPos>> roads = s.roadPolylines();
        buf.writeVarInt(roads.size());
        for (java.util.List<BlockPos> pl : roads) {
            buf.writeVarInt(pl.size());
            for (BlockPos p : pl) buf.writeBlockPos(p);
        }
    }

    public static MapSnapshot read(FriendlyByteBuf buf) {
        int sc = buf.readVarInt();
        List<BlockPos> structures = new ArrayList<>(sc);
        for (int i = 0; i < sc; i++) structures.add(buf.readBlockPos());
        List<Records.StructureInfo> infos = new ArrayList<>(sc);
        for (int i = 0; i < sc; i++) {
            boolean has = buf.readBoolean();
            if (has) {
                String id = buf.readUtf();
                infos.add(new Records.StructureInfo(structures.get(i), id));
            }
        }
        int cc = buf.readVarInt();
        List<Records.StructureConnection> conns = new ArrayList<>(cc);
        for (int i = 0; i < cc; i++) {
            BlockPos a = buf.readBlockPos();
            BlockPos b = buf.readBlockPos();
            int ord = buf.readVarInt();
            Records.ConnectionStatus st = Records.ConnectionStatus.values()[ord];
            conns.add(new Records.StructureConnection(a, b, st));
        }
        int rp = buf.readVarInt();
        java.util.List<java.util.List<BlockPos>> roads = new ArrayList<>(rp);
        for (int i = 0; i < rp; i++) {
            int pc = buf.readVarInt();
            java.util.List<BlockPos> poly = new ArrayList<>(pc);
            for (int j = 0; j < pc; j++) poly.add(buf.readBlockPos());
            roads.add(poly);
        }
        return new MapSnapshot(structures, conns, infos, roads);
    }
}
