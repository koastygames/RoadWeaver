package net.shiroha233.roadweaver.map.tile.render;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.map.tile.core.MapTileCoord;
import net.shiroha233.roadweaver.map.tile.core.MapTileScheme;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;

import java.awt.image.BufferedImage;

/**
 * 基础地貌瓦片渲染器。
 */
public final class TerrainTileRenderer {
    private TerrainTileRenderer() {}

    public static BufferedImage render(ServerLevel level, TerrainSamplingCache cache, MapTileCoord coord) {
        BufferedImage image = new BufferedImage(
                MapTileScheme.TILE_SIZE_PX,
                MapTileScheme.TILE_SIZE_PX,
                BufferedImage.TYPE_INT_ARGB);

        int nearWaterDistance = Math.max(
                RoadConstants.CHUNK_SIZE_BLOCKS,
                MapTileScheme.blocksPerPixel(coord.zoom()) * 2);
        int seaLevel = level.getSeaLevel();

        for (int pixelZ = 0; pixelZ < MapTileScheme.TILE_SIZE_PX; pixelZ++) {
            int worldZ = MapTileScheme.sampleBlockZ(coord, pixelZ);
            for (int pixelX = 0; pixelX < MapTileScheme.TILE_SIZE_PX; pixelX++) {
                int worldX = MapTileScheme.sampleBlockX(coord, pixelX);
                int height = cache.height(level, worldX, worldZ);
                int oceanFloor = cache.oceanFloor(level, worldX, worldZ);
                Holder<Biome> biome = cache.getBiome(level, worldX, worldZ);
                boolean columnWater = cache.isColumnWater(level, worldX, worldZ);
                boolean nearWater = columnWater || cache.isNearWaterLike(level, worldX, worldZ, nearWaterDistance);
                int argb = TerrainTilePalette.colorFor(
                        biome,
                        height,
                        seaLevel,
                        oceanFloor,
                        columnWater,
                        nearWater);
                image.setRGB(pixelX, pixelZ, argb);
            }
        }

        return image;
    }
}