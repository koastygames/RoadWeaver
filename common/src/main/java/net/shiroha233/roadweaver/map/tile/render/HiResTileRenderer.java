package net.shiroha233.roadweaver.map.tile.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.shiroha233.roadweaver.map.tile.core.ChunkTileCoord;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 高精度区块瓦片渲染器。
 */
public final class HiResTileRenderer {
    private HiResTileRenderer() {}

    public static final int CHUNK_SIZE = 16;
    public static final int SAMPLE_SIZE = CHUNK_SIZE + 2;

    public static BufferedImage renderChunk(ClientLevel level, ChunkTileCoord coord) {
        if (level == null || coord == null) return null;

        BufferedImage image = new BufferedImage(CHUNK_SIZE, CHUNK_SIZE, BufferedImage.TYPE_INT_ARGB);
        int minBuildY = level.getMinBuildHeight();

        int baseX = coord.minBlockX() - 1;
        int baseZ = coord.minBlockZ() - 1;

        int[][] heightGrid = new int[SAMPLE_SIZE][SAMPLE_SIZE];
        int[][] colorGrid = new int[SAMPLE_SIZE][SAMPLE_SIZE];

        for (int pz = 0; pz < SAMPLE_SIZE; pz++) {
            int worldZ = baseZ + pz;
            int chunkZ = worldZ >> 4;
            for (int px = 0; px < SAMPLE_SIZE; px++) {
                int worldX = baseX + px;
                int chunkX = worldX >> 4;

                if (!level.hasChunk(chunkX, chunkZ)) {
                    heightGrid[pz][px] = Integer.MIN_VALUE;
                    colorGrid[pz][px] = 0;
                    continue;
                }

                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
                heightGrid[pz][px] = surfaceY;

                BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(worldX, surfaceY, worldZ);
                BlockState state = level.getBlockState(cursor);
                while (cursor.getY() > minBuildY && state.isAir()) {
                    cursor.move(0, -1, 0);
                    state = level.getBlockState(cursor);
                }

                if (state.isAir()) {
                    colorGrid[pz][px] = 0;
                } else {
                    MapColor mapColor = state.getMapColor(level, cursor);
                    if (mapColor != null && mapColor != MapColor.NONE) {
                        colorGrid[pz][px] = 0xFF000000 | mapColor.col;
                    } else {
                        colorGrid[pz][px] = 0;
                    }
                }
            }
        }

        for (int pz = 0; pz < CHUNK_SIZE; pz++) {
            int sampleZ = pz + 1;
            for (int px = 0; px < CHUNK_SIZE; px++) {
                int sampleX = px + 1;

                int baseColor = colorGrid[sampleZ][sampleX];
                if (baseColor == 0) {
                    image.setRGB(px, pz, 0);
                    continue;
                }

                double shade = 1.0;
                boolean hasValidNeighbors = true;
                for (int dy = -1; dy <= 1 && hasValidNeighbors; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (heightGrid[sampleZ + dy][sampleX + dx] == Integer.MIN_VALUE) {
                            hasValidNeighbors = false;
                        }
                    }
                }

                if (hasValidNeighbors) {
                    int[][] local = {
                            {heightGrid[sampleZ - 1][sampleX - 1], heightGrid[sampleZ - 1][sampleX], heightGrid[sampleZ - 1][sampleX + 1]},
                            {heightGrid[sampleZ][sampleX - 1], heightGrid[sampleZ][sampleX], heightGrid[sampleZ][sampleX + 1]},
                            {heightGrid[sampleZ + 1][sampleX - 1], heightGrid[sampleZ + 1][sampleX], heightGrid[sampleZ + 1][sampleX + 1]}
                    };
                    shade = HeightShader.computeShade(local);
                }

                image.setRGB(px, pz, HeightShader.multiplyRgb(baseColor, shade));
            }
        }

        return image;
    }

    public static BufferedImage downsample(int zoom, List<Path> coveredChunkPaths, int chunksPerTile) {
        int tileSize = 64;
        int chunkPx = tileSize / chunksPerTile;
        BufferedImage image = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);

        int idx = 0;
        for (int cz = 0; cz < chunksPerTile; cz++) {
            for (int cx = 0; cx < chunksPerTile; cx++) {
                if (idx >= coveredChunkPaths.size()) break;
                Path chunkPath = coveredChunkPaths.get(idx++);
                if (chunkPath == null || !Files.exists(chunkPath)) continue;

                try {
                    BufferedImage chunkImg = ImageIO.read(chunkPath.toFile());
                    if (chunkImg == null) continue;

                    int srcSize = Math.min(chunkImg.getWidth(), Math.min(chunkImg.getHeight(), CHUNK_SIZE));
                    for (int py = 0; py < chunkPx; py++) {
                        int srcY = py * srcSize / chunkPx;
                        for (int px = 0; px < chunkPx; px++) {
                            int srcX = px * srcSize / chunkPx;
                            int rgb = chunkImg.getRGB(Math.min(srcX, srcSize - 1), Math.min(srcY, srcSize - 1));
                            if (rgb != 0) {
                                image.setRGB(cx * chunkPx + px, cz * chunkPx + py, rgb);
                            }
                        }
                    }
                } catch (IOException ignored) {}
            }
        }

        return image;
    }
}