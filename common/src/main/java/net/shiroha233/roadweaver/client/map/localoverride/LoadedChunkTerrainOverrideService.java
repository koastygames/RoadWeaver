package net.shiroha233.roadweaver.client.map.localoverride;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileLayer;
import net.shiroha233.roadweaver.map.tile.core.MapTileRect;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.map.tile.storage.ServerMapTileStorage;
import net.shiroha233.roadweaver.util.ComputeService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * 已加载区块的 terrain override 构建服务。
 */
public final class LoadedChunkTerrainOverrideService {
    private LoadedChunkTerrainOverrideService() {}

    public static CompletableFuture<Void> refreshRectAsync(ClientLevel clientLevel, ServerLevel serverLevel, MapTileRect rect) {
        if (clientLevel == null || serverLevel == null || rect == null) {
            return CompletableFuture.completedFuture(null);
        }
        return ComputeService.runAsync(() -> refreshRect(clientLevel, serverLevel, rect));
    }

    public static void refreshRect(ClientLevel clientLevel, ServerLevel serverLevel, MapTileRect rect) {
        for (MapTileCoord coord : rect.coords()) {
            refreshTile(clientLevel, serverLevel, coord);
        }
    }

    public static void refreshTile(ClientLevel clientLevel, ServerLevel serverLevel, MapTileCoord coord) {
        if (clientLevel == null || serverLevel == null || coord == null) return;
        BufferedImage image = loadBaseImage(serverLevel, coord);
        if (image == null) return;

        boolean changed = false;
        for (int pixelZ = 0; pixelZ < MapTileScheme.TILE_SIZE_PX; pixelZ++) {
            int worldZ = MapTileScheme.sampleBlockZ(coord, pixelZ);
            for (int pixelX = 0; pixelX < MapTileScheme.TILE_SIZE_PX; pixelX++) {
                int worldX = MapTileScheme.sampleBlockX(coord, pixelX);
                if (!clientLevel.hasChunk(worldX >> 4, worldZ >> 4)) {
                    continue;
                }
                int current = image.getRGB(pixelX, pixelZ);
                int actual = sampleLoadedArgb(clientLevel, worldX, worldZ, current);
                if (actual != current) {
                    image.setRGB(pixelX, pixelZ, actual);
                    changed = true;
                }
            }
        }

        if (changed) {
            ClientMapOverrideStorage.writePng(
                    clientLevel.dimension().location(),
                    MapTileLayer.TERRAIN,
                    coord,
                    image);
        }
    }

    private static BufferedImage loadBaseImage(ServerLevel serverLevel, MapTileCoord coord) {
        Path path = ServerMapTileStorage.path(serverLevel, MapTileLayer.TERRAIN, coord);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return ImageIO.read(path.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    private static int sampleLoadedArgb(ClientLevel level, int worldX, int worldZ, int fallbackArgb) {
        int minY = level.getMinBuildHeight();
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
        if (surfaceY < minY) {
            return fallbackArgb;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(worldX, surfaceY, worldZ);
        BlockState state = level.getBlockState(cursor);
        while (cursor.getY() > minY && state.isAir()) {
            cursor.move(0, -1, 0);
            state = level.getBlockState(cursor);
        }
        if (state.isAir()) {
            return fallbackArgb;
        }

        MapColor mapColor = state.getMapColor(level, cursor);
        if (mapColor == null || mapColor == MapColor.NONE) {
            return fallbackArgb;
        }
        return 0xFF000000 | mapColor.col;
    }
}