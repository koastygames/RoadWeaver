package net.shiroha233.roadweaver.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;

public class MapNetworkPayloads {
    public static final CustomPacketPayload.Type<MapRequestRectPayload> REQ_RECT = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_request_rect"));
    public static final CustomPacketPayload.Type<MapSnapshotPayload> SNAP = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_snapshot"));
    public static final CustomPacketPayload.Type<MapTeleportPayload> TP_REQ = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_teleport"));
    public static final CustomPacketPayload.Type<MapTeleportAckPayload> TP_ACK = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_teleport_ack"));
    public static final CustomPacketPayload.Type<MapManualConnectPayload> MAN_REQ = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("roadweaver", "map_manual_connect"));

    public record MapRequestRectPayload(int requestSeq, ResourceLocation dimension, int minX, int minZ, int maxX, int maxZ) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MapRequestRectPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MapRequestRectPayload::requestSeq,
            ResourceLocation.STREAM_CODEC, MapRequestRectPayload::dimension,
            ByteBufCodecs.VAR_INT, MapRequestRectPayload::minX,
            ByteBufCodecs.VAR_INT, MapRequestRectPayload::minZ,
            ByteBufCodecs.VAR_INT, MapRequestRectPayload::maxX,
            ByteBufCodecs.VAR_INT, MapRequestRectPayload::maxZ,
            MapRequestRectPayload::new
        );
        @Override public Type<MapRequestRectPayload> type() { return REQ_RECT; }
    }

    public record MapSnapshotPayload(int requestSeq, ResourceLocation dimension, MapSnapshot snapshot) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MapSnapshotPayload> CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeVarInt(val.requestSeq);
                buf.writeResourceLocation(val.dimension);
                MapSnapshotCodec.write(buf, val.snapshot);
            },
            buf -> new MapSnapshotPayload(
                buf.readVarInt(),
                buf.readResourceLocation(),
                MapSnapshotCodec.read(buf)
            )
        );
        @Override public Type<MapSnapshotPayload> type() { return SNAP; }
    }

    public record MapTeleportPayload(int x, int y, int z) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MapTeleportPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MapTeleportPayload::x,
            ByteBufCodecs.VAR_INT, MapTeleportPayload::y,
            ByteBufCodecs.VAR_INT, MapTeleportPayload::z,
            MapTeleportPayload::new
        );
        @Override public Type<MapTeleportPayload> type() { return TP_REQ; }
    }

    public record MapTeleportAckPayload(boolean success, int x, int y, int z) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MapTeleportAckPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MapTeleportAckPayload::success,
            ByteBufCodecs.VAR_INT, MapTeleportAckPayload::x,
            ByteBufCodecs.VAR_INT, MapTeleportAckPayload::y,
            ByteBufCodecs.VAR_INT, MapTeleportAckPayload::z,
            MapTeleportAckPayload::new
        );
        @Override public Type<MapTeleportAckPayload> type() { return TP_ACK; }
    }

    public record MapManualConnectPayload(BlockPos from, BlockPos to) implements CustomPacketPayload {
        public static final StreamCodec<FriendlyByteBuf, MapManualConnectPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MapManualConnectPayload::from,
            BlockPos.STREAM_CODEC, MapManualConnectPayload::to,
            MapManualConnectPayload::new
        );
        @Override public Type<MapManualConnectPayload> type() { return MAN_REQ; }
    }
}
