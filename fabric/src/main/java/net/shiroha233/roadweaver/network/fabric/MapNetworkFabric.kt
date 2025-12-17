package net.shiroha233.roadweaver.network.fabric

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.Heightmap
import net.shiroha233.roadweaver.RoadWeaver
import net.shiroha233.roadweaver.client.map.RoadMapScreen
import net.shiroha233.roadweaver.client.map.data.MapDataCollector
import net.shiroha233.roadweaver.client.map.data.MapSnapshot
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.network.MapSnapshotCodec
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.util.ComputeService
import java.util.concurrent.CompletableFuture

object MapNetworkFabric {
    @JvmField
    val REQ_RECT_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_request_rect")

    @JvmField
    val SNAP_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_snapshot")

    @JvmField
    val TP_REQ_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_teleport")

    @JvmField
    val TP_ACK_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_teleport_ack")

    @JvmField
    val MAN_REQ_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "map_manual_connect")

    @JvmStatic
    fun register() {
        PayloadTypeRegistry.playC2S().register(RequestMapSnapshotC2S.TYPE, RequestMapSnapshotC2S.CODEC)
        PayloadTypeRegistry.playS2C().register(MapSnapshotS2C.TYPE, MapSnapshotS2C.CODEC)
        PayloadTypeRegistry.playC2S().register(TeleportC2S.TYPE, TeleportC2S.CODEC)
        PayloadTypeRegistry.playS2C().register(TeleportAckS2C.TYPE, TeleportAckS2C.CODEC)
        PayloadTypeRegistry.playC2S().register(ManualConnectC2S.TYPE, ManualConnectC2S.CODEC)
    }

    @JvmStatic
    fun registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(RequestMapSnapshotC2S.TYPE) { payload, context ->
            val sp: ServerPlayer = context.player()
            val minX = payload.minX
            val minZ = payload.minZ
            val maxX = payload.maxX
            val maxZ = payload.maxZ
            val cx = kotlin.math.round(sp.x).toInt()
            val cz = kotlin.math.round(sp.z).toInt()

            val radiusChunks: Int = try {
                val cfg = net.shiroha233.roadweaver.config.ConfigService.get()
                if (cfg.dynamicPlanEnabled()) cfg.dynamicPlanRadiusChunks() else cfg.initialPlanRadiusChunks()
            } catch (_: Throwable) {
                256
            }
            val radiusBlocks = kotlin.math.max(1, radiusChunks) * 16

            CompletableFuture
                .supplyAsync(
                    { MapDataCollector.build(sp.serverLevel(), minX, minZ, maxX, maxZ, cx, cz, radiusBlocks) },
                    ComputeService.executor()
                )
                .thenAccept { snapshot ->
                    context.server().execute { ServerPlayNetworking.send(sp, MapSnapshotS2C(snapshot)) }
                }
        }

        ServerPlayNetworking.registerGlobalReceiver(TeleportC2S.TYPE) { payload, context ->
            val sp: ServerPlayer = context.player()
            context.server().execute {
                val allowed = sp.isCreative || sp.hasPermissions(2)
                if (!allowed) {
                    ServerPlayNetworking.send(sp, TeleportAckS2C(false, 0, 0, 0))
                    return@execute
                }

                val level = sp.serverLevel()
                val x = payload.x
                val z = payload.z
                level.getChunk(x shr 4, z shr 4)

                var ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                ty = if (ty <= level.minBuildHeight) level.seaLevel + 1 else ty + 1

                sp.teleportTo(level, x + 0.5, ty.toDouble(), z + 0.5, sp.yRot, sp.xRot)
                ServerPlayNetworking.send(sp, TeleportAckS2C(true, x, ty, z))
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ManualConnectC2S.TYPE) { payload, context ->
            val sp: ServerPlayer = context.player()
            context.server().execute {
                val level = sp.serverLevel()
                val provider = WorldDataProvider.getInstance()
                val origin = provider.getStructureConnections(level)
                val list = if (origin != null) ArrayList(origin) else ArrayList()

                val a = BlockPos(payload.ax, 0, payload.az)
                val b = BlockPos(payload.bx, 0, payload.bz)

                var exists = false
                for (c in list) {
                    val f = c.from
                    val t = c.to
                    if ((f == a && t == b) || (f == b && t == a)) {
                        exists = true
                        break
                    }
                }

                if (!exists) {
                    list.add(Records.StructureConnection(a, b, Records.ConnectionStatus.PLANNED))
                    provider.setStructureConnections(level, list)
                }
            }
        }
    }

    @JvmStatic
    fun registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(MapSnapshotS2C.TYPE) { payload, context ->
            val s: MapSnapshot = payload.snapshot
            context.client().execute {
                val current = context.client().screen
                if (current is RoadMapScreen) {
                    current.onMapSnapshotReceived(s, 0)
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(TeleportAckS2C.TYPE) { payload, context ->
            val ok = payload.ok
            val fx = payload.x
            val fy = payload.y
            val fz = payload.z
            context.client().execute {
                val p = context.client().player ?: return@execute
                if (ok) p.displayClientMessage(
                    Component.translatable("gui.roadweaver.map.teleport.success_pos", fx, fy, fz),
                    true
                )
                else p.displayClientMessage(
                    Component.translatable("gui.roadweaver.map.teleport.denied"),
                    true
                )
            }
        }
    }

    @JvmStatic
    fun requestSnapshot(minX: Int, minZ: Int, maxX: Int, maxZ: Int) {
        ClientPlayNetworking.send(RequestMapSnapshotC2S(minX, minZ, maxX, maxZ))
    }

    @JvmStatic
    fun requestTeleport(x: Int, y: Int, z: Int) {
        ClientPlayNetworking.send(TeleportC2S(x, y, z))
    }

    @JvmStatic
    fun requestManualConnect(ax: Int, az: Int, bx: Int, bz: Int) {
        ClientPlayNetworking.send(ManualConnectC2S(ax, az, bx, bz))
    }

    // Payloads

    class RequestMapSnapshotC2S(
        @JvmField val minX: Int,
        @JvmField val minZ: Int,
        @JvmField val maxX: Int,
        @JvmField val maxZ: Int
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<RequestMapSnapshotC2S> = CustomPacketPayload.Type(REQ_RECT_ID)

            @JvmField
            val CODEC: StreamCodec<FriendlyByteBuf, RequestMapSnapshotC2S> = StreamCodec.of(
                { buf, v ->
                    buf.writeVarInt(v.minX)
                    buf.writeVarInt(v.minZ)
                    buf.writeVarInt(v.maxX)
                    buf.writeVarInt(v.maxZ)
                },
                { buf -> RequestMapSnapshotC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()) }
            )
        }
    }

    class MapSnapshotS2C(@JvmField val snapshot: MapSnapshot) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<MapSnapshotS2C> = CustomPacketPayload.Type(SNAP_ID)

            @JvmField
            val CODEC: StreamCodec<FriendlyByteBuf, MapSnapshotS2C> = StreamCodec.of(
                { buf, v -> MapSnapshotCodec.write(buf, v.snapshot) },
                { buf -> MapSnapshotS2C(MapSnapshotCodec.read(buf)) }
            )
        }
    }

    class TeleportC2S(
        @JvmField val x: Int,
        @JvmField val y: Int,
        @JvmField val z: Int
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<TeleportC2S> = CustomPacketPayload.Type(TP_REQ_ID)

            @JvmField
            val CODEC: StreamCodec<FriendlyByteBuf, TeleportC2S> = StreamCodec.of(
                { buf, v ->
                    buf.writeVarInt(v.x)
                    buf.writeVarInt(v.y)
                    buf.writeVarInt(v.z)
                },
                { buf -> TeleportC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()) }
            )
        }
    }

    class TeleportAckS2C(
        @JvmField val ok: Boolean,
        @JvmField val x: Int,
        @JvmField val y: Int,
        @JvmField val z: Int
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<TeleportAckS2C> = CustomPacketPayload.Type(TP_ACK_ID)

            @JvmField
            val CODEC: StreamCodec<FriendlyByteBuf, TeleportAckS2C> = StreamCodec.of(
                { buf, v ->
                    buf.writeBoolean(v.ok)
                    buf.writeVarInt(v.x)
                    buf.writeVarInt(v.y)
                    buf.writeVarInt(v.z)
                },
                { buf -> TeleportAckS2C(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()) }
            )
        }
    }

    class ManualConnectC2S(
        @JvmField val ax: Int,
        @JvmField val az: Int,
        @JvmField val bx: Int,
        @JvmField val bz: Int
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<ManualConnectC2S> = CustomPacketPayload.Type(MAN_REQ_ID)

            @JvmField
            val CODEC: StreamCodec<FriendlyByteBuf, ManualConnectC2S> = StreamCodec.of(
                { buf, v ->
                    buf.writeVarInt(v.ax)
                    buf.writeVarInt(v.az)
                    buf.writeVarInt(v.bx)
                    buf.writeVarInt(v.bz)
                },
                { buf -> ManualConnectC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()) }
            )
        }
    }
}
