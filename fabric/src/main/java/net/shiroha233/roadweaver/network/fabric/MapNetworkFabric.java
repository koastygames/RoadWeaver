package net.shiroha233.roadweaver.network.fabric;

import java.util.concurrent.CompletableFuture;
import net.shiroha233.roadweaver.util.ComputeService;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.network.MapNetworkPayloads;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.minecraft.core.BlockPos;

public class MapNetworkFabric {

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(MapNetworkPayloads.REQ_RECT, MapNetworkPayloads.MapRequestRectPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapNetworkPayloads.SNAP, MapNetworkPayloads.MapSnapshotPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MapNetworkPayloads.TP_REQ, MapNetworkPayloads.MapTeleportPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapNetworkPayloads.TP_ACK, MapNetworkPayloads.MapTeleportAckPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MapNetworkPayloads.MAN_REQ, MapNetworkPayloads.MapManualConnectPayload.CODEC);
    }

    public static void registerServerReceivers() {
        // 矩形范围请求：minX,minZ,maxX,maxZ
        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.REQ_RECT, (payload, context) -> {
            int requestSeq = payload.requestSeq();
            int minX = payload.minX();
            int minZ = payload.minZ();
            int maxX = payload.maxX();
            int maxZ = payload.maxZ();
            ServerPlayer sp = context.player();
            int cx = (int) Math.round(sp.getX());
            int cz = (int) Math.round(sp.getZ());
            int computedRadiusBlocks;
            try {
                net.shiroha233.roadweaver.config.ModConfig cfg = net.shiroha233.roadweaver.config.ConfigService.get();
                if (cfg.highwayEnabled()) {
                    computedRadiusBlocks = Math.max(16, cfg.highwayPlanningRadiusBlocks());
                } else {
                    int radiusChunks = cfg.dynamicPlanEnabled()
                            ? cfg.dynamicPlanRadiusChunks()
                            : cfg.initialPlanRadiusChunks();
                    computedRadiusBlocks = Math.max(1, radiusChunks) * 16;
                }
            } catch (Throwable t) {
                computedRadiusBlocks = 256 * 16;
            }
            final int radiusBlocksFinal = Math.max(16, computedRadiusBlocks);
            CompletableFuture
                .supplyAsync(() -> {
                    // 不信任客户端传来的维度：以服务端玩家当前所处维度为准（防止伪造/竞态）。
                    var level = sp.serverLevel();
                    ResourceLocation actualDimensionId = level.dimension().location();
                    MapSnapshot snapshot = MapDataCollector.build(level, minX, minZ, maxX, maxZ, cx, cz, radiusBlocksFinal);
                    return new MapNetworkPayloads.MapSnapshotPayload(requestSeq, actualDimensionId, snapshot);
                }, ComputeService.executor())
                .thenAccept(outPayload -> context.server().execute(() -> ServerPlayNetworking.send(sp, outPayload)));
        });

        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.TP_REQ, (payload, context) -> {
            int x = payload.x();
            int z = payload.z();
            context.server().execute(() -> {
                ServerPlayer sp = context.player();
                boolean allowed = sp.isCreative() || sp.hasPermissions(2);
                if (!allowed) {
                    ServerPlayNetworking.send(sp, new MapNetworkPayloads.MapTeleportAckPayload(false, 0, 0, 0));
                    return;
                }
                var level = sp.serverLevel();
                level.getChunk(x >> 4, z >> 4);
                int ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (ty <= level.getMinBuildHeight()) ty = level.getSeaLevel() + 1; else ty += 1;
                sp.teleportTo(level, x + 0.5, ty, z + 0.5, sp.getYRot(), sp.getXRot());
                ServerPlayNetworking.send(sp, new MapNetworkPayloads.MapTeleportAckPayload(true, x, ty, z));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.MAN_REQ, (payload, context) -> {
            BlockPos a = payload.from();
            BlockPos b = payload.to();
            context.server().execute(() -> {
                ServerPlayer sp = context.player();
                if (sp == null) return;

                boolean allowed = sp.hasPermissions(2);
                if (!allowed) {
                    sp.displayClientMessage(Component.translatable("gui.roadweaver.map.manual_connect.denied"), true);
                    return;
                }

                var level = sp.serverLevel();
                WorldDataProvider provider = WorldDataProvider.getInstance();
                java.util.List<Records.StructureConnection> origin = provider.getStructureConnections(level);
                java.util.List<Records.StructureConnection> list = origin != null ? new java.util.ArrayList<>(origin) : new java.util.ArrayList<>();
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
        });
    }

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.SNAP, (payload, context) -> {
            int requestSeq = payload.requestSeq();
            ResourceLocation dimensionId = payload.dimension();
            MapSnapshot s = payload.snapshot();
            context.client().execute(() -> {
                if (context.client().screen instanceof RoadMapScreen screen) {
                    screen.acceptSnapshot(requestSeq, dimensionId, s);
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(MapNetworkPayloads.TP_ACK, (payload, context) -> {
            boolean ok = payload.success();
            int fx = payload.x();
            int fy = payload.y();
            int fz = payload.z();
            context.client().execute(() -> {
                if (context.client().player == null) return;
                if (ok) context.client().player.displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.success_pos", fx, fy, fz), true);
                else context.client().player.displayClientMessage(Component.translatable("gui.roadweaver.map.teleport.denied"), true);
            });
        });
    }

    public static void requestSnapshot(int requestSeq, ResourceLocation dimensionId, int minX, int minZ, int maxX, int maxZ) {
        ClientPlayNetworking.send(new MapNetworkPayloads.MapRequestRectPayload(requestSeq, dimensionId, minX, minZ, maxX, maxZ));
    }

    public static void requestTeleport(int x, int y, int z) {
        ClientPlayNetworking.send(new MapNetworkPayloads.MapTeleportPayload(x, y, z));
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        ClientPlayNetworking.send(new MapNetworkPayloads.MapManualConnectPayload(new BlockPos(ax, 0, az), new BlockPos(bx, 0, bz)));
    }
}
