package net.shiroha233.roadweaver.network.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client26.RoadMapScreen26;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.helpers.PermissionCompat;
import net.shiroha233.roadweaver.map.permission.MapAccessService;
import net.shiroha233.roadweaver.network.MapSnapshotCodec;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Fabric networking implementation for the native Minecraft 26.2 RoadWeaver map UI. */
public final class MapNetworkFabric {
    private static boolean payloadTypesRegistered;

    private MapNetworkFabric() {}

    private static synchronized void registerPayloadTypes() {
        if (payloadTypesRegistered) return;
        PayloadTypeRegistry.serverboundPlay().register(RequestRectPayload.TYPE, RequestRectPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TeleportRequestPayload.TYPE, TeleportRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ManualConnectPayload.TYPE, ManualConnectPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SnapshotPayload.TYPE, SnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TeleportAckPayload.TYPE, TeleportAckPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AccessSyncPayload.TYPE, AccessSyncPayload.CODEC);
        payloadTypesRegistered = true;
    }

    public static void registerServerReceivers() {
        registerPayloadTypes();

        ServerPlayNetworking.registerGlobalReceiver(RequestRectPayload.TYPE, (payload, context) -> {
            ServerPlayer sp = context.player();
            if (!MapAccessService.canOpenMap(sp)) {
                context.server().execute(() -> syncMapAccess(sp));
                return;
            }
            int cx = (int)Math.round(sp.getX());
            int cz = (int)Math.round(sp.getZ());
            int computedRadiusBlocks;
            try {
                ModConfig cfg = ConfigService.get();
                if (cfg.highway().enabled()) computedRadiusBlocks = Math.max(16, cfg.highway().planningRadiusBlocks());
                else {
                    int radiusChunks = cfg.planning().dynamicPlanEnabled()
                            ? cfg.planning().dynamicPlanRadiusChunks()
                            : cfg.planning().initialPlanRadiusChunks();
                    computedRadiusBlocks = Math.max(1, radiusChunks) * 16;
                }
            } catch (Throwable ignored) {
                computedRadiusBlocks = 4096;
            }
            final int radiusBlocks = Math.max(16, computedRadiusBlocks);

            CompletableFuture.supplyAsync(() -> {
                var level = sp.level();
                Identifier actualDimensionId = level.dimension().identifier();
                MapSnapshot snapshot = MapDataCollector.build(level, payload.minX(), payload.minZ(), payload.maxX(), payload.maxZ(), cx, cz, radiusBlocks);
                return new SnapshotPayload(payload.requestSeq(), actualDimensionId, snapshot);
            }, ThreadPoolManager.computeExecutor()).thenAccept(reply -> context.server().execute(() -> {
                if (!sp.isRemoved() && !sp.hasDisconnected()) ServerPlayNetworking.send(sp, reply);
            }));
        });

        ServerPlayNetworking.registerGlobalReceiver(TeleportRequestPayload.TYPE, (payload, context) -> context.server().execute(() -> {
            ServerPlayer sp = context.player();
            if (!(sp.isCreative() || PermissionCompat.hasCommandLevel2(sp))) {
                ServerPlayNetworking.send(sp, new TeleportAckPayload(false, 0, 0, 0));
                return;
            }
            int x = payload.x();
            int z = payload.z();
            var level = sp.level();
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinY()) y = level.getSeaLevel() + 1; else y += 1;
            sp.teleportTo(level, x + 0.5D, y, z + 0.5D, java.util.Set.of(), sp.getYRot(), sp.getXRot(), false);
            ServerPlayNetworking.send(sp, new TeleportAckPayload(true, x, y, z));
        }));

        ServerPlayNetworking.registerGlobalReceiver(ManualConnectPayload.TYPE, (payload, context) -> context.server().execute(() -> {
            ServerPlayer sp = context.player();
            if (!PermissionCompat.hasCommandLevel2(sp)) return;
            var level = sp.level();
            WorldDataProvider provider = WorldDataProvider.getInstance();
            List<StructureConnection> origin = provider.getStructureConnections(level);
            List<StructureConnection> list = origin != null ? new ArrayList<>(origin) : new ArrayList<>();
            BlockPos a = new BlockPos(payload.ax(), 0, payload.az());
            BlockPos b = new BlockPos(payload.bx(), 0, payload.bz());
            boolean exists = list.stream().anyMatch(c -> (c.from().equals(a) && c.to().equals(b)) || (c.from().equals(b) && c.to().equals(a)));
            if (!exists) {
                list.add(new StructureConnection(a, b, ConnectionStatus.PLANNED));
                provider.setStructureConnections(level, list);
            }
        }));
    }

    public static void registerClientReceivers() {
        registerPayloadTypes();
        ClientPlayNetworking.registerGlobalReceiver(SnapshotPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            if (context.client().gui.screen() instanceof RoadMapScreen26 screen) {
                screen.acceptSnapshot(payload.requestSeq(), payload.dimensionId(), payload.snapshot());
            }
        }));
        ClientPlayNetworking.registerGlobalReceiver(TeleportAckPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            Component message = payload.ok()
                    ? Component.translatable("gui.roadweaver.map.teleport.success_pos", payload.x(), payload.y(), payload.z())
                    : Component.translatable("gui.roadweaver.map.teleport.denied");
            context.client().gui.hud.setOverlayMessage(message, false);
        }));
        ClientPlayNetworking.registerGlobalReceiver(AccessSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> RoadMapScreen26.applyAccessAllowed(payload.allowed())));
    }

    public static void requestSnapshot(int requestSeq, Identifier dimensionId, int minX, int minZ, int maxX, int maxZ) {
        ClientPlayNetworking.send(new RequestRectPayload(requestSeq, dimensionId, minX, minZ, maxX, maxZ));
    }
    public static void requestTeleport(int x, int y, int z) { ClientPlayNetworking.send(new TeleportRequestPayload(x, y, z)); }
    public static void requestManualConnect(int ax, int az, int bx, int bz) { ClientPlayNetworking.send(new ManualConnectPayload(ax, az, bx, bz)); }
    public static void syncMapAccess(ServerPlayer player) {
        if (player != null) ServerPlayNetworking.send(player, new AccessSyncPayload(MapAccessService.canOpenMap(player)));
    }

    private record RequestRectPayload(int requestSeq, Identifier dimensionId, int minX, int minZ, int maxX, int maxZ) implements CustomPacketPayload {
        private static final Type<RequestRectPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("roadweaver", "map_request_rect"));
        private static final StreamCodec<RegistryFriendlyByteBuf, RequestRectPayload> CODEC = CustomPacketPayload.codec(
                (p, b) -> { b.writeVarInt(p.requestSeq); b.writeIdentifier(p.dimensionId); b.writeVarInt(p.minX); b.writeVarInt(p.minZ); b.writeVarInt(p.maxX); b.writeVarInt(p.maxZ); },
                b -> new RequestRectPayload(b.readVarInt(), b.readIdentifier(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private record SnapshotPayload(int requestSeq, Identifier dimensionId, MapSnapshot snapshot) implements CustomPacketPayload {
        private static final Type<SnapshotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("roadweaver", "map_snapshot"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPayload> CODEC = CustomPacketPayload.codec(
                (p, b) -> { b.writeVarInt(p.requestSeq); b.writeIdentifier(p.dimensionId); MapSnapshotCodec.write(b, p.snapshot); },
                b -> new SnapshotPayload(b.readVarInt(), b.readIdentifier(), MapSnapshotCodec.read(b)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private record TeleportRequestPayload(int x, int y, int z) implements CustomPacketPayload {
        private static final Type<TeleportRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("roadweaver", "map_teleport"));
        private static final StreamCodec<RegistryFriendlyByteBuf, TeleportRequestPayload> CODEC = CustomPacketPayload.codec(
                (p, b) -> { b.writeVarInt(p.x); b.writeVarInt(p.y); b.writeVarInt(p.z); },
                b -> new TeleportRequestPayload(b.readVarInt(), b.readVarInt(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private record TeleportAckPayload(boolean ok, int x, int y, int z) implements CustomPacketPayload {
        private static final Type<TeleportAckPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("roadweaver", "map_teleport_ack"));
        private static final StreamCodec<RegistryFriendlyByteBuf, TeleportAckPayload> CODEC = CustomPacketPayload.codec(
                (p, b) -> { b.writeBoolean(p.ok); b.writeVarInt(p.x); b.writeVarInt(p.y); b.writeVarInt(p.z); },
                b -> new TeleportAckPayload(b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private record ManualConnectPayload(int ax, int az, int bx, int bz) implements CustomPacketPayload {
        private static final Type<ManualConnectPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("roadweaver", "map_manual_connect"));
        private static final StreamCodec<RegistryFriendlyByteBuf, ManualConnectPayload> CODEC = CustomPacketPayload.codec(
                (p, b) -> { b.writeVarInt(p.ax); b.writeVarInt(p.az); b.writeVarInt(p.bx); b.writeVarInt(p.bz); },
                b -> new ManualConnectPayload(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private record AccessSyncPayload(boolean allowed) implements CustomPacketPayload {
        private static final Type<AccessSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("roadweaver", "map_access_sync"));
        private static final StreamCodec<RegistryFriendlyByteBuf, AccessSyncPayload> CODEC = CustomPacketPayload.codec(
                (p, b) -> b.writeBoolean(p.allowed), b -> new AccessSyncPayload(b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
