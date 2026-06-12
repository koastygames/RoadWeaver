package net.shiroha233.roadweaver.network.fabric;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.map.permission.MapAccessService;
import net.shiroha233.roadweaver.network.MapRequestBounds;
import net.shiroha233.roadweaver.network.MapSnapshotCodec;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Fabric 平台网络通信实现
 */
public class MapNetworkFabric {
    public static final ResourceLocation REQ_RECT = new ResourceLocation("roadweaver", "map_request_rect");
    public static final ResourceLocation SNAP = new ResourceLocation("roadweaver", "map_snapshot");
    public static final ResourceLocation TP_REQ = new ResourceLocation("roadweaver", "map_teleport");
    public static final ResourceLocation TP_ACK = new ResourceLocation("roadweaver", "map_teleport_ack");
    public static final ResourceLocation MAN_REQ = new ResourceLocation("roadweaver", "map_manual_connect");
    public static final ResourceLocation ACCESS_SYNC = new ResourceLocation("roadweaver", "map_access_sync");

    public static void registerServerReceivers() {
        // 地图矩形范围请求
        ServerPlayNetworking.registerGlobalReceiver(REQ_RECT, (server, player, handler, buf, responseSender) -> {
            int requestSeq = buf.readVarInt();
            buf.readResourceLocation(); // 客户端维度 ID 不可信，仅消费缓冲区
            int minX = buf.readVarInt();
            int minZ = buf.readVarInt();
            int maxX = buf.readVarInt();
            int maxZ = buf.readVarInt();
            
            ServerPlayer sp = player;
            if (!MapAccessService.canOpenMap(sp)) {
                server.execute(() -> syncMapAccess(sp));
                return;
            }
            int cx = (int) Math.round(sp.getX());
            int cz = (int) Math.round(sp.getZ());
            
            CompletableFuture
                .supplyAsync(() -> {
                    var level = sp.serverLevel();
                    ResourceLocation actualDimensionId = level.dimension().location();
                    MapRequestBounds.Rect rect = MapRequestBounds.clampToPlayer(sp, minX, minZ, maxX, maxZ);
                    MapSnapshot snapshot = MapDataCollector.build(level, rect.minX(), rect.minZ(), rect.maxX(), rect.maxZ(), cx, cz, rect.radiusBlocks());
                    FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
                    out.writeVarInt(requestSeq);
                    out.writeResourceLocation(actualDimensionId);
                    MapSnapshotCodec.write(out, snapshot);
                    return out;
                }, ThreadPoolManager.computeExecutor())
                .thenAccept(out -> server.execute(() -> ServerPlayNetworking.send(sp, SNAP, out)));
        });

        // 传送请求
        ServerPlayNetworking.registerGlobalReceiver(TP_REQ, (server, player, handler, buf, responseSender) -> {
            int x = buf.readVarInt();
            buf.readVarInt(); // y 坐标不使用
            int z = buf.readVarInt();
            
            server.execute(() -> {
                ServerPlayer sp = player;
                boolean allowed = sp.isCreative() || sp.hasPermissions(2);
                if (!allowed) {
                    FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
                    out.writeBoolean(false);
                    ServerPlayNetworking.send(sp, TP_ACK, out);
                    return;
                }
                
                var level = sp.serverLevel();
                level.getChunk(x >> 4, z >> 4);
                int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (ty <= level.getMinBuildHeight()) ty = level.getSeaLevel() + 1; 
                else ty += 1;
                
                sp.teleportTo(level, x + 0.5, ty, z + 0.5, sp.getYRot(), sp.getXRot());
                
                FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
                out.writeBoolean(true);
                out.writeVarInt(x);
                out.writeVarInt(ty);
                out.writeVarInt(z);
                ServerPlayNetworking.send(sp, TP_ACK, out);
            });
        });

        // 手动连接请求
        ServerPlayNetworking.registerGlobalReceiver(MAN_REQ, (server, player, handler, buf, responseSender) -> {
            int ax = buf.readVarInt();
            int az = buf.readVarInt();
            int bx = buf.readVarInt();
            int bz = buf.readVarInt();
            
            server.execute(() -> {
                ServerPlayer sp = player;
                if (sp == null) return;

                boolean allowed = sp.hasPermissions(2);
                if (!allowed) {
                    sp.displayClientMessage(Component.translatable("gui.roadweaver.map.manual_connect.denied"), true);
                    return;
                }

                var level = sp.serverLevel();
                WorldDataProvider provider = WorldDataProvider.getInstance();
                List<StructureConnection> origin = provider.getStructureConnections(level);
                List<StructureConnection> list = origin != null ? new ArrayList<>(origin) : new ArrayList<>();
                
                BlockPos a = new BlockPos(ax, 0, az);
                BlockPos b = new BlockPos(bx, 0, bz);
                
                boolean exists = false;
                for (StructureConnection c : list) {
                    BlockPos f = c.from();
                    BlockPos t = c.to();
                    if ((f.equals(a) && t.equals(b)) || (f.equals(b) && t.equals(a))) { 
                        exists = true; 
                        break; 
                    }
                }
                
                if (!exists) {
                    list.add(new StructureConnection(a, b, ConnectionStatus.PLANNED));
                    provider.setStructureConnections(level, list);
                }
            });
        });
    }

