package net.shiroha233.roadweaver.network26;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.shiroha233.roadweaver.client.map.data.MapDataCollector;
import net.shiroha233.roadweaver.client.map.data.MapSnapshot;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.helpers.PermissionCompat;
import net.shiroha233.roadweaver.map.permission.MapAccessService;
import net.shiroha233.roadweaver.network.MapNetworkPayloads;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.util.ComputeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Server/common half of the Minecraft 26.2 NeoForge map protocol. */
public final class MapNetworkNeoForge26 {
    private MapNetworkNeoForge26() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(MapNetworkNeoForge26::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");

        registrar.playToServer(
                MapNetworkPayloads.REQ_RECT,
                MapNetworkPayloads.MapRequestRectPayload.CODEC,
                MapNetworkNeoForge26::handleRequestRect
        );
        registrar.playToClient(
                MapNetworkPayloads.SNAP,
                MapNetworkPayloads.MapSnapshotPayload.CODEC
        );
        registrar.playToServer(
                MapNetworkPayloads.TP_REQ,
                MapNetworkPayloads.MapTeleportPayload.CODEC,
                MapNetworkNeoForge26::handleTeleportRequest
        );
        registrar.playToClient(
                MapNetworkPayloads.TP_ACK,
                MapNetworkPayloads.MapTeleportAckPayload.CODEC
        );
        registrar.playToClient(
                MapNetworkPayloads.ACCESS_SYNC,
                MapNetworkPayloads.MapAccessSyncPayload.CODEC
        );
        registrar.playToServer(
                MapNetworkPayloads.MAN_REQ,
                MapNetworkPayloads.MapManualConnectPayload.CODEC,
                MapNetworkNeoForge26::handleManualConnect
        );
    }

    private static void handleRequestRect(MapNetworkPayloads.MapRequestRectPayload payload, IPayloadContext context) {
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
        } catch (Throwable ignored) {
            computedRadiusBlocks = 4096;
        }
        final int radiusBlocks = Math.max(16, computedRadiusBlocks);

        CompletableFuture.supplyAsync(() -> {
            var level = sp.level();
            Identifier actualDimensionId = level.dimension().identifier();
            MapSnapshot snapshot = MapDataCollector.build(
                    level,
                    payload.minX(), payload.minZ(), payload.maxX(), payload.maxZ(),
                    cx, cz, radiusBlocks
            );
            return new MapNetworkPayloads.MapSnapshotPayload(payload.requestSeq(), actualDimensionId, snapshot);
        }, ComputeService.executor()).thenAccept(out -> {
            var server = sp.level().getServer();
            if (server != null) {
                server.execute(() -> {
                    if (!sp.isRemoved() && !sp.hasDisconnected()) {
                        PacketDistributor.sendToPlayer(sp, out);
                    }
                });
            }
        });
    }

    private static void handleTeleportRequest(MapNetworkPayloads.MapTeleportPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sp)) return;

        if (!(sp.isCreative() || PermissionCompat.hasCommandLevel2(sp))) {
            PacketDistributor.sendToPlayer(sp, new MapNetworkPayloads.MapTeleportAckPayload(false, 0, 0, 0));
            return;
        }

        var level = sp.level();
        int x = payload.x();
        int z = payload.z();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinY()) y = level.getSeaLevel() + 1;
        else y += 1;

        sp.teleportTo(level, x + 0.5D, y, z + 0.5D, Set.of(), sp.getYRot(), sp.getXRot(), false);
        PacketDistributor.sendToPlayer(sp, new MapNetworkPayloads.MapTeleportAckPayload(true, x, y, z));
    }

    private static void handleManualConnect(MapNetworkPayloads.MapManualConnectPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sp)) return;
        if (!PermissionCompat.hasCommandLevel2(sp)) return;

        var level = sp.level();
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> origin = provider.getStructureConnections(level);
        List<StructureConnection> connections = origin != null ? new ArrayList<>(origin) : new ArrayList<>();
        BlockPos a = payload.from();
        BlockPos b = payload.to();

        boolean exists = false;
        for (StructureConnection connection : connections) {
            BlockPos from = connection.from();
            BlockPos to = connection.to();
            if ((from.equals(a) && to.equals(b)) || (from.equals(b) && to.equals(a))) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            connections.add(new StructureConnection(a, b, ConnectionStatus.PLANNED));
            provider.setStructureConnections(level, connections);
        }
    }

    public static void syncMapAccess(ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(
                    player,
                    new MapNetworkPayloads.MapAccessSyncPayload(MapAccessService.canOpenMap(player))
            );
        }
    }
}