package net.shiroha233.roadweaver.client.map.localoverride;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.map.tile.core.ChunkTileCoord;
import net.shiroha233.roadweaver.map.tile.core.LodTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;

import java.nio.file.Path;

/**
 * 本地 override 优先级解析。
 */
public final class ClientMapOverrideResolver {
    private ClientMapOverrideResolver() {}

    public static Path resolveOrNull(ResourceLocation dimensionId, MapTileLayer layer, MapTileCoord coord, Path fallback) {
        if (dimensionId != null && ClientMapOverrideStorage.exists(dimensionId, layer, coord)) {
            return ClientMapOverrideStorage.path(dimensionId, layer, coord);
        }
        return fallback;
    }

    public static Path resolveChunkTileOrNull(ServerLevel level, ResourceLocation dimensionId, ChunkTileCoord coord) {
        if (level != null && dimensionId != null && ClientMapOverrideStorage.chunkTileExists(level, dimensionId, coord)) {
            return ClientMapOverrideStorage.chunkTilePath(level, dimensionId, coord);
        }
        return null;
    }

    public static Path resolveLodTileOrNull(ServerLevel level, ResourceLocation dimensionId, LodTileCoord coord) {
        if (level != null && dimensionId != null && ClientMapOverrideStorage.lodTileExists(level, dimensionId, coord)) {
            return ClientMapOverrideStorage.lodTilePath(level, dimensionId, coord);
        }
        return null;
    }
}