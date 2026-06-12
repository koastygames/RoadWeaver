package net.shiroha233.roadweaver.map.tile.storage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;

import java.nio.file.Path;

/**
 * 服务端基础瓦片路径解析。
 */
public final class ServerMapTilePathResolver {
    private ServerMapTilePathResolver() {}

    private static final String ROOT_DIR = "data/roadweaver/map";

    public static Path mapRoot(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve(ROOT_DIR);
    }

    public static Path dimensionRoot(ServerLevel level) {
        return mapRoot(level).resolve(dimensionKey(level.dimension().location()));
    }

    public static Path layerRoot(ServerLevel level, MapTileLayer layer) {
        return dimensionRoot(level).resolve(layer.folderName());
    }

    public static Path tilePath(ServerLevel level, MapTileLayer layer, MapTileCoord coord) {
        return layerRoot(level, layer)
                .resolve(coord.zoomFolder())
                .resolve(Integer.toString(coord.tileX()))
                .resolve(coord.tileFileName());
    }

    public static String dimensionKey(ResourceLocation location) {
        return location.getNamespace() + "_" + location.getPath().replace('/', '_');
    }
}