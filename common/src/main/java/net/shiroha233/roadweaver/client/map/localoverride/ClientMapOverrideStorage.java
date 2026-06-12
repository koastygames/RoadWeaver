package net.shiroha233.roadweaver.client.map.localoverride;

import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端本地 override 瓦片存储。
 */
public final class ClientMapOverrideStorage {
    private ClientMapOverrideStorage() {}

    public static Path path(ResourceLocation dimensionId, MapTileLayer layer, MapTileCoord coord) {
        return ClientMapOverridePathResolver.tilePath(dimensionId, layer, coord);
    }

    public static boolean exists(ResourceLocation dimensionId, MapTileLayer layer, MapTileCoord coord) {
        return Files.exists(path(dimensionId, layer, coord));
    }

    public static Path writePng(ResourceLocation dimensionId, MapTileLayer layer, MapTileCoord coord, BufferedImage image) {
        Path path = path(dimensionId, layer, coord);
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                ImageIO.write(image, "PNG", out);
            }
            return path;
        } catch (IOException e) {
            throw new IllegalStateException("failed to write override tile: " + path, e);
        }
    }
}