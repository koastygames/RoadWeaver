package net.shiroha233.roadweaver.network26;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.shiroha233.roadweaver.client26.RoadMapScreen26;
import net.shiroha233.roadweaver.network.MapNetworkPayloads;

/** Client-only half of the Minecraft 26.2 NeoForge map protocol. */
public final class ClientNetworkNeoForge26 {
    private ClientNetworkNeoForge26() {}

    public static void registerHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(MapNetworkPayloads.SNAP, ClientNetworkNeoForge26::handleSnapshot);
        event.register(MapNetworkPayloads.TP_ACK, ClientNetworkNeoForge26::handleTeleportAck);
        event.register(MapNetworkPayloads.ACCESS_SYNC, ClientNetworkNeoForge26::handleAccessSync);
    }

    private static void handleSnapshot(MapNetworkPayloads.MapSnapshotPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof RoadMapScreen26 screen) {
            screen.acceptSnapshot(payload.requestSeq(), payload.dimension(), payload.snapshot());
        }
    }

    private static void handleTeleportAck(MapNetworkPayloads.MapTeleportAckPayload payload, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        Component message = payload.success()
                ? Component.translatable("gui.roadweaver.map.teleport.success_pos", payload.x(), payload.y(), payload.z())
                : Component.translatable("gui.roadweaver.map.teleport.denied");
        mc.gui.hud.setOverlayMessage(message, false);
    }

    private static void handleAccessSync(MapNetworkPayloads.MapAccessSyncPayload payload, IPayloadContext context) {
        RoadMapScreen26.applyAccessAllowed(payload.allowed());
    }

    public static void requestSnapshot(int requestSeq, Identifier dimensionId, int minX, int minZ, int maxX, int maxZ) {
        ClientPacketDistributor.sendToServer(
                new MapNetworkPayloads.MapRequestRectPayload(requestSeq, dimensionId, minX, minZ, maxX, maxZ)
        );
    }

    public static void requestTeleport(int x, int y, int z) {
        ClientPacketDistributor.sendToServer(new MapNetworkPayloads.MapTeleportPayload(x, y, z));
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        ClientPacketDistributor.sendToServer(
                new MapNetworkPayloads.MapManualConnectPayload(
                        new net.minecraft.core.BlockPos(ax, 0, az),
                        new net.minecraft.core.BlockPos(bx, 0, bz)
                )
        );
    }
}