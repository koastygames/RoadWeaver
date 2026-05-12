/* 文件职责：为单个 Highway 网格单元格提供粗采样地形数据，用于交叉点选择。 */
package net.shiroha233.roadweaver.features.highway.terrain;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

/**
 * Highway 单元格粗采样地形场
 */
public final class HighwayCellTerrainField implements PathTerrainField {

    private final int step;
    private final int seaLevel;
    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    private final short[] heights;
    private final short[] oceanFloors;
    private final byte[] waterColumns;
    private final byte[] biomeFlags;

    private static final byte BIOME_NORMAL = 0;
    private static final byte BIOME_WATER = 1;

    private HighwayCellTerrainField(int step, int seaLevel, int minX, int minZ, int sizeX, int sizeZ) {
        this.step = step;
        this.seaLevel = seaLevel;
        this.minX = minX;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        int size = sizeX * sizeZ;
        this.heights = new short[size];
        this.oceanFloors = new short[size];
        this.waterColumns = new byte[size];
        this.biomeFlags = new byte[size];
    }

    public static HighwayCellTerrainField build(ServerLevel level,
                                                TerrainSamplingCache cache,
                                                int cellMinX,
                                                int cellMinZ,
                                                int gridBlocks) {
        int step = RoadConstants.HIGHWAY_TERRAIN_SAMPLING_STEP;
        int seaLevel = level.getSeaLevel();
        int sizeX = gridBlocks / step;
        int sizeZ = gridBlocks / step;

        HighwayCellTerrainField field = new HighwayCellTerrainField(step, seaLevel, cellMinX, cellMinZ, sizeX, sizeZ);

        for (int gz = 0; gz < sizeZ; gz++) {
            if (Thread.currentThread().isInterrupted()) return null;
            int worldZ = cellMinZ + gz * step;
            for (int gx = 0; gx < sizeX; gx++) {
                int worldX = cellMinX + gx * step;
                int index = gz * sizeX + gx;

                int h = cache.height(level, worldX, worldZ);
                field.heights[index] = (short) h;

                Holder<Biome> biome = cache.getBiome(level, worldX, worldZ);
                boolean waterBiome = biome.is(BiomeTags.IS_RIVER)
                        || biome.is(BiomeTags.IS_OCEAN)
                        || biome.is(BiomeTags.IS_DEEP_OCEAN);
                field.biomeFlags[index] = waterBiome ? BIOME_WATER : BIOME_NORMAL;

                int oceanFloor = cache.oceanFloor(level, worldX, worldZ);
                field.oceanFloors[index] = (short) oceanFloor;

                boolean columnWater = (waterBiome && oceanFloor < seaLevel)
                        || (h <= seaLevel + 1 && oceanFloor < h - 1);
                field.waterColumns[index] = (byte) (columnWater ? 1 : 0);
            }
        }
        return field;
    }

    @Override
    public int seaLevel() {
        return seaLevel;
    }

    @Override
    public int height(int x, int z) {
        return heights[indexForWorld(x, z)];
    }

    @Override
    public int oceanFloor(int x, int z) {
        return oceanFloors[indexForWorld(x, z)];
    }

    @Override
    public boolean isColumnWater(int x, int z) {
        return waterColumns[indexForWorld(x, z)] != 0;
    }

    @Override
    public boolean isNearWater(int x, int z, int neighborDistance) {
        if (isColumnWater(x, z)) return true;
        int d = Math.max(step, neighborDistance);
        int[][] offsets = {{d, 0}, {-d, 0}, {0, d}, {0, -d}};
        for (int[] off : offsets) {
            int nx = x + off[0];
            int nz = z + off[1];
            if (contains(nx, nz) && isColumnWater(nx, nz)) return true;
        }
        return false;
    }

    @Override
    public Holder<Biome> biome(int x, int z) {
        return null;
    }

    @Override
    public boolean contains(int x, int z) {
        int gx = gridX(x);
        int gz = gridZ(z);
        return gx >= 0 && gx < sizeX && gz >= 0 && gz < sizeZ;
    }

    @Override
    public int step() {
        return step;
    }

    public int sizeX() { return sizeX; }
    public int sizeZ() { return sizeZ; }
    public int minX() { return minX; }
    public int minZ() { return minZ; }

    public int heightAt(int gx, int gz) {
        return heights[gz * sizeX + gx];
    }

    public boolean isWaterAt(int gx, int gz) {
        return waterColumns[gz * sizeX + gx] != 0;
    }

    public boolean isWaterBiomeAt(int gx, int gz) {
        return biomeFlags[gz * sizeX + gx] == BIOME_WATER;
    }

    private int indexForWorld(int x, int z) {
        return gridZ(z) * sizeX + gridX(x);
    }

    private int gridX(int x) {
        return Math.floorDiv(x - minX, step);
    }

    private int gridZ(int z) {
        return Math.floorDiv(z - minZ, step);
    }
}
