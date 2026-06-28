package net.shiroha233.roadweaver.network.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.client.map.MapLoadPhase;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotPatch;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.map.MapPatchService;
import net.shiroha233.roadweaver.map.MapPatchService;
import net.shiroha233.roadweaver.map.permission.MapAccessService;
import net.shiroha233.roadweaver.network.MapSnapshotCodec;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.util.ComputeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Forge 平台地图网络通信实现
 */
public class MapNetworkForge {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RoadWeaver.MOD_ID, "map"),
            () -> VERSION, VERSION::equals, VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, RequestMapSnapshotC2S.class, RequestMapSnapshotC2S::encode, RequestMapSnapshotC2S::decode, RequestMapSnapshotC2S::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MapSnapshotS2C.class, MapSnapshotS2C::encode, MapSnapshotS2C::decode, MapSnapshotS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, MapPatchS2C.class, MapPatchS2C::encode, MapPatchS2C::decode, MapPatchS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, TeleportC2S.class, TeleportC2S::encode, TeleportC2S::decode, TeleportC2S::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, TeleportAckS2C.class, TeleportAckS2C::encode, TeleportAckS2C::decode, TeleportAckS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ManualConnectC2S.class, ManualConnectC2S::encode, ManualConnectC2S::decode, ManualConnectC2S::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MapAccessSyncS2C.class, MapAccessSyncS2C::encode, MapAccessSyncS2C::decode, MapAccessSyncS2C::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static class RequestMapSnapshotC2S {
        public final int requestSeq;
        public final ResourceLocation requestedDimensionId;
        public final MapLoadPhase phase;
        public final int responseIndex;
        public final int minX, minZ, maxX, maxZ;

        public RequestMapSnapshotC2S(int requestSeq,
                                     ResourceLocation requestedDimensionId,
                                     MapLoadPhase phase,
                                     int responseIndex,
                                     int minX,
                                     int minZ,
                                     int maxX,
                                     int maxZ) {
            this.requestSeq = requestSeq;
            this.requestedDimensionId = requestedDimensionId;
            this.phase = phase;
            this.responseIndex = responseIndex;
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        public static void encode(RequestMapSnapshotC2S msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.requestSeq);
            buf.writeResourceLocation(msg.requestedDimensionId);
            buf.writeUtf(msg.phase.name());
            buf.writeVarInt(msg.responseIndex);
            buf.writeVarInt(msg.minX);
            buf.writeVarInt(msg.minZ);
            buf.writeVarInt(msg.maxX);
            buf.writeVarInt(msg.maxZ);
        }

        public static RequestMapSnapshotC2S decode(FriendlyByteBuf buf) {
            int requestSeq = buf.readVarInt();
            ResourceLocation requestedDimensionId = buf.readResourceLocation();
            MapLoadPhase phase = MapLoadPhase.valueOf(buf.readUtf());
            int responseIndex = buf.readVarInt();
            int minX = buf.readVarInt();
            int minZ = buf.readVarInt();
            int maxX = buf.readVarInt();
            int maxZ = buf.readVarInt();
            return new RequestMapSnapshotC2S(requestSeq, requestedDimensionId, phase, responseIndex, minX, minZ, maxX, maxZ);
        }

        public static void handle(RequestMapSnapshotC2S msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            var player = c.getSender();
            if (player != null) {
                if (!MapAccessService.canOpenMap(player)) {
                    c.enqueueWork(() -> syncMapAccess(player));
                    c.setPacketHandled(true);
                    return;
                }
                CompletableFuture
                    .supplyAsync(() -> {
                        var level = player.serverLevel();
                        ResourceLocation actualDimensionId = level.dimension().location();
                        MapSnapshot snap = switch (msg.phase) {
                            case STRUCTURES -> MapDataCollector.buildStructuresSnapshot(level, msg.minX, msg.minZ, msg.maxX, msg.maxZ);
                            case ROADS -> MapDataCollector.buildRoadsSnapshot(level, msg.minX, msg.minZ, msg.maxX, msg.maxZ);
                            case CONNECTIONS -> MapDataCollector.buildConnectionsSnapshot(level, msg.minX, msg.minZ, msg.maxX, msg.maxZ);
                        };
                        return new MapSnapshotS2C(msg.requestSeq, actualDimensionId, msg.phase, msg.responseIndex, snap);
                    }, ComputeService.mapExecutor())
                    .thenAccept(packet -> c.enqueueWork(() -> CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT)));
            }
            c.setPacketHandled(true);
        }
    }

    public static void requestSnapshot(int requestSeq,
                                       ResourceLocation dimensionId,
                                       MapLoadPhase phase,
                                       int responseIndex,
                                       int minX,
                                       int minZ,
                                       int maxX,
                                       int maxZ) {
        CHANNEL.sendToServer(new RequestMapSnapshotC2S(requestSeq, dimensionId, phase, responseIndex, minX, minZ, maxX, maxZ));
    }

    public static class MapSnapshotS2C {
        public final int requestSeq;
        public final ResourceLocation dimensionId;
        public final MapLoadPhase phase;
        public final int responseIndex;
        public final MapSnapshot snapshot;

        public MapSnapshotS2C(int requestSeq, ResourceLocation dimensionId, MapLoadPhase phase, int responseIndex, MapSnapshot s) {
            this.requestSeq = requestSeq;
            this.dimensionId = dimensionId;
            this.phase = phase;
            this.responseIndex = responseIndex;
            this.snapshot = s;
        }

        public static void encode(MapSnapshotS2C msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.requestSeq);
            buf.writeResourceLocation(msg.dimensionId);
            buf.writeUtf(msg.phase.name());
            buf.writeVarInt(msg.responseIndex);
            MapSnapshotCodec.write(buf, msg.snapshot);
        }

        public static MapSnapshotS2C decode(FriendlyByteBuf buf) {
            int requestSeq = buf.readVarInt();
            ResourceLocation dimensionId = buf.readResourceLocation();
            MapLoadPhase phase = MapLoadPhase.valueOf(buf.readUtf());
            int responseIndex = buf.readVarInt();
            return new MapSnapshotS2C(requestSeq, dimensionId, phase, responseIndex, MapSnapshotCodec.read(buf));
        }

        public static void handle(MapSnapshotS2C msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof RoadMapScreen screen) {
                    screen.acceptSnapshotPart(msg.requestSeq, msg.dimensionId, msg.phase, msg.responseIndex, msg.snapshot);
                }
            });
            c.setPacketHandled(true);
        }
    }

    public static class MapPatchS2C {
        public final ResourceLocation dimensionId;
        public final MapSnapshotPatch patch;

        public MapPatchS2C(ResourceLocation dimensionId, MapSnapshotPatch patch) {
            this.dimensionId = dimensionId;
            this.patch = patch;
        }

        public static void encode(MapPatchS2C msg, FriendlyByteBuf buf) {
            buf.writeResourceLocation(msg.dimensionId);
            MapSnapshotCodec.writePatch(buf, msg.patch);
        }

        public static MapPatchS2C decode(FriendlyByteBuf buf) {
            ResourceLocation dimensionId = buf.readResourceLocation();
            MapSnapshotPatch patch = MapSnapshotCodec.readPatch(buf);
            return new MapPatchS2C(dimensionId, patch);
        }

        public static void handle(MapPatchS2C msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof RoadMapScreen screen) {
                    screen.acceptPatch(msg.dimensionId, msg.patch);
                } else {
                    MapSnapshotCache.applyPatch(msg.dimensionId, msg.patch);
                }
            });
            c.setPacketHandled(true);
        }
    }

    public static class MapAccessSyncS2C {
        public final boolean allowed;

        public MapAccessSyncS2C(boolean allowed) {
            this.allowed = allowed;
        }

        public static void encode(MapAccessSyncS2C msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.allowed);
        }

        public static MapAccessSyncS2C decode(FriendlyByteBuf buf) {
            return new MapAccessSyncS2C(buf.readBoolean());
        }

        public static void handle(MapAccessSyncS2C msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> ClientMapAccessGuard.applyServerState(Minecraft.getInstance(), msg.allowed));
            c.setPacketHandled(true);
        }
    }

    public static class TeleportC2S {
        public final int x, y, z;

        public TeleportC2S(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public static void encode(TeleportC2S msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.x);
            buf.writeVarInt(msg.y);
            buf.writeVarInt(msg.z);
        }

        public static TeleportC2S decode(FriendlyByteBuf buf) {
            int x = buf.readVarInt();
            int y = buf.readVarInt();
            int z = buf.readVarInt();
            return new TeleportC2S(x, y, z);
        }

        public static void handle(TeleportC2S msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                var sp = c.getSender();
                if (sp == null) return;
                boolean allowed = sp.isCreative() || sp.hasPermissions(2);
                if (!allowed) {
                    CHANNEL.sendTo(new TeleportAckS2C(false, 0, 0, 0), sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                    return;
                }
                var level = sp.serverLevel();
                level.getChunk(msg.x >> 4, msg.z >> 4);
                int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, msg.x, msg.z);
                if (ty <= level.getMinBuildHeight()) ty = level.getSeaLevel() + 1; else ty += 1;
                sp.teleportTo(level, msg.x + 0.5, ty, msg.z + 0.5, sp.getYRot(), sp.getXRot());
                CHANNEL.sendTo(new TeleportAckS2C(true, msg.x, ty, msg.z), sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            });
            c.setPacketHandled(true);
        }
    }

    public static class TeleportAckS2C {
        public final boolean ok;
        public final int x, y, z;

        public TeleportAckS2C(boolean ok, int x, int y, int z) {
            this.ok = ok;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public static void encode(TeleportAckS2C msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.ok);
            buf.writeVarInt(msg.x);
            buf.writeVarInt(msg.y);
            buf.writeVarInt(msg.z);
        }

        public static TeleportAckS2C decode(FriendlyByteBuf buf) {
            boolean ok = buf.readBoolean();
            int x = buf.readVarInt();
            int y = buf.readVarInt();
            int z = buf.readVarInt();
            return new TeleportAckS2C(ok, x, y, z);
        }

        public static void handle(TeleportAckS2C msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                Minecraft mc = Minecraft.getInstance();
                var player = mc.player;
                if (player == null) return;
                if (msg.ok) player.displayClientMessage(Objects.requireNonNull(Component.translatable("gui.roadweaver.map.teleport.success_pos", msg.x, msg.y, msg.z)), true);
                else player.displayClientMessage(Objects.requireNonNull(Component.translatable("gui.roadweaver.map.teleport.denied")), true);
            });
            c.setPacketHandled(true);
        }
    }

    public static void requestTeleport(int x, int y, int z) {
        CHANNEL.sendToServer(new TeleportC2S(x, y, z));
    }

    public static class ManualConnectC2S {
        public final int ax, az, bx, bz;

        public ManualConnectC2S(int ax, int az, int bx, int bz) {
            this.ax = ax;
            this.az = az;
            this.bx = bx;
            this.bz = bz;
        }

        public static void encode(ManualConnectC2S msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.ax);
            buf.writeVarInt(msg.az);
            buf.writeVarInt(msg.bx);
            buf.writeVarInt(msg.bz);
        }

        public static ManualConnectC2S decode(FriendlyByteBuf buf) {
            int ax = buf.readVarInt();
            int az = buf.readVarInt();
            int bx = buf.readVarInt();
            int bz = buf.readVarInt();
            return new ManualConnectC2S(ax, az, bx, bz);
        }

        public static void handle(ManualConnectC2S msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                var sp = c.getSender();
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
                BlockPos a = new BlockPos(msg.ax, 0, msg.az);
                BlockPos b = new BlockPos(msg.bx, 0, msg.bz);
                boolean exists = false;
                for (StructureConnection sc : list) {
                    BlockPos f = sc.from();
                    BlockPos t = sc.to();
                    if ((f.equals(a) && t.equals(b)) || (f.equals(b) && t.equals(a))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    StructureConnection created = new StructureConnection(a, b, ConnectionStatus.PLANNED);
                    list.add(created);
                    provider.setStructureConnections(level, list);
                    MapPatchService.publishConnection(level, created);
                }
            });
            c.setPacketHandled(true);
        }
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        CHANNEL.sendToServer(new ManualConnectC2S(ax, az, bx, bz));
    }

    public static void broadcastPatch(ServerPlayer player, ResourceLocation dimensionId, MapSnapshotPatch patch) {
        if (player == null || dimensionId == null || patch == null || patch.isEmpty()) return;
        CHANNEL.sendTo(new MapPatchS2C(dimensionId, patch), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void syncMapAccess(ServerPlayer player) {
        if (player != null) {
            CHANNEL.sendTo(new MapAccessSyncS2C(MapAccessService.canOpenMap(player)), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }
}