    public static void registerClientReceivers() {
        // 地图快照接收
        ClientPlayNetworking.registerGlobalReceiver(SNAP, (client, handler, buf, responseSender) -> {
            int requestSeq = buf.readVarInt();
            ResourceLocation dimensionId = buf.readResourceLocation();
            MapSnapshot s = MapSnapshotCodec.read(buf);
            
            client.execute(() -> {
                if (client.screen instanceof RoadMapScreen screen) {
                    screen.acceptSnapshot(requestSeq, dimensionId, s);
                }
            });
        });
        
        // 传送确认接收
        ClientPlayNetworking.registerGlobalReceiver(TP_ACK, (client, handler, buf, responseSender) -> {
            boolean ok = buf.readBoolean();
            int rx = 0, ry = 0, rz = 0;
            if (ok) {
                rx = buf.readVarInt();
                ry = buf.readVarInt();
                rz = buf.readVarInt();
            }
            
            int fx = rx, fy = ry, fz = rz;
            client.execute(() -> {
                if (client.player == null) return;
                if (ok) {
                    client.player.displayClientMessage(
                        Component.translatable("gui.roadweaver.map.teleport.success_pos", fx, fy, fz), true);
                } else {
                    client.player.displayClientMessage(
                        Component.translatable("gui.roadweaver.map.teleport.denied"), true);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ACCESS_SYNC, (client, handler, buf, responseSender) -> {
            boolean allowed = buf.readBoolean();
            client.execute(() -> ClientMapAccessGuard.applyServerState(client, allowed));
        });
    }

    public static void requestSnapshot(int requestSeq, ResourceLocation dimensionId, int minX, int minZ, int maxX, int maxZ) {
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        out.writeVarInt(requestSeq);
        out.writeResourceLocation(dimensionId);
        out.writeVarInt(minX);
        out.writeVarInt(minZ);
        out.writeVarInt(maxX);
        out.writeVarInt(maxZ);
        ClientPlayNetworking.send(REQ_RECT, out);
    }

    public static void requestTeleport(int x, int y, int z) {
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        out.writeVarInt(x);
        out.writeVarInt(y);
        out.writeVarInt(z);
        ClientPlayNetworking.send(TP_REQ, out);
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        out.writeVarInt(ax);
        out.writeVarInt(az);
        out.writeVarInt(bx);
        out.writeVarInt(bz);
        ClientPlayNetworking.send(MAN_REQ, out);
    }

    public static void syncMapAccess(ServerPlayer player) {
        if (player == null) {
            return;
        }
        FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
        out.writeBoolean(MapAccessService.canOpenMap(player));
        ServerPlayNetworking.send(player, ACCESS_SYNC, out);
    }
}
