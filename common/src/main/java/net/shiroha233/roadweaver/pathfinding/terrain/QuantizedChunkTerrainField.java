/* 文件职责：将粗路径走廊覆盖的区块批量量化为普通道路搜索地形场。 */
package net.shiroha233.roadweaver.pathfinding.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于粗路径走廊按步长量化的普通道路搜索地形场。
 */
public final class QuantizedChunkTerrainField implements PathTerrainField {
    private final int step;
    private final int seaLevel;
    private final int minX;
    private final int minZ;
    private final int sizeX;
    private final int sizeZ;
    private final short[] heights;
    private final short[] oceanFloors;
    private final byte[] waterColumns;
    private final byte[] validCells;
    private final Holder<Biome>[] biomes;

    @SuppressWarnings("unchecked")
    private QuantizedChunkTerrainField(int step,
                                       int seaLevel,
                                       int minX,
                                       int minZ,
                                       int sizeX,
                                       int sizeZ) {
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
        this.validCells = new byte[size];
        this.biomes = (Holder<Biome>[]) new Holder<?>[size];
    }

    public static QuantizedChunkTerrainField build(ServerLevel level,
                                                   TerrainSamplingCache cache,
                                                   AccurateHeightSampler accurate,
                                                   List<BlockPos> coarsePath,
                                                   int step,
                                                   int chunkRadius) {
        if (coarsePath == null || coarsePath.isEmpty()) {
            return null;
        }

        int radius = Math.max(0, chunkRadius);
        Set<Long> chunks = new HashSet<>();
        int minChunkX = Integer.MAX_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;
        for (BlockPos pos : coarsePath) {
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int cx = chunkX + dx;
                    int cz = chunkZ + dz;
                    long key = ChunkPos.asLong(cx, cz);
                    if (chunks.add(key)) {
                        if (cx < minChunkX) minChunkX = cx;
                        if (cx > maxChunkX) maxChunkX = cx;
                        if (cz < minChunkZ) minChunkZ = cz;
                        if (cz > maxChunkZ) maxChunkZ = cz;
                    }
                }
            }
        }

        if (chunks.isEmpty()) {
            return null;
        }

        int minWorldX = minChunkX << 4;
        int minWorldZ = minChunkZ << 4;
        int maxX = ((maxChunkX + 1) << 4) - 1;
        int maxZ = ((maxChunkZ + 1) << 4) - 1;
        int minX = snapToGrid(minWorldX, step);
        int minZ = snapToGrid(minWorldZ, step);
        int sizeX = ((maxX - minX) / step) + 1;
        int sizeZ = ((maxZ - minZ) / step) + 1;

        int seaLevel = level.getSeaLevel();
        QuantizedChunkTerrainField field = new QuantizedChunkTerrainField(step, seaLevel, minX, minZ, sizeX, sizeZ);

        for (long chunkKey : chunks) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            int chunkMinX = chunkX << 4;
            int chunkMinZ = chunkZ << 4;
            int chunkMaxX = chunkMinX + 15;
            int chunkMaxZ = chunkMinZ + 15;

            int gridStartX = Math.max(0, ceilDiv(chunkMinX - minX, step));
            int gridEndX = Math.min(sizeX - 1, floorDiv(chunkMaxX - minX, step));
            int gridStartZ = Math.max(0, ceilDiv(chunkMinZ - minZ, step));
            int gridEndZ = Math.min(sizeZ - 1, floorDiv(chunkMaxZ - minZ, step));

            for (int gz = gridStartZ; gz <= gridEndZ; gz++) {
                int worldZ = minZ + gz * step;
                for (int gx = gridStartX; gx <= gridEndX; gx++) {
                    int worldX = minX + gx * step;
                    int index = field.index(gx, gz);
                    int motion = accurate.motionBlockingNoLeaves(worldX, worldZ);
                    int worldSurface = accurate.worldSurfaceWg(worldX, worldZ);
                    int oceanFloor = accurate.oceanFloorWg(worldX, worldZ);
                    int height = motion > seaLevel + 2 ? motion : worldSurface;
                    Holder<Biome> biome = cache.getBiome(level, worldX, worldZ);
                    boolean biomeWater = biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN);
                    boolean columnWater = (biomeWater && oceanFloor < seaLevel)
                            || ((height <= seaLevel + 1) && (oceanFloor < height - 1));

                    field.heights[index] = (short) height;
                    field.oceanFloors[index] = (short) oceanFloor;
                    field.waterColumns[index] = (byte) (columnWater ? 1 : 0);
                    field.validCells[index] = 1;
                    field.biomes[index] = biome;
                }
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
        if (isColumnWater(x, z)) {
            return true;
        }
        int d = Math.max(step, alignDistance(neighborDistance));
        int[][] offsets = {
                {d, 0}, {-d, 0}, {0, d}, {0, -d},
                {d, d}, {d, -d}, {-d, d}, {-d, -d}
        };
        for (int[] off : offsets) {
            int nx = x + off[0];
            int nz = z + off[1];
            if (contains(nx, nz) && isColumnWater(nx, nz)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Holder<Biome> biome(int x, int z) {
        return biomes[indexForWorld(x, z)];
    }

    @Override
    public boolean contains(int x, int z) {
        int gx = gridX(x);
        int gz = gridZ(z);
        return gx >= 0 && gx < sizeX && gz >= 0 && gz < sizeZ && validCells[index(gx, gz)] != 0;
    }

    @Override
    public int step() {
        return step;
    }

    public List<BlockPos> gridPositions() {
        List<BlockPos> out = new java.util.ArrayList<>(sizeX * sizeZ);
        for (int gz = 0; gz < sizeZ; gz++) {
            int worldZ = minZ + gz * step;
            for (int gx = 0; gx < sizeX; gx++) {
                int idx = index(gx, gz);
                if (validCells[idx] == 0) {
                    continue;
                }
                int worldX = minX + gx * step;
                out.add(new BlockPos(worldX, heights[idx], worldZ));
            }
        }
        return out;
    }

    private int indexForWorld(int x, int z) {
        return index(gridX(x), gridZ(z));
    }

    private int gridX(int x) {
        return floorDiv(x - minX, step);
    }

    private int gridZ(int z) {
        return floorDiv(z - minZ, step);
    }

    private int index(int gx, int gz) {
        if (gx < 0 || gx >= sizeX || gz < 0 || gz >= sizeZ) {
            throw new IllegalArgumentException("Terrain field out of bounds: " + gx + "," + gz);
        }
        return gz * sizeX + gx;
    }

    private int alignDistance(int distance) {
        int cells = Math.max(1, (int) Math.ceil(distance / (double) step));
        return cells * step;
    }

    private static int floorDiv(int value, int step) {
        return Math.floorDiv(value, step);
    }

    private static int ceilDiv(int value, int step) {
        return -Math.floorDiv(-value, step);
    }

    private static int snapToGrid(int value, int step) {
        return Math.floorDiv(value, step) * step;
    }
}
