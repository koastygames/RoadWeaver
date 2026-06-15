package net.shiroha233.roadweaver.map.tile.storage;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * 服务端基础瓦片 PNG 存储。
 */
public final class ServerMapTileStorage {
    private ServerMapTileStorage() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    public static Path path(ServerLevel level, MapTileLayer layer, MapTileCoord coord) {
        return ServerMapTilePathResolver.tilePath(level, layer, coord);
    }

    public static boolean exists(ServerLevel level, MapTileLayer layer, MapTileCoord coord) {
        return Files.exists(path(level, layer, coord));
    }

    public static Path writePng(ServerLevel level, MapTileLayer layer, MapTileCoord coord, BufferedImage image) {
        Path path = path(level, layer, coord);
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(tempPath)) {
                ImageIO.write(image, "PNG", out);
            }
            moveIntoPlace(tempPath, path);
            return path;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {}
            throw new IllegalStateException("failed to write tile png: " + path, e);
        }
    }

    private static void moveIntoPlace(Path tempPath, Path path) throws IOException {
        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void clearAll(ServerLevel level) {
        Path root = ServerMapTilePathResolver.dimensionRoot(level);
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOGGER.warn("删除瓦片缓存失败: {}", path, e);
                }
            });
        } catch (IOException e) {
            LOGGER.warn("清理瓦片目录失败: {}", root, e);
        }
    }
}