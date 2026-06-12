package net.shiroha233.roadweaver.client.map.localoverride;

import net.minecraft.resources.ResourceLocation;
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
}