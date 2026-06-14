package net.shiroha233.roadweaver.client.map.localoverride;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.client.map.tile.LoadedChunkTileOverrideManager;
import net.shiroha233.roadweaver.map.tile.core.MapTileRect;

import java.util.concurrent.CompletableFuture;

/**
 * 区块覆盖构建服务。
 */
public final class LoadedChunkTerrainOverrideService {
    private LoadedChunkTerrainOverrideService() {}

    public static CompletableFuture<Void> refreshRectAsync(ClientLevel clientLevel, ServerLevel serverLevel, MapTileRect rect) {
        if (clientLevel == null || serverLevel == null || rect == null) {
            return CompletableFuture.completedFuture(null);
        }
        int minBlockX = rect.minTileX() * 1024;
        int minBlockZ = rect.minTileZ() * 1024;
        int maxBlockX = (rect.maxTileX() + 1) * 1024;
        int maxBlockZ = (rect.maxTileZ() + 1) * 1024;
        for (int cz = minBlockZ; cz < maxBlockZ; cz += 16) {
            for (int cx = minBlockX; cx < maxBlockX; cx += 16) {
                if (clientLevel.hasChunk(cx >> 4, cz >> 4)) {
                    LoadedChunkTileOverrideManager.onChunkGenerated(clientLevel, serverLevel, cx >> 4, cz >> 4);
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    public static void refreshRect(ClientLevel clientLevel, ServerLevel serverLevel, MapTileRect rect) {
        refreshRectAsync(clientLevel, serverLevel, rect);
    }
}