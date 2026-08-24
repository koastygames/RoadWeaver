package net.shiroha233.roadweaver.map.permission.neoforge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.shiroha233.roadweaver.map.permission.MapAccessPolicy;
import net.shiroha233.roadweaver.network26.MapNetworkNeoForge26;

/** NeoForge bridge for persistent map permissions and 26.2 client synchronisation. */
public final class MapAccessPlatformBridgeImpl {
    private MapAccessPlatformBridgeImpl() {
    }

    public static MapAccessPolicy getPolicy(MinecraftServer server) {
        return NeoForgeMapAccessSavedData.get(server).getPolicy();
    }

    public static void setPolicy(MinecraftServer server, MapAccessPolicy policy) {
        NeoForgeMapAccessSavedData.get(server).setPolicy(policy);
    }

    public static void syncPlayer(ServerPlayer player) {
        if (player != null) {
            MapNetworkNeoForge26.syncMapAccess(player);
        }
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MapNetworkNeoForge26.syncMapAccess(player);
        }
    }
}