package net.shiroha233.roadweaver.map.permission.neoforge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.shiroha233.roadweaver.map.permission.MapAccessPolicy;

/**
 * NeoForge bridge for map permissions.
 *
 * The permission policy itself remains fully persistent on Minecraft 26.2. The legacy map GUI
 * packet channel is intentionally disabled in the 26.2 core port, so client synchronisation is
 * a no-op until the map UI/network layer is migrated to the new 26.2 APIs.
 */
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
        // Client map networking is disabled in the Minecraft 26.2 core compatibility build.
    }

    public static void syncAll(MinecraftServer server) {
        // Client map networking is disabled in the Minecraft 26.2 core compatibility build.
    }
}
