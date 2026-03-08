package net.shiroha233.roadweaver.network.fabric;

import net.minecraft.resources.Identifier;

/**
 * Fabric 平台网络桥接实现
 */
public final class ClientNetBridgeImpl {
    private ClientNetBridgeImpl() {}

    public static void requestSnapshot(int requestSeq, Identifier dimensionId, int minX, int minZ, int maxX, int maxZ) {
        MapNetworkFabric.requestSnapshot(requestSeq, dimensionId, minX, minZ, maxX, maxZ);
    }

    public static void requestTeleport(int x, int y, int z) {
        MapNetworkFabric.requestTeleport(x, y, z);
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        MapNetworkFabric.requestManualConnect(ax, az, bx, bz);
    }
}
