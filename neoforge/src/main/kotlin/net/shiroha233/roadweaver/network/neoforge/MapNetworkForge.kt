package net.shiroha233.roadweaver.network.neoforge

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.Heightmap
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import net.shiroha233.roadweaver.RoadWeaver
import net.shiroha233.roadweaver.client.map.RoadMapScreen
import net.shiroha233.roadweaver.client.map.data.MapDataCollector
import net.shiroha233.roadweaver.client.map.data.MapSnapshot
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.network.MapSnapshotCodec
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.util.ComputeService
import java.util.concurrent.CompletableFuture

/**
 * NeoForge 地图网络通道
 *
 * 注意：这里严格保持与 Java 版协议一致（ID、Codec、处理逻辑），避免客户端/服务端不兼容。
 */
@Suppress("MemberVisibilityCanBePrivate")
object MapNetworkForge {
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
    fun register(modBus: IEventBus) {
        modBus.addListener(::registerPayloads)
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar: PayloadRegistrar = event.registrar("1")

        registrar.playToClient(MapSnapshotS2C.TYPE, MapSnapshotS2C.CODEC, MapSnapshotS2C::handle)
        registrar.playToClient(TeleportAckS2C.TYPE, TeleportAckS2C.CODEC, TeleportAckS2C::handle)

        registrar.playToServer(RequestMapSnapshotC2S.TYPE, RequestMapSnapshotC2S.CODEC, RequestMapSnapshotC2S::handle)
        registrar.playToServer(TeleportC2S.TYPE, TeleportC2S.CODEC, TeleportC2S::handle)
        registrar.playToServer(ManualConnectC2S.TYPE, ManualConnectC2S.CODEC, ManualConnectC2S::handle)
    }

    @JvmStatic
    fun requestSnapshot(minX: Int, minZ: Int, maxX: Int, maxZ: Int) {
        PacketDistributor.sendToServer(RequestMapSnapshotC2S(minX, minZ, maxX, maxZ))
    }

    @JvmStatic
    fun requestTeleport(x: Int, y: Int, z: Int) {
        PacketDistributor.sendToServer(TeleportC2S(x, y, z))
    }

    @JvmStatic
    fun requestManualConnect(ax: Int, az: Int, bx: Int, bz: Int) {
        PacketDistributor.sendToServer(ManualConnectC2S(ax, az, bx, bz))
    }

    // Payloads

    data class RequestMapSnapshotC2S(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int) : CustomPacketPayload {
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

            @JvmStatic
            fun handle(payload: RequestMapSnapshotC2S, context: IPayloadContext) {
                val sp = context.player() as ServerPlayer
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
                        {
                            MapDataCollector.build(
                                sp.serverLevel(),
                                payload.minX,
                                payload.minZ,
                                payload.maxX,
                                payload.maxZ,
                                cx,
                                cz,
                                radiusBlocks
                            )
                        },
                        ComputeService.executor()
                    )
                    .thenAccept { snapshot ->
                        context.enqueueWork { PacketDistributor.sendToPlayer(sp, MapSnapshotS2C(snapshot)) }
                    }
            }
        }
    }

    data class MapSnapshotS2C(val snapshot: MapSnapshot) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<MapSnapshotS2C> = CustomPacketPayload.Type(SNAP_ID)

            @JvmField
            val CODEC: StreamCodec<FriendlyByteBuf, MapSnapshotS2C> = StreamCodec.of(
                { buf, v -> MapSnapshotCodec.write(buf, v.snapshot) },
                { buf -> MapSnapshotS2C(MapSnapshotCodec.read(buf)) }
            )

            @JvmStatic
            fun handle(payload: MapSnapshotS2C, context: IPayloadContext) {
                context.enqueueWork {
                    val mc = Minecraft.getInstance()
                    val screen = mc.screen
                    if (screen is RoadMapScreen) {
                        screen.setSnapshot(payload.snapshot)
                    }
                }
            }
        }
    }

    data class TeleportC2S(val x: Int, val y: Int, val z: Int) : CustomPacketPayload {
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

            @JvmStatic
            fun handle(payload: TeleportC2S, context: IPayloadContext) {
                context.enqueueWork {
                    val sp = context.player() as ServerPlayer
                    val allowed = sp.isCreative || sp.hasPermissions(2)
                    if (!allowed) {
                        PacketDistributor.sendToPlayer(sp, TeleportAckS2C(false, 0, 0, 0))
                        return@enqueueWork
                    }

                    val level = sp.serverLevel()
                    level.getChunk(payload.x shr 4, payload.z shr 4)
                    var ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, payload.x, payload.z)
                    ty = if (ty <= level.minBuildHeight) level.seaLevel + 1 else ty + 1

                    sp.teleportTo(level, payload.x + 0.5, ty.toDouble(), payload.z + 0.5, sp.yRot, sp.xRot)
                    PacketDistributor.sendToPlayer(sp, TeleportAckS2C(true, payload.x, ty, payload.z))
                }
            }
        }
    }

    data class TeleportAckS2C(val ok: Boolean, val x: Int, val y: Int, val z: Int) : CustomPacketPayload {
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

            @JvmStatic
            fun handle(payload: TeleportAckS2C, context: IPayloadContext) {
                context.enqueueWork {
                    val mc = Minecraft.getInstance()
                    val player = mc.player ?: return@enqueueWork
                    if (payload.ok) {
                        player.displayClientMessage(
                            Component.translatable(
                                "gui.roadweaver.map.teleport.success_pos",
                                payload.x,
                                payload.y,
                                payload.z
                            ),
                            true
                        )
                    } else {
                        player.displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.denied"), true)
                    }
                }
            }
        }
    }

    data class ManualConnectC2S(val ax: Int, val az: Int, val bx: Int, val bz: Int) : CustomPacketPayload {
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

            @JvmStatic
            fun handle(payload: ManualConnectC2S, context: IPayloadContext) {
                context.enqueueWork {
                    val sp = context.player() as ServerPlayer
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
    }
}
