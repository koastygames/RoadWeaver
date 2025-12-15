package net.shiroha233.roadweaver.network.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.helpers.LevelCompat;
import net.shiroha233.roadweaver.network.MapSnapshotCodec;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.util.ComputeService;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class MapNetworkForge {
    public static final ResourceLocation REQ_RECT_ID = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_request_rect");
    public static final ResourceLocation SNAP_ID = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_snapshot");
    public static final ResourceLocation TP_REQ_ID = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_teleport");
    public static final ResourceLocation TP_ACK_ID = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_teleport_ack");
    public static final ResourceLocation MAN_REQ_ID = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_manual_connect");

    public static void register(IEventBus modBus) {
        modBus.addListener(MapNetworkForge::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        
        registrar.playToClient(MapSnapshotS2C.TYPE, MapSnapshotS2C.CODEC, MapSnapshotS2C::handle);
        registrar.playToClient(TeleportAckS2C.TYPE, TeleportAckS2C.CODEC, TeleportAckS2C::handle);
        
        registrar.playToServer(RequestMapSnapshotC2S.TYPE, RequestMapSnapshotC2S.CODEC, RequestMapSnapshotC2S::handle);
        registrar.playToServer(TeleportC2S.TYPE, TeleportC2S.CODEC, TeleportC2S::handle);
        registrar.playToServer(ManualConnectC2S.TYPE, ManualConnectC2S.CODEC, ManualConnectC2S::handle);
    }

    public static void requestSnapshot(int minX, int minZ, int maxX, int maxZ) {
        sendToServer(new RequestMapSnapshotC2S(minX, minZ, maxX, maxZ));
    }

    public static void requestTeleport(int x, int y, int z) {
        sendToServer(new TeleportC2S(x, y, z));
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        sendToServer(new ManualConnectC2S(ax, az, bx, bz));
    }

    private static void sendToServer(CustomPacketPayload payload) {
        try {
            var m = PacketDistributor.class.getMethod("sendToServer", CustomPacketPayload.class);
            m.invoke(null, payload);
            return;
        } catch (Throwable ignored) {
        }

        try {
            // Newer PacketDistributor APIs may expose a SERVER target with a noArg() distributor.
            var serverField = PacketDistributor.class.getField("SERVER");
            Object server = serverField.get(null);
            var noArg = server.getClass().getMethod("noArg");
            Object distributor = noArg.invoke(server);
            var send = distributor.getClass().getMethod("send", CustomPacketPayload.class);
            send.invoke(distributor, payload);
        } catch (Throwable t) {
            RoadWeaver.getLogger().warn("Failed to send payload to server: {}", payload.type().id(), t);
        }
    }

    // Payloads

    public record RequestMapSnapshotC2S(int minX, int minZ, int maxX, int maxZ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RequestMapSnapshotC2S> TYPE = new CustomPacketPayload.Type<>(REQ_RECT_ID);
        public static final StreamCodec<FriendlyByteBuf, RequestMapSnapshotC2S> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeVarInt(v.minX); buf.writeVarInt(v.minZ); buf.writeVarInt(v.maxX); buf.writeVarInt(v.maxZ); },
            buf -> new RequestMapSnapshotC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        
        public static void handle(RequestMapSnapshotC2S payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
            ServerPlayer sp = (ServerPlayer) context.player();
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
                .supplyAsync(() -> MapDataCollector.build(LevelCompat.getServerLevel(sp), payload.minX, payload.minZ, payload.maxX, payload.maxZ, cx, cz, radiusBlocks), ComputeService.executor())
                .thenAccept(snapshot -> context.enqueueWork(() -> PacketDistributor.sendToPlayer(sp, new MapSnapshotS2C(snapshot))));
        }
    }

    public record MapSnapshotS2C(MapSnapshot snapshot) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MapSnapshotS2C> TYPE = new CustomPacketPayload.Type<>(SNAP_ID);
        public static final StreamCodec<FriendlyByteBuf, MapSnapshotS2C> CODEC = StreamCodec.of(
            (buf, v) -> MapSnapshotCodec.write(buf, v.snapshot),
            buf -> new MapSnapshotS2C(MapSnapshotCodec.read(buf))
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        
        public static void handle(MapSnapshotS2C payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
            context.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof RoadMapScreen screen) {
                    screen.setSnapshot(payload.snapshot);
                }
            });
        }
    }

    public record TeleportC2S(int x, int y, int z) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TeleportC2S> TYPE = new CustomPacketPayload.Type<>(TP_REQ_ID);
        public static final StreamCodec<FriendlyByteBuf, TeleportC2S> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeVarInt(v.x); buf.writeVarInt(v.y); buf.writeVarInt(v.z); },
            buf -> new TeleportC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        
        public static void handle(TeleportC2S payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer sp = (ServerPlayer) context.player();
                boolean allowed = sp.isCreative() || sp.hasPermissions(2);
                if (!allowed) {
                    PacketDistributor.sendToPlayer(sp, new TeleportAckS2C(false, 0, 0, 0));
                    return;
                }
                ServerLevel level = LevelCompat.getServerLevel(sp);
                level.getChunk(payload.x >> 4, payload.z >> 4);
                int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, payload.x, payload.z);
                if (ty <= level.getMinY()) ty = level.getSeaLevel() + 1; else ty += 1;
                boolean okTp = LevelCompat.teleport(sp, level, payload.x + 0.5, ty, payload.z + 0.5, sp.getYRot(), sp.getXRot());
                PacketDistributor.sendToPlayer(sp, new TeleportAckS2C(okTp, payload.x, ty, payload.z));
            });
        }
    }

    public record TeleportAckS2C(boolean ok, int x, int y, int z) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<TeleportAckS2C> TYPE = new CustomPacketPayload.Type<>(TP_ACK_ID);
        public static final StreamCodec<FriendlyByteBuf, TeleportAckS2C> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeBoolean(v.ok); buf.writeVarInt(v.x); buf.writeVarInt(v.y); buf.writeVarInt(v.z); },
            buf -> new TeleportAckS2C(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        
        public static void handle(TeleportAckS2C payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
            context.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;
                if (payload.ok) mc.player.displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.success_pos", payload.x, payload.y, payload.z), true);
                else mc.player.displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.denied"), true);
            });
        }
    }

    public record ManualConnectC2S(int ax, int az, int bx, int bz) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ManualConnectC2S> TYPE = new CustomPacketPayload.Type<>(MAN_REQ_ID);
        public static final StreamCodec<FriendlyByteBuf, ManualConnectC2S> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeVarInt(v.ax); buf.writeVarInt(v.az); buf.writeVarInt(v.bx); buf.writeVarInt(v.bz); },
            buf -> new ManualConnectC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        
        public static void handle(ManualConnectC2S payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
            context.enqueueWork(() -> {
                ServerPlayer sp = (ServerPlayer) context.player();
                ServerLevel level = LevelCompat.getServerLevel(sp);
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
        }
    }
}
