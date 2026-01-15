package net.shiroha233.roadweaver.network.neoforge;

import net.minecraft.resources.ResourceLocation;

public final class ClientNetBridgeImpl {
    private ClientNetBridgeImpl() {}

    public static void requestSnapshot(int requestSeq, ResourceLocation dimensionId, int minX, int minZ, int maxX, int maxZ) {
        MapNetworkForge.requestSnapshot(requestSeq, dimensionId, minX, minZ, maxX, maxZ);
    }

    public static void requestTeleport(int x, int y, int z) {
        MapNetworkForge.requestTeleport(x, y, z);
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        MapNetworkForge.requestManualConnect(ax, az, bx, bz);
    }
}
