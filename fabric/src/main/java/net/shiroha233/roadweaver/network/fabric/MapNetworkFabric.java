package net.shiroha233.roadweaver.network.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
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
    private static boolean payloadTypesRegistered = false;

    private static synchronized void registerPayloadTypes() {
        if (payloadTypesRegistered) {
            return;
        }

        PayloadTypeRegistry.playC2S().register(RequestRectPayload.TYPE, RequestRectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TeleportRequestPayload.TYPE, TeleportRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ManualConnectPayload.TYPE, ManualConnectPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SnapshotPayload.TYPE, SnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TeleportAckPayload.TYPE, TeleportAckPayload.CODEC);

        payloadTypesRegistered = true;
    }

    public static void registerServerReceivers() {
        registerPayloadTypes();

        ServerPlayNetworking.registerGlobalReceiver(RequestRectPayload.TYPE, (payload, context) -> {
            ServerPlayer sp = context.player();
            int cx = (int) Math.round(sp.getX());
            int cz = (int) Math.round(sp.getZ());

            int computedRadiusBlocks;
            try {
                ModConfig cfg = ConfigService.get();
                if (cfg.highway().enabled()) {
                    computedRadiusBlocks = Math.max(16, cfg.highway().planningRadiusBlocks());
                } else {
                    int radiusChunks = cfg.planning().dynamicPlanEnabled()
                            ? cfg.planning().dynamicPlanRadiusChunks()
                            : cfg.planning().initialPlanRadiusChunks();
                    computedRadiusBlocks = Math.max(1, radiusChunks) * 16;
                }
            } catch (Throwable t) {
                computedRadiusBlocks = 256 * 16;
            }

            final int radiusBlocksFinal = Math.max(16, computedRadiusBlocks);

            CompletableFuture
                    .supplyAsync(() -> {
                        var level = sp.serverLevel();
                        ResourceLocation actualDimensionId = level.dimension().location();
                        MapSnapshot snapshot = MapDataCollector.build(
                                level,
                                payload.minX(),
                                payload.minZ(),
                                payload.maxX(),
                                payload.maxZ(),
                                cx,
                                cz,
                                radiusBlocksFinal
                        );
                        return new SnapshotPayload(payload.requestSeq(), actualDimensionId, snapshot);
                    }, ThreadPoolManager.computeExecutor())
                    .thenAccept(reply -> context.server().execute(() -> ServerPlayNetworking.send(sp, reply)));
        });

        ServerPlayNetworking.registerGlobalReceiver(TeleportRequestPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer sp = context.player();
                boolean allowed = sp.isCreative() || sp.hasPermissions(2);
                if (!allowed) {
                    ServerPlayNetworking.send(sp, new TeleportAckPayload(false, 0, 0, 0));
                    return;
                }

                int x = payload.x();
                int z = payload.z();
                var level = sp.serverLevel();
                level.getChunk(x >> 4, z >> 4);
                int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (ty <= level.getMinBuildHeight()) {
                    ty = level.getSeaLevel() + 1;
                } else {
                    ty += 1;
                }

                sp.teleportTo(level, x + 0.5, ty, z + 0.5, sp.getYRot(), sp.getXRot());
                ServerPlayNetworking.send(sp, new TeleportAckPayload(true, x, ty, z));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ManualConnectPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer sp = context.player();
                boolean allowed = sp.hasPermissions(2);
                if (!allowed) {
                    sp.displayClientMessage(Component.translatable("gui.roadweaver.map.manual_connect.denied"), true);
                    return;
                }

                var level = sp.serverLevel();
                WorldDataProvider provider = WorldDataProvider.getInstance();
                List<StructureConnection> origin = provider.getStructureConnections(level);
                List<StructureConnection> list = origin != null ? new ArrayList<>(origin) : new ArrayList<>();

                BlockPos a = new BlockPos(payload.ax(), 0, payload.az());
                BlockPos b = new BlockPos(payload.bx(), 0, payload.bz());

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
        registerPayloadTypes();

        ClientPlayNetworking.registerGlobalReceiver(SnapshotPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof RoadMapScreen screen) {
                        screen.acceptSnapshot(payload.requestSeq(), payload.dimensionId(), payload.snapshot());
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(TeleportAckPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.player() == null) {
                        return;
                    }
                    if (payload.ok()) {
                        context.player().displayClientMessage(
                                Component.translatable("gui.roadweaver.map.teleport.success_pos", payload.x(), payload.y(), payload.z()),
                                true
                        );
                    } else {
                        context.player().displayClientMessage(
                                Component.translatable("gui.roadweaver.map.teleport.denied"),
                                true
                        );
                    }
                })
        );
    }

    public static void requestSnapshot(int requestSeq, ResourceLocation dimensionId, int minX, int minZ, int maxX, int maxZ) {
        ClientPlayNetworking.send(new RequestRectPayload(requestSeq, dimensionId, minX, minZ, maxX, maxZ));
    }

    public static void requestTeleport(int x, int y, int z) {
        ClientPlayNetworking.send(new TeleportRequestPayload(x, y, z));
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        ClientPlayNetworking.send(new ManualConnectPayload(ax, az, bx, bz));
    }

    private record RequestRectPayload(int requestSeq, ResourceLocation dimensionId, int minX, int minZ, int maxX, int maxZ)
            implements CustomPacketPayload {
        private static final Type<RequestRectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_request_rect"));
        private static final StreamCodec<RegistryFriendlyByteBuf, RequestRectPayload> CODEC = CustomPacketPayload.codec(
                (payload, buf) -> {
                    buf.writeVarInt(payload.requestSeq);
                    buf.writeResourceLocation(payload.dimensionId);
                    buf.writeVarInt(payload.minX);
                    buf.writeVarInt(payload.minZ);
                    buf.writeVarInt(payload.maxX);
                    buf.writeVarInt(payload.maxZ);
                },
                buf -> new RequestRectPayload(
                        buf.readVarInt(),
                        buf.readResourceLocation(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record SnapshotPayload(int requestSeq, ResourceLocation dimensionId, MapSnapshot snapshot) implements CustomPacketPayload {
        private static final Type<SnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_snapshot"));
        private static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPayload> CODEC = CustomPacketPayload.codec(
                (payload, buf) -> {
                    buf.writeVarInt(payload.requestSeq);
                    buf.writeResourceLocation(payload.dimensionId);
                    MapSnapshotCodec.write(buf, payload.snapshot);
                },
                buf -> new SnapshotPayload(
                        buf.readVarInt(),
                        buf.readResourceLocation(),
                        MapSnapshotCodec.read(buf)
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record TeleportRequestPayload(int x, int y, int z) implements CustomPacketPayload {
        private static final Type<TeleportRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_teleport"));
        private static final StreamCodec<RegistryFriendlyByteBuf, TeleportRequestPayload> CODEC = CustomPacketPayload.codec(
                (payload, buf) -> {
                    buf.writeVarInt(payload.x);
                    buf.writeVarInt(payload.y);
                    buf.writeVarInt(payload.z);
                },
                buf -> new TeleportRequestPayload(
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record TeleportAckPayload(boolean ok, int x, int y, int z) implements CustomPacketPayload {
        private static final Type<TeleportAckPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_teleport_ack"));
        private static final StreamCodec<RegistryFriendlyByteBuf, TeleportAckPayload> CODEC = CustomPacketPayload.codec(
                (payload, buf) -> {
                    buf.writeBoolean(payload.ok);
                    buf.writeVarInt(payload.x);
                    buf.writeVarInt(payload.y);
                    buf.writeVarInt(payload.z);
                },
                buf -> new TeleportAckPayload(
                        buf.readBoolean(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record ManualConnectPayload(int ax, int az, int bx, int bz) implements CustomPacketPayload {
        private static final Type<ManualConnectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_manual_connect"));
        private static final StreamCodec<RegistryFriendlyByteBuf, ManualConnectPayload> CODEC = CustomPacketPayload.codec(
                (payload, buf) -> {
                    buf.writeVarInt(payload.ax);
                    buf.writeVarInt(payload.az);
                    buf.writeVarInt(payload.bx);
                    buf.writeVarInt(payload.bz);
                },
                buf -> new ManualConnectPayload(
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
