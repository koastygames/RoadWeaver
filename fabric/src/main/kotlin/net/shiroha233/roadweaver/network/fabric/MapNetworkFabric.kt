package net.shiroha233.roadweaver.network.fabric

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.Heightmap
import net.shiroha233.roadweaver.RoadWeaver
import net.shiroha233.roadweaver.client.map.RoadMapScreen
import net.shiroha233.roadweaver.client.map.data.MapDataCollector
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.network.MapSnapshotCodec
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.util.ComputeService
import java.util.concurrent.CompletableFuture

object MapNetworkFabric {
    val REQ_RECT_ID: ResourceLocation = ResourceLocation(RoadWeaver.MOD_ID, "map_request_rect")
    val SNAP_ID: ResourceLocation = ResourceLocation(RoadWeaver.MOD_ID, "map_snapshot")
    val TP_REQ_ID: ResourceLocation = ResourceLocation(RoadWeaver.MOD_ID, "map_teleport")
    val TP_ACK_ID: ResourceLocation = ResourceLocation(RoadWeaver.MOD_ID, "map_teleport_ack")
    val MAN_REQ_ID: ResourceLocation = ResourceLocation(RoadWeaver.MOD_ID, "map_manual_connect")

    @JvmStatic
    fun register() {
        // Nothing needed here for 1.20.1 style, Handlers registered during init
    }

    @JvmStatic
    fun registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(REQ_RECT_ID) { server, player, handler, buf, responseSender ->
            val minX = buf.readVarInt()
            val minZ = buf.readVarInt()
            val maxX = buf.readVarInt()
            val maxZ = buf.readVarInt()
            
            val cx = kotlin.math.round(player.x).toInt()
            val cz = kotlin.math.round(player.z).toInt()

            val radiusChunks: Int = try {
                val cfg = net.shiroha233.roadweaver.config.ConfigService.get()
                if (cfg.dynamicPlanEnabled()) cfg.dynamicPlanRadiusChunks() else cfg.initialPlanRadiusChunks()
            } catch (_: Throwable) { 256 }
            val radiusBlocks = kotlin.math.max(1, radiusChunks) * 16

            val level = player.serverLevel()
            CompletableFuture.supplyAsync({
                MapDataCollector.build(level, minX, minZ, maxX, maxZ, cx, cz, radiusBlocks)
            }, ComputeService.executor()).thenAccept { snapshot ->
                server.execute {
                    val out = PacketByteBufs.create()
                    MapSnapshotCodec.write(out, snapshot)
                    ServerPlayNetworking.send(player, SNAP_ID, out)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(TP_REQ_ID) { server, player, handler, buf, responseSender ->
            val x = buf.readVarInt()
            val y = buf.readVarInt()
            val z = buf.readVarInt()
            
            server.execute {
                val allowed = player.isCreative || player.hasPermissions(2)
                if (!allowed) {
                    val ack = PacketByteBufs.create()
                    ack.writeBoolean(false)
                    ack.writeVarInt(0); ack.writeVarInt(0); ack.writeVarInt(0)
                    ServerPlayNetworking.send(player, TP_ACK_ID, ack)
                    return@execute
                }

                val level = player.serverLevel()
                level.getChunk(x shr 4, z shr 4)
                var ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                ty = if (ty <= level.minBuildHeight) level.seaLevel + 1 else ty + 1

                player.teleportTo(level, x + 0.5, ty.toDouble(), z + 0.5, player.yRot, player.xRot)
                
                val ack = PacketByteBufs.create()
                ack.writeBoolean(true)
                ack.writeVarInt(x); ack.writeVarInt(ty); ack.writeVarInt(z)
                ServerPlayNetworking.send(player, TP_ACK_ID, ack)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(MAN_REQ_ID) { server, player, handler, buf, responseSender ->
            val ax = buf.readVarInt()
            val az = buf.readVarInt()
            val bx = buf.readVarInt()
            val bz = buf.readVarInt()
            
            server.execute {
                val level = player.serverLevel()
                val provider = WorldDataProvider.getInstance()
                val origin = provider.getStructureConnections(level)
                val list = if (origin != null) ArrayList(origin) else ArrayList()

                val a = BlockPos(ax, 0, az)
                val b = BlockPos(bx, 0, bz)

                var exists = false
                for (c in list) {
                    val f = c.from
                    val t = c.to
                    val sameAB = f.x == a.x && f.z == a.z && t.x == b.x && t.z == b.z
                    val sameBA = f.x == b.x && f.z == b.z && t.x == a.x && t.z == a.z
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
        }
    }

    @JvmStatic
    fun registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(SNAP_ID) { client, handler, buf, responseSender ->
            val snapshot = MapSnapshotCodec.read(buf)
            client.execute {
                val current = client.screen
                if (current is RoadMapScreen) {
                    current.onMapSnapshotReceived(snapshot, 0)
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(TP_ACK_ID) { client, handler, buf, responseSender ->
            val ok = buf.readBoolean()
            val fx = buf.readVarInt()
            val fy = buf.readVarInt()
            val fz = buf.readVarInt()
            client.execute {
                val p = client.player ?: return@execute
                if (ok) p.displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.success_pos", fx, fy, fz), true)
                else p.displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.denied"), true)
            }
        }
    }

    @JvmStatic
    fun requestSnapshot(minX: Int, minZ: Int, maxX: Int, maxZ: Int) {
        val buf = PacketByteBufs.create()
        buf.writeVarInt(minX)
        buf.writeVarInt(minZ)
        buf.writeVarInt(maxX)
        buf.writeVarInt(maxZ)
        ClientPlayNetworking.send(REQ_RECT_ID, buf)
    }

    @JvmStatic
    fun requestTeleport(x: Int, y: Int, z: Int) {
        val buf = PacketByteBufs.create()
        buf.writeVarInt(x)
        buf.writeVarInt(y)
        buf.writeVarInt(z)
        ClientPlayNetworking.send(TP_REQ_ID, buf)
    }

    @JvmStatic
    fun requestManualConnect(ax: Int, az: Int, bx: Int, bz: Int) {
        val buf = PacketByteBufs.create()
        buf.writeVarInt(ax)
        buf.writeVarInt(az)
        buf.writeVarInt(bx)
        buf.writeVarInt(bz)
        ClientPlayNetworking.send(MAN_REQ_ID, buf)
    }
}
