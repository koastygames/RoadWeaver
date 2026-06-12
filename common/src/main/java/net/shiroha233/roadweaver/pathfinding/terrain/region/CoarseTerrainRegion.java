package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.pathfinding.terrain.PathTerrainField;

/**
 * 区域级粗采样结果。
 */
public final class CoarseTerrainRegion implements PathTerrainField {
    private static final byte FLAG_COLUMN_WATER = 1;
    private static final byte FLAG_WATER_BIOME = 1 << 1;
    private static final byte FLAG_NEAR_WATER = 1 << 2;

    private final CoarseRegionBounds bounds;
    private final int seaLevel;
    private final short[] heights;
    private final short[] oceanFloors;
    private final byte[] flags;
    private final int[] terrainArgb;

    public CoarseTerrainRegion(CoarseRegionBounds bounds,
                               int seaLevel,
                               short[] heights,
                               short[] oceanFloors,
                               byte[] flags,
                               int[] terrainArgb) {
        this.bounds = bounds;
        this.seaLevel = seaLevel;
        this.heights = heights;
        this.oceanFloors = oceanFloors;
        this.flags = flags;
        this.terrainArgb = terrainArgb;
        int expected = Math.toIntExact(bounds.sampleCount());
        if (heights.length != expected || oceanFloors.length != expected
                || flags.length != expected || terrainArgb.length != expected) {
            throw new IllegalArgumentException("coarse region arrays do not match bounds");
        }
    }

    public CoarseRegionBounds bounds() {
        return bounds;
    }

    public int terrainArgbAtIndex(int index) {
        return terrainArgb[index];
    }

    @Override
    public int seaLevel() {
        return seaLevel;
    }

    @Override
    public int height(int x, int z) {
        if (!contains(x, z)) return seaLevel;
        return heights[bounds.indexOfNearest(x, z)];
    }

    @Override
    public int oceanFloor(int x, int z) {
        if (!contains(x, z)) return seaLevel;
        return oceanFloors[bounds.indexOfNearest(x, z)];
    }

    @Override
    public boolean isColumnWater(int x, int z) {
        return contains(x, z) && hasFlag(bounds.indexOfNearest(x, z), FLAG_COLUMN_WATER);
    }

    @Override
    public boolean isNearWater(int x, int z, int neighborDistance) {
        if (!contains(x, z)) return false;
        if (hasFlag(bounds.indexOfNearest(x, z), FLAG_NEAR_WATER)) return true;
        int steps = Math.max(1, ceilDiv(Math.max(0, neighborDistance), bounds.step()));
        for (int dz = -steps; dz <= steps; dz++) {
            for (int dx = -steps; dx <= steps; dx++) {
                if (dx == 0 && dz == 0) continue;
                int wx = x + dx * bounds.step();
                int wz = z + dz * bounds.step();
                if (contains(wx, wz) && hasFlag(bounds.indexOfNearest(wx, wz), FLAG_COLUMN_WATER)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Holder<Biome> biome(int x, int z) {
        return null;
    }

    @Override
    public boolean contains(int x, int z) {
        return bounds.contains(x, z);
    }

    @Override
    public int step() {
        return bounds.step();
    }

    @Override
    public boolean isWaterBiome(int x, int z) {
        return contains(x, z) && hasFlag(bounds.indexOfNearest(x, z), FLAG_WATER_BIOME);
    }

    @Override
    public SampleBundle sampleBundle(int x, int z) {
        if (!contains(x, z)) {
            return new SampleBundle(seaLevel, seaLevel, false, false, 0);
        }
        int index = bounds.indexOfNearest(x, z);
        int ocean = oceanFloors[index];
        boolean columnWater = hasFlag(index, FLAG_COLUMN_WATER);
        boolean waterBiome = hasFlag(index, FLAG_WATER_BIOME);
        int depth = Math.max(0, seaLevel - ocean);
        return new SampleBundle(heights[index], ocean, columnWater, waterBiome, depth);
    }

    static byte flags(boolean columnWater, boolean waterBiome, boolean nearWater) {
        int value = 0;
        if (columnWater) value |= FLAG_COLUMN_WATER;
        if (waterBiome) value |= FLAG_WATER_BIOME;
        if (nearWater) value |= FLAG_NEAR_WATER;
        return (byte) value;
    }

    private boolean hasFlag(int index, byte flag) {
        return (flags[index] & flag) != 0;
    }

    private static int ceilDiv(int value, int divisor) {
        return Math.floorDiv(value + divisor - 1, divisor);
    }
}