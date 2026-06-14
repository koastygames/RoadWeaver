package net.shiroha233.roadweaver.client.map.localoverride;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.shiroha233.roadweaver.client.map.data.MapDataStorage;
import net.shiroha233.roadweaver.map.tile.core.ChunkTileCoord;
import net.shiroha233.roadweaver.map.tile.core.LodTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * 客户端本地 override 瓦片路径解析。
 */
public final class ClientMapOverridePathResolver {
    private ClientMapOverridePathResolver() {}

    private static final String ROOT_DIR = "tile-overrides";
    private static final String HIRES_ROOT = "data/roadweaver/map";

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

    public static Path chunkTilePath(ServerLevel level, ResourceLocation dimensionId, ChunkTileCoord coord) {
        return level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve(HIRES_ROOT)
                .resolve(dimensionId.getNamespace() + "_" + dimensionId.getPath().replace('/', '_'))
                .resolve("terrain")
                .resolve("chunks")
                .resolve(Integer.toString(coord.chunkX()))
                .resolve(coord.fileName());
    }

    public static Path lodTilePath(ServerLevel level, ResourceLocation dimensionId, LodTileCoord coord) {
        return level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve(HIRES_ROOT)
                .resolve(dimensionId.getNamespace() + "_" + dimensionId.getPath().replace('/', '_'))
                .resolve("terrain")
                .resolve("lod")
                .resolve(coord.zoomFolder())
                .resolve(Integer.toString(coord.tileX()))
                .resolve(coord.fileName());
    }
}
