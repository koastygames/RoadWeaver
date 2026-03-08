package net.shiroha233.roadweaver.network.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.helpers.PermissionCompat;
import net.shiroha233.roadweaver.map.permission.MapAccessService;
import net.shiroha233.roadweaver.network.MapNetworkPayloads;
import net.shiroha233.roadweaver.util.ComputeService;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;

import java.util.concurrent.CompletableFuture;

public class MapNetworkNeoForge {

    public static void register(IEventBus modBus) {
        modBus.addListener(MapNetworkNeoForge::registerPayloads);
    }

    private static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(RoadWeaver.MOD_ID);

        registrar.playToServer(
                MapNetworkPayloads.REQ_RECT,
                MapNetworkPayloads.MapRequestRectPayload.CODEC,
                MapNetworkNeoForge::handleRequestRect
        );
        registrar.playToClient(
                MapNetworkPayloads.SNAP,
                MapNetworkPayloads.MapSnapshotPayload.CODEC,
                MapNetworkNeoForge::handleSnapshot
        );
        registrar.playToServer(
                MapNetworkPayloads.TP_REQ,
                MapNetworkPayloads.MapTeleportPayload.CODEC,
                MapNetworkNeoForge::handleTeleportRequest
        );
        registrar.playToClient(
                MapNetworkPayloads.TP_ACK,
                MapNetworkPayloads.MapTeleportAckPayload.CODEC,
                MapNetworkNeoForge::handleTeleportAck
        );
        registrar.playToClient(
                MapNetworkPayloads.ACCESS_SYNC,
                MapNetworkPayloads.MapAccessSyncPayload.CODEC,
                MapNetworkNeoForge::handleAccessSync
        );
        registrar.playToServer(
                MapNetworkPayloads.MAN_REQ,
                MapNetworkPayloads.MapManualConnectPayload.CODEC,
                MapNetworkNeoForge::handleManualConnect
        );
    }

    private static void handleRequestRect(final MapNetworkPayloads.MapRequestRectPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!MapAccessService.canOpenMap(sp)) {
                syncMapAccess(sp);
                return;
            }
            
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
            
            CompletableFuture.supplyAsync(() -> {
                var level = sp.level();
                Identifier actualDimensionId = level.dimension().identifier();
                MapSnapshot snap = MapDataCollector.build(level, payload.minX(), payload.minZ(), payload.maxX(), payload.maxZ(), cx, cz, radiusBlocksFinal);
                return new MapNetworkPayloads.MapSnapshotPayload(payload.requestSeq(), actualDimensionId, snap);
            }, ComputeService.executor()).thenAccept(outPayload -> {
                var server = sp.level().getServer();
                if (server != null) {
                    server.execute(() -> PacketDistributor.sendToPlayer(sp, outPayload));
                }
            });
        });
    }

    private static void handleSnapshot(final MapNetworkPayloads.MapSnapshotPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof RoadMapScreen screen) {
                screen.acceptSnapshot(payload.requestSeq(), payload.dimension(), payload.snapshot());
            }
        });
    }

    /**
     * 处理传送请求 - 使用异步区块加载避免阻塞主线程
     * 原理：getChunk() 是同步调用，会阻塞主线程等待区块加载完成。
     * 改用 getChunkFuture() 异步加载区块，加载完成后再在主线程执行传送。
     */
    private static void handleTeleportRequest(final MapNetworkPayloads.MapTeleportPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            
            boolean allowed = sp.isCreative() || PermissionCompat.hasCommandLevel2(sp);
            if (!allowed) {
                PacketDistributor.sendToPlayer(sp, new MapNetworkPayloads.MapTeleportAckPayload(false, 0, 0, 0));
                return;
            }
            
            var level = sp.level();
            int x = payload.x();
            int z = payload.z();
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            
            // 异步加载区块，避免阻塞主线程
            level.getChunkSource().getChunkFuture(chunkX, chunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true)
                .thenAccept(either -> {
                    // 区块加载完成后，回到主线程执行传送
                    level.getServer().execute(() -> {
                        // 再次检查玩家是否仍在线
                        if (sp.isRemoved() || sp.hasDisconnected()) return;
                        
                        int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        if (ty <= level.getMinY()) ty = level.getSeaLevel() + 1; else ty += 1;
                        sp.teleportTo(level, x + 0.5, ty, z + 0.5, java.util.Set.of(), sp.getYRot(), sp.getXRot(), false);
                        PacketDistributor.sendToPlayer(sp, new MapNetworkPayloads.MapTeleportAckPayload(true, x, ty, z));
                    });
                });
        });
    }

    private static void handleTeleportAck(final MapNetworkPayloads.MapTeleportAckPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            var player = mc.player;
            if (player == null) return;
            if (payload.success()) {
                player.displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.success_pos", payload.x(), payload.y(), payload.z()), true);
            } else {
                player.displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.denied"), true);
            }
        });
    }

    private static void handleAccessSync(final MapNetworkPayloads.MapAccessSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientMapAccessGuard.applyServerState(Minecraft.getInstance(), payload.allowed()));
    }

    private static void handleManualConnect(final MapNetworkPayloads.MapManualConnectPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;

            boolean allowed = PermissionCompat.hasCommandLevel2(sp);
            if (!allowed) {
                sp.displayClientMessage(Component.translatable("gui.roadweaver.map.manual_connect.denied"), true);
                return;
            }

            var level = sp.level();
            WorldDataProvider provider = WorldDataProvider.getInstance();
            java.util.List<StructureConnection> origin = provider.getStructureConnections(level);
            java.util.List<StructureConnection> list = origin != null ? new java.util.ArrayList<>(origin) : new java.util.ArrayList<>();
            BlockPos a = payload.from();
            BlockPos b = payload.to();
            boolean exists = false;
            for (StructureConnection sc : list) {
                BlockPos f = sc.from();
                BlockPos t = sc.to();
                if ((f.equals(a) && t.equals(b)) || (f.equals(b) && t.equals(a))) { exists = true; break; }
            }
            if (!exists) {
                list.add(new StructureConnection(a, b, ConnectionStatus.PLANNED));
                provider.setStructureConnections(level, list);
            }
        });
    }

    public static void requestSnapshot(int requestSeq, Identifier dimensionId, int minX, int minZ, int maxX, int maxZ) {
        ClientPacketDistributor.sendToServer(new MapNetworkPayloads.MapRequestRectPayload(requestSeq, dimensionId, minX, minZ, maxX, maxZ));
    }

    public static void requestTeleport(int x, int y, int z) {
        ClientPacketDistributor.sendToServer(new MapNetworkPayloads.MapTeleportPayload(x, y, z));
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        ClientPacketDistributor.sendToServer(new MapNetworkPayloads.MapManualConnectPayload(new BlockPos(ax, 0, az), new BlockPos(bx, 0, bz)));
    }

    public static void syncMapAccess(ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new MapNetworkPayloads.MapAccessSyncPayload(MapAccessService.canOpenMap(player)));
        }
    }
}
