package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileRect;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.map.tile.storage.ServerMapTileStorage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 将区域粗采样结果输出为地图 terrain PNG。
 */
public final class CoarseTerrainPngWriter {
    private CoarseTerrainPngWriter() {}

    public static void writeTerrainTiles(ServerLevel level, CoarseTerrainRegion region) {
        if (level == null || region == null) return;
        for (int zoom = MapTileScheme.MIN_ZOOM; zoom <= MapTileScheme.MAX_ZOOM; zoom++) {
            MapTileRect rect = region.bounds().tileRect(zoom);
            for (MapTileCoord coord : rect.coords()) {
                ServerMapTileStorage.writePng(level, MapTileLayer.TERRAIN, coord, renderTile(level, region, coord));
            }
        }
    }

    private static BufferedImage renderTile(ServerLevel level, CoarseTerrainRegion region, MapTileCoord coord) {
        BufferedImage image = loadExistingTile(level, coord);
        for (int pixelZ = 0; pixelZ < MapTileScheme.TILE_SIZE_PX; pixelZ++) {
            int worldZ = MapTileScheme.sampleBlockZ(coord, pixelZ);
            for (int pixelX = 0; pixelX < MapTileScheme.TILE_SIZE_PX; pixelX++) {
                int worldX = MapTileScheme.sampleBlockX(coord, pixelX);
                if (!region.contains(worldX, worldZ)) {
                    continue;
                }
                int index = region.bounds().indexOfNearest(worldX, worldZ);
                image.setRGB(pixelX, pixelZ, region.terrainArgbAtIndex(index));
            }
        }
        return image;
    }

    private static BufferedImage loadExistingTile(ServerLevel level, MapTileCoord coord) {
        Path path = ServerMapTileStorage.path(level, MapTileLayer.TERRAIN, coord);
        if (Files.exists(path)) {
            try {
                BufferedImage image = ImageIO.read(path.toFile());
                if (image != null) return image;
            } catch (IOException ignored) {}
        }
        return new BufferedImage(
                MapTileScheme.TILE_SIZE_PX,
                MapTileScheme.TILE_SIZE_PX,
                BufferedImage.TYPE_INT_ARGB);
    }
}