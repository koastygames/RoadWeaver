package net.shiroha233.roadweaver.network.forge

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.network.simple.SimpleChannel
import net.shiroha233.roadweaver.RoadWeaver
import net.shiroha233.roadweaver.client.map.RoadMapScreen
import net.shiroha233.roadweaver.client.map.data.MapDataCollector
import net.shiroha233.roadweaver.client.map.data.MapSnapshot
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.network.MapSnapshotCodec
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.util.ComputeService
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

/**
 * Forge 地图网络通道 (1.20.1)
 */
@Suppress("MemberVisibilityCanBePrivate")
object MapNetworkForge {
    private const val PROTOCOL_VERSION = "1"
    
    val INSTANCE: SimpleChannel = NetworkRegistry.newSimpleChannel(
        ResourceLocation(RoadWeaver.MOD_ID, "main"),
        { PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION },
        { it == PROTOCOL_VERSION }
    )

    @JvmStatic
    fun register(modBus: IEventBus) {
        var id = 0
        INSTANCE.registerMessage(id++, RequestMapSnapshotC2S::class.java, RequestMapSnapshotC2S::encode, RequestMapSnapshotC2S::decode, RequestMapSnapshotC2S::handle)
        INSTANCE.registerMessage(id++, MapSnapshotS2C::class.java, MapSnapshotS2C::encode, MapSnapshotS2C::decode, MapSnapshotS2C::handle)
        INSTANCE.registerMessage(id++, TeleportC2S::class.java, TeleportC2S::encode, TeleportC2S::decode, TeleportC2S::handle)
        INSTANCE.registerMessage(id++, TeleportAckS2C::class.java, TeleportAckS2C::encode, TeleportAckS2C::decode, TeleportAckS2C::handle)
        INSTANCE.registerMessage(id++, ManualConnectC2S::class.java, ManualConnectC2S::encode, ManualConnectC2S::decode, ManualConnectC2S::handle)
    }

    @JvmStatic
    fun requestSnapshot(minX: Int, minZ: Int, maxX: Int, maxZ: Int) {
        INSTANCE.sendToServer(RequestMapSnapshotC2S(minX, minZ, maxX, maxZ))
    }

    @JvmStatic
    fun requestTeleport(x: Int, y: Int, z: Int) {
        INSTANCE.sendToServer(TeleportC2S(x, y, z))
    }

    @JvmStatic
    fun requestManualConnect(ax: Int, az: Int, bx: Int, bz: Int) {
        INSTANCE.sendToServer(ManualConnectC2S(ax, az, bx, bz))
    }

    // Packets
    
    class RequestMapSnapshotC2S(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int) {
        fun encode(buf: FriendlyByteBuf) {
            buf.writeVarInt(minX)
            buf.writeVarInt(minZ)
            buf.writeVarInt(maxX)
            buf.writeVarInt(maxZ)
        }

        companion object {
            fun decode(buf: FriendlyByteBuf): RequestMapSnapshotC2S {
                return RequestMapSnapshotC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
            }

            fun handle(msg: RequestMapSnapshotC2S, ctx: Supplier<NetworkEvent.Context>) {
                val context = ctx.get()
                context.enqueueWork {
                    val sp = context.sender ?: return@enqueueWork
                    val cx = kotlin.math.round(sp.x).toInt()
                    val cz = kotlin.math.round(sp.z).toInt()
                    
                    val radiusChunks: Int = try {
                        val cfg = net.shiroha233.roadweaver.config.ConfigService.get()
                        if (cfg.dynamicPlanEnabled()) cfg.dynamicPlanRadiusChunks() else cfg.initialPlanRadiusChunks()
                    } catch (_: Throwable) { 
                        256 
                    }
                    val radiusBlocks = kotlin.math.max(1, radiusChunks) * 16

                    val level = sp.serverLevel()
                    CompletableFuture.supplyAsync({
                        MapDataCollector.build(level, msg.minX, msg.minZ, msg.maxX, msg.maxZ, cx, cz, radiusBlocks)
                    }, ComputeService.executor()).thenAccept { snapshot ->
                        context.enqueueWork {
                            INSTANCE.send(PacketDistributor.PLAYER.with { sp }, MapSnapshotS2C(snapshot))
                        }
                    }
                }
                context.packetHandled = true
            }
        }
    }

    class MapSnapshotS2C(val snapshot: MapSnapshot) {
        fun encode(buf: FriendlyByteBuf) {
            MapSnapshotCodec.write(buf, snapshot)
        }

        companion object {
            fun decode(buf: FriendlyByteBuf): MapSnapshotS2C {
                return MapSnapshotS2C(MapSnapshotCodec.read(buf))
            }

            fun handle(msg: MapSnapshotS2C, ctx: Supplier<NetworkEvent.Context>) {
                val context = ctx.get()
                context.enqueueWork {
                    val current = Minecraft.getInstance().screen
                    if (current is RoadMapScreen) {
                        current.onMapSnapshotReceived(msg.snapshot, 0)
                    }
                }
                context.packetHandled = true
            }
        }
    }

