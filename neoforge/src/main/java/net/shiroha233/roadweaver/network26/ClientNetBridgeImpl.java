package net.shiroha233.roadweaver.network.neoforge;

import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.network26.ClientNetworkNeoForge26;

/** Architectury @ExpectPlatform implementation retained outside the retired legacy network source path. */
public final class ClientNetBridgeImpl {
    private ClientNetBridgeImpl() {}

    public static void requestSnapshot(int requestSeq, Identifier dimensionId, int minX, int minZ, int maxX, int maxZ) {
        ClientNetworkNeoForge26.requestSnapshot(requestSeq, dimensionId, minX, minZ, maxX, maxZ);
    }

    public static void requestTeleport(int x, int y, int z) {
        ClientNetworkNeoForge26.requestTeleport(x, y, z);
    }

    public static void requestManualConnect(int ax, int az, int bx, int bz) {
        ClientNetworkNeoForge26.requestManualConnect(ax, az, bx, bz);
    }
}