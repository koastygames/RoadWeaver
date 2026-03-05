package net.shiroha233.roadweaver.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
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
}