    class TeleportC2S(val x: Int, val y: Int, val z: Int) {
        fun encode(buf: FriendlyByteBuf) {
            buf.writeVarInt(x)
            buf.writeVarInt(y)
            buf.writeVarInt(z)
        }

        companion object {
            fun decode(buf: FriendlyByteBuf): TeleportC2S {
                return TeleportC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
            }

            fun handle(msg: TeleportC2S, ctx: Supplier<NetworkEvent.Context>) {
                val context = ctx.get()
                context.enqueueWork {
                    val sp = context.sender ?: return@enqueueWork
                    val allowed = sp.isCreative || sp.hasPermissions(2)
                    if (!allowed) {
                        INSTANCE.send(PacketDistributor.PLAYER.with { sp }, TeleportAckS2C(false, 0, 0, 0))
                        return@enqueueWork
                    }

                    val level = sp.serverLevel()
                    level.getChunk(msg.x shr 4, msg.z shr 4)

                    var ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, msg.x, msg.z)
                    ty = if (ty <= level.minBuildHeight) level.seaLevel + 1 else ty + 1

                    sp.teleportTo(level, msg.x + 0.5, ty.toDouble(), msg.z + 0.5, sp.yRot, sp.xRot)
                    INSTANCE.send(PacketDistributor.PLAYER.with { sp }, TeleportAckS2C(true, msg.x, ty, msg.z))
                }
                context.packetHandled = true
            }
        }
    }

    class TeleportAckS2C(val ok: Boolean, val x: Int, val y: Int, val z: Int) {
        fun encode(buf: FriendlyByteBuf) {
            buf.writeBoolean(ok)
            buf.writeVarInt(x)
            buf.writeVarInt(y)
            buf.writeVarInt(z)
        }

        companion object {
            fun decode(buf: FriendlyByteBuf): TeleportAckS2C {
                return TeleportAckS2C(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
            }

            fun handle(msg: TeleportAckS2C, ctx: Supplier<NetworkEvent.Context>) {
                val context = ctx.get()
                context.enqueueWork {
                    val p = Minecraft.getInstance().player ?: return@enqueueWork
                    if (msg.ok) {
                        p.displayClientMessage(
                            Component.translatable("gui.roadweaver.map.teleport.success_pos", msg.x, msg.y, msg.z),
                            true
                        )
                    } else {
                        p.displayClientMessage(
                            Component.translatable("gui.roadweaver.map.teleport.denied"),
                            true
                        )
                    }
                }
                context.packetHandled = true
            }
        }
    }

    class ManualConnectC2S(val ax: Int, val az: Int, val bx: Int, val bz: Int) {
        fun encode(buf: FriendlyByteBuf) {
            buf.writeVarInt(ax)
            buf.writeVarInt(az)
            buf.writeVarInt(bx)
            buf.writeVarInt(bz)
        }

        companion object {
            fun decode(buf: FriendlyByteBuf): ManualConnectC2S {
                return ManualConnectC2S(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
            }

            fun handle(msg: ManualConnectC2S, ctx: Supplier<NetworkEvent.Context>) {
                val context = ctx.get()
                context.enqueueWork {
                    val sp = context.sender ?: return@enqueueWork

                    val allowed = sp.hasPermissions(2)
                    if (!allowed) {
                        sp.displayClientMessage(Component.translatable("gui.roadweaver.map.manual_connect.denied"), true)
                        return@enqueueWork
                    }

                    val level = sp.serverLevel()
                    val provider = WorldDataProvider.getInstance()
                    val origin = provider.getStructureConnections(level)
                    val list = if (origin != null) ArrayList(origin) else ArrayList()

                    val a = BlockPos(msg.ax, 0, msg.az)
                    val b = BlockPos(msg.bx, 0, msg.bz)

                    var exists = false
                    for (c in list) {
                        val f = c.from
                        val t = c.to
                        val sameAB = (f === a || (f.x == a.x && f.y == a.y && f.z == a.z)) &&
                            (t === b || (t.x == b.x && t.y == b.y && t.z == b.z))
                        val sameBA = (f === b || (f.x == b.x && f.y == b.y && f.z == b.z)) &&
                            (t === a || (t.x == a.x && t.y == a.y && t.z == a.z))
                        if (sameAB || sameBA) {
                            exists = true
                            break
                        }
                    }

                    if (!exists) {
                        list.add(Records.StructureConnection(a, b, Records.ConnectionStatus.PLANNED))
                        provider.setStructureConnections(level, list)
                    }
                }
                context.packetHandled = true
            }
        }
    }
}
