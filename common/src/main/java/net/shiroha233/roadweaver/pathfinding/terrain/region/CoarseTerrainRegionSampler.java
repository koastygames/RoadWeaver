package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.map.tile.render.TerrainTilePalette;
import net.shiroha233.roadweaver.pathfinding.cache.FastHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;


/**
 * 构建规划区域级粗采样数组。
 */
public final class CoarseTerrainRegionSampler {
    private CoarseTerrainRegionSampler() {}

    public static CoarseTerrainRegion sample(ServerLevel level,
                                             int minBlockX,
                                             int minBlockZ,
                                             int maxBlockX,
                                             int maxBlockZ,
                                             int step) {
        if (level == null) {
            throw new IllegalArgumentException("level must not be null");
        }
        int safeStep = Math.max(RoadConstants.ASTAR_STEP_MIN, step);
        CoarseRegionBounds bounds = CoarseRegionBounds.aligned(
                level.dimension().location(), minBlockX, minBlockZ, maxBlockX, maxBlockZ, safeStep);
        if (bounds.sampleCount() > RoadConstants.COARSE_REGION_MAX_SAMPLES) {
            throw new IllegalArgumentException("coarse region sample count too large: " + bounds.sampleCount());
        }

        int size = Math.toIntExact(bounds.sampleCount());
        short[] heights = new short[size];
        short[] oceanFloors = new short[size];
        byte[] flags = new byte[size];
        int[] terrainArgb = new int[size];

        int seaLevel = level.getSeaLevel();
        sampleTerrain(level, bounds, seaLevel, heights, oceanFloors, flags, terrainArgb);
        markNearWater(bounds, flags);
        return new CoarseTerrainRegion(bounds, seaLevel, heights, oceanFloors, flags, terrainArgb);
    }

    private static void markNearWater(CoarseRegionBounds bounds, byte[] flags) {
        int radiusSamples = Math.max(1, RoadConstants.CHUNK_SIZE_BLOCKS / Math.max(1, bounds.step()));
        byte[] next = flags.clone();
        markNearWaterRows(bounds, flags, next, radiusSamples, 0, bounds.height());
        System.arraycopy(next, 0, flags, 0, flags.length);
    }

    private static void markNearWaterRows(CoarseRegionBounds bounds,
                                          byte[] flags,
                                          byte[] next,
                                          int radiusSamples,
                                          int fromZ,
                                          int toZ) {
        for (int iz = fromZ; iz < toZ; iz++) {
            if (Thread.currentThread().isInterrupted()) return;
            for (int ix = 0; ix < bounds.width(); ix++) {
                int index = iz * bounds.width() + ix;
                if ((flags[index] & 1) != 0) {
                    next[index] = (byte) (next[index] | 4);
                    continue;
                }
                boolean nearWater = false;
                for (int dz = -radiusSamples; dz <= radiusSamples && !nearWater; dz++) {
                    int nz = iz + dz;
                    if (nz < 0 || nz >= bounds.height()) continue;
                    for (int dx = -radiusSamples; dx <= radiusSamples; dx++) {
                        int nx = ix + dx;
                        if (nx < 0 || nx >= bounds.width()) continue;
                        int ni = nz * bounds.width() + nx;
                        if ((flags[ni] & 1) != 0) {
                            nearWater = true;
                            break;
                        }
                    }
                }
                if (nearWater) {
                    next[index] = (byte) (next[index] | 4);
                }
            }
        }
    }

    private static void sampleTerrain(ServerLevel level,
                                      CoarseRegionBounds bounds,
                                      int seaLevel,
                                      short[] heights,
                                      short[] oceanFloors,
                                      byte[] flags,
                                      int[] terrainArgb) {
        sampleRows(level, bounds, seaLevel, heights, oceanFloors, flags, terrainArgb, 0, bounds.height());
    }

    private static void sampleRows(ServerLevel level,
                                   CoarseRegionBounds bounds,
                                   int seaLevel,
                                   short[] heights,
                                   short[] oceanFloors,
                                   byte[] flags,
                                   int[] terrainArgb,
                                   int fromZ,
                                   int toZ) {
        FastHeightSampler fastSampler = FastHeightSampler.create(level);
        TerrainSamplingCache terrainCache = new TerrainSamplingCache();
        try {
            for (int iz = fromZ; iz < toZ; iz++) {
                if (Thread.currentThread().isInterrupted()) return;
                int z = bounds.blockZAt(iz);
                for (int ix = 0; ix < bounds.width(); ix++) {
                    int x = bounds.blockXAt(ix);
                    int index = iz * bounds.width() + ix;
                    int height = fastSampler.sampleHeight(x, z);
                    int oceanFloor = height;
                    Holder<Biome> biome = terrainCache.getBiome(level, x, z);
                    boolean waterBiome = isWaterBiome(biome);
                    boolean columnWater = waterBiome && oceanFloor < seaLevel;
                    boolean nearWater = columnWater;
                    heights[index] = toShort(height);
                    oceanFloors[index] = toShort(oceanFloor);
                    flags[index] = CoarseTerrainRegion.flags(columnWater, waterBiome, nearWater);
                    terrainArgb[index] = TerrainTilePalette.colorFor(
                            biome,
                            height,
                            seaLevel,
                            oceanFloor,
                            columnWater,
                            nearWater);
                }
            }
        } finally {
            fastSampler.clearCache();
            terrainCache.clear();
        }
    }

    private static boolean isWaterBiome(Holder<Biome> biome) {
        return biome != null
                && (biome.is(BiomeTags.IS_RIVER)
                || biome.is(BiomeTags.IS_OCEAN)
                || biome.is(BiomeTags.IS_DEEP_OCEAN));
    }

    private static short toShort(int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }
}