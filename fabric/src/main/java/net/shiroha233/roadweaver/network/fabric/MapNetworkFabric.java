package net.shiroha233.roadweaver.network.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.network.MapSnapshotCodec;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.util.ComputeService;

import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;

public class MapNetworkFabric {
    public static final ResourceLocation REQ_RECT_ID = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_request_rect");
    public static final ResourceLocation SNAP_ID = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_snapshot");
    public static final ResourceLocation TP_REQ_ID = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_teleport");
    public static final ResourceLocation TP_ACK_ID = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_teleport_ack");
    public static final ResourceLocation MAN_REQ_ID = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_manual_connect");

    public static void register() {
        PayloadTypeRegistry.playC2S().register(RequestMapSnapshotC2S.TYPE, RequestMapSnapshotC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(MapSnapshotS2C.TYPE, MapSnapshotS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(TeleportC2S.TYPE, TeleportC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(TeleportAckS2C.TYPE, TeleportAckS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(ManualConnectC2S.TYPE, ManualConnectC2S.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(RequestMapSnapshotC2S.TYPE, (payload, context) -> {
            ServerPlayer sp = context.player();
            int minX = payload.minX;
            int minZ = payload.minZ;
            int maxX = payload.maxX;
            int maxZ = payload.maxZ;
            int cx = (int) Math.round(sp.getX());
            int cz = (int) Math.round(sp.getZ());
            int radiusChunks;
            try {
                net.shiroha233.roadweaver.config.ModConfig cfg = net.shiroha233.roadweaver.config.ConfigService.get();
                radiusChunks = (cfg.dynamicPlanEnabled() ? cfg.dynamicPlanRadiusChunks() : cfg.initialPlanRadiusChunks());
            } catch (Throwable t) {
                radiusChunks = 256;
            }
            int radiusBlocks = Math.max(1, radiusChunks) * 16;
            CompletableFuture
                .supplyAsync(() -> MapDataCollector.build(sp.serverLevel(), minX, minZ, maxX, maxZ, cx, cz, radiusBlocks), ComputeService.executor())
                .thenAccept(snapshot -> context.server().execute(() -> ServerPlayNetworking.send(sp, new MapSnapshotS2C(snapshot))));
        });

        ServerPlayNetworking.registerGlobalReceiver(TeleportC2S.TYPE, (payload, context) -> {
            ServerPlayer sp = context.player();
            context.server().execute(() -> {
                boolean allowed = sp.isCreative() || sp.hasPermissions(2);
                if (!allowed) {
                    ServerPlayNetworking.send(sp, new TeleportAckS2C(false, 0, 0, 0));
                    return;
                }
                var level = sp.serverLevel();
                int x = payload.x;
                int z = payload.z;
                level.getChunk(x >> 4, z >> 4);
                int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (ty <= level.getMinBuildHeight()) ty = level.getSeaLevel() + 1; else ty += 1;
                sp.teleportTo(level, x + 0.5, ty, z + 0.5, sp.getYRot(), sp.getXRot());
                ServerPlayNetworking.send(sp, new TeleportAckS2C(true, x, ty, z));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ManualConnectC2S.TYPE, (payload, context) -> {
            ServerPlayer sp = context.player();
            context.server().execute(() -> {
                var level = sp.serverLevel();
                WorldDataProvider provider = WorldDataProvider.getInstance();
                java.util.List<Records.StructureConnection> origin = provider.getStructureConnections(level);
                java.util.List<Records.StructureConnection> list = origin != null ? new ArrayList<>(origin) : new ArrayList<>();
                BlockPos a = new BlockPos(payload.ax, 0, payload.az);
                BlockPos b = new BlockPos(payload.bx, 0, payload.bz);
                boolean exists = false;
                for (Records.StructureConnection c : list) {
                    BlockPos f = c.from();
                    BlockPos t = c.to();
                    if ((f.equals(a) && t.equals(b)) || (f.equals(b) && t.equals(a))) { exists = true; break; }
                }
                if (!exists) {
                    list.add(new Records.StructureConnection(a, b, Records.ConnectionStatus.PLANNED));
                    provider.setStructureConnections(level, list);
                }
            });
        });
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(MapSnapshotS2C.TYPE, (payload, context) -> {
            MapSnapshot s = payload.snapshot;
            context.client().execute(() -> {
                if (context.client().screen instanceof RoadMapScreen screen) {
                    screen.setSnapshot(s);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TeleportAckS2C.TYPE, (payload, context) -> {
            boolean ok = payload.ok;
            int fx = payload.x;
            int fy = payload.y;
            int fz = payload.z;
            context.client().execute(() -> {
                if (context.client().player == null) return;
                if (ok) context.client().player.displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.success_pos", fx, fy, fz), true);
                else context.client().player.displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.denied"), true);
            });
        });
    }

    public static void requestSnapshot(int minX, int minZ, int maxX, int maxZ) {
        ClientPlayNetworking.send(new RequestMapSnapshotC2S(minX, minZ, maxX, maxZ));
    }

    public static void requestTeleport(int x, int y, int z) {
        ClientPlayNetworking.send(new TeleportC2S(x, y, z));
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        ClientPlayNetworking.send(new ManualConnectC2S(ax, az, bx, bz));
    }

    // Payloads

    public record RequestMapSnapshotC2S(int minX, int minZ, int maxX, int maxZ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RequestMapSnapshotC2S> TYPE = new CustomPacketPayload.Type<>(REQ_RECT_ID);
        public static final StreamCodec<FriendlyByteBuf, RequestMapSnapshotC2S> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeVarInt(v.minX); buf.writeVarInt(v.minZ); buf.writeVarInt(v.maxX); buf.writeVarInt(v.maxZ); },
            buf -> new RequestMapSnapshotC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record MapSnapshotS2C(MapSnapshot snapshot) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MapSnapshotS2C> TYPE = new CustomPacketPayload.Type<>(SNAP_ID);
        public static final StreamCodec<FriendlyByteBuf, MapSnapshotS2C> CODEC = StreamCodec.of(
            (buf, v) -> MapSnapshotCodec.write(buf, v.snapshot),
            buf -> new MapSnapshotS2C(MapSnapshotCodec.read(buf))
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TeleportC2S(int x, int y, int z) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TeleportC2S> TYPE = new CustomPacketPayload.Type<>(TP_REQ_ID);
        public static final StreamCodec<FriendlyByteBuf, TeleportC2S> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeVarInt(v.x); buf.writeVarInt(v.y); buf.writeVarInt(v.z); },
            buf -> new TeleportC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TeleportAckS2C(boolean ok, int x, int y, int z) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TeleportAckS2C> TYPE = new CustomPacketPayload.Type<>(TP_ACK_ID);
        public static final StreamCodec<FriendlyByteBuf, TeleportAckS2C> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeBoolean(v.ok); buf.writeVarInt(v.x); buf.writeVarInt(v.y); buf.writeVarInt(v.z); },
            buf -> new TeleportAckS2C(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ManualConnectC2S(int ax, int az, int bx, int bz) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ManualConnectC2S> TYPE = new CustomPacketPayload.Type<>(MAN_REQ_ID);
        public static final StreamCodec<FriendlyByteBuf, ManualConnectC2S> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeVarInt(v.ax); buf.writeVarInt(v.az); buf.writeVarInt(v.bx); buf.writeVarInt(v.bz); },
            buf -> new ManualConnectC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
