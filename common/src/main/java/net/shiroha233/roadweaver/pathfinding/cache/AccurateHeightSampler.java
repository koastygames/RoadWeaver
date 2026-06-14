package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 精确高度采样器
 */
public final class AccurateHeightSampler {
    private final ServerLevel level;
    private final ChunkGenerator generator;
    private final NoiseChunkHeightSampler noiseChunkSampler;

    private ConcurrentHashMap<Long, Integer> motionBlockingCache = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Long, Integer> worldSurfaceCache = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Long, Integer> oceanFloorCache = new ConcurrentHashMap<>();

    private static long hashXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private AccurateHeightSampler(ServerLevel level,
                                  ChunkGenerator generator,
                                  NoiseChunkHeightSampler noiseChunkSampler) {
        this.level = level;
        this.generator = generator;
        this.noiseChunkSampler = noiseChunkSampler;
    }

    public static AccurateHeightSampler create(ServerLevel level) {
        var chunkSource = level.getChunkSource();
        ChunkGenerator generator = chunkSource.getGenerator();
        NoiseChunkHeightSampler noiseChunkSampler = null;
        if (generator instanceof NoiseBasedChunkGenerator noiseGenerator) {
            noiseChunkSampler = NoiseChunkHeightSampler.create(
                    level,
                    noiseGenerator,
                    chunkSource.getGeneratorState().randomState());
        }
        return new AccurateHeightSampler(
                level,
                generator,
                noiseChunkSampler);
    }

    public int motionBlockingNoLeaves(int x, int z) {
        return sampleCached(motionBlockingCache, x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
    }

    public int worldSurfaceWg(int x, int z) {
        return sampleCached(worldSurfaceCache, x, z, Heightmap.Types.WORLD_SURFACE_WG);
    }

    public int oceanFloorWg(int x, int z) {
        return sampleCached(oceanFloorCache, x, z, Heightmap.Types.OCEAN_FLOOR_WG);
    }

    public int surfaceHeight(int x, int z) {
        int sea = level.getSeaLevel();
        int motion = motionBlockingNoLeaves(x, z);
        if (motion > sea + 2) return motion;
        return worldSurfaceWg(x, z);
    }

    private int sampleCached(ConcurrentHashMap<Long, Integer> cache, int x, int z, Heightmap.Types type) {
        long key = hashXZ(x, z);
        Integer cached = cache.get(key);
        if (cached != null) {
            AccurateSamplingStats.recordCacheHit();
            return cached;
        }
        AccurateSamplingStats.recordCacheMiss();
        int h;
        if (noiseChunkSampler != null) {
            h = switch (type) {
                case MOTION_BLOCKING_NO_LEAVES -> noiseChunkSampler.motionBlockingNoLeaves(x, z);
                case WORLD_SURFACE_WG -> noiseChunkSampler.worldSurfaceWg(x, z);
                case OCEAN_FLOOR_WG -> noiseChunkSampler.oceanFloorWg(x, z);
                default -> generator.getBaseHeight(x, z, type, level, level.getChunkSource().getGeneratorState().randomState());
            };
        } else {
            h = generator.getBaseHeight(x, z, type, level, level.getChunkSource().getGeneratorState().randomState());
        }
        cache.put(key, h);
        return h;
    }

    /**
     * 释放缓存。用新实例替换而非 clear()，确保内部 table 数组被 GC 回收。
     */
    public void clear() {
        motionBlockingCache = new ConcurrentHashMap<>();
        worldSurfaceCache = new ConcurrentHashMap<>();
        oceanFloorCache = new ConcurrentHashMap<>();
        if (noiseChunkSampler != null) {
            noiseChunkSampler.clear();
        }
    }

    public List<BlockPos> samplePathHeights(List<BlockPos> path, int divisor) {
        if (path == null || path.isEmpty()) return path;
        int div = Math.max(1, divisor);
        if (div == 1) {
            return sampleEveryNode(path);
        }
        return sampleWithSubdivision(path, div);
    }

    private List<BlockPos> sampleEveryNode(List<BlockPos> path) {
        List<BlockPos> out = new ArrayList<>(path.size());
        for (BlockPos p : path) {
            out.add(new BlockPos(p.getX(), surfaceHeight(p.getX(), p.getZ()), p.getZ()));
        }
        return out;
    }

    private List<BlockPos> sampleWithSubdivision(List<BlockPos> path, int divisor) {
        int n = path.size();
        if (n == 1) return sampleEveryNode(path);

        List<BlockPos> out = new ArrayList<>((n - 1) * divisor + 1);
        for (int i = 0; i < n - 1; i++) {
            BlockPos a = path.get(i);
            BlockPos b = path.get(i + 1);
            for (int k = 0; k < divisor; k++) {
                double t = (double) k / divisor;
                int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * t);
                int z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t);
                out.add(new BlockPos(x, surfaceHeight(x, z), z));
            }
        }
        BlockPos last = path.get(n - 1);
        out.add(new BlockPos(last.getX(), surfaceHeight(last.getX(), last.getZ()), last.getZ()));
        return out;
    }
}
