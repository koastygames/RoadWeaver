package net.shiroha233.roadweaver.client.map.localoverride;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.client.map.data.MapDataStorage;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;

import java.nio.file.Path;

/**
 * 客户端本地 override 瓦片路径解析。
 */
public final class ClientMapOverridePathResolver {
    private ClientMapOverridePathResolver() {}

    private static final String ROOT_DIR = "tile-overrides";

    public static Path root() {
        Path worldDir = MapDataStorage.getWorldDataDir();
        if (worldDir != null) {
            return worldDir.resolve(ROOT_DIR);
        }
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/roadweaver/mapdata/_fallback").resolve(ROOT_DIR);
    }

    public static Path dimensionRoot(ResourceLocation dimensionId) {
        return root().resolve(dimensionId.getNamespace() + "_" + dimensionId.getPath().replace('/', '_'));
    }

    public static Path tilePath(ResourceLocation dimensionId, MapTileLayer layer, MapTileCoord coord) {
        return dimensionRoot(dimensionId)
                .resolve(layer.folderName())
                .resolve(coord.zoomFolder())
                .resolve(Integer.toString(coord.tileX()))
                .resolve(coord.tileFileName());
    }
}