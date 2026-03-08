package net.shiroha233.roadweaver.network.neoforge;

import net.minecraft.resources.Identifier;

public final class ClientNetBridgeImpl {
    private ClientNetBridgeImpl() {}

    public static void requestSnapshot(int requestSeq, Identifier dimensionId, int minX, int minZ, int maxX, int maxZ) {
        MapNetworkNeoForge.requestSnapshot(requestSeq, dimensionId, minX, minZ, maxX, maxZ);
    }

    public static void requestTeleport(int x, int y, int z) {
        MapNetworkNeoForge.requestTeleport(x, y, z);
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        MapNetworkNeoForge.requestManualConnect(ax, az, bx, bz);
    }
}
