package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 精确高度采样器：使用 ChunkGenerator#getBaseHeight 对路径节点进行二次精采样
 */
public final class AccurateHeightSampler {
    private final ServerLevel level;
    private final ChunkGenerator generator;
    private final RandomState randomState;

    private final ConcurrentHashMap<Long, Integer> motionBlockingCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> worldSurfaceCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> oceanFloorCache = new ConcurrentHashMap<>();

    private static long hashXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private AccurateHeightSampler(ServerLevel level, ChunkGenerator generator, RandomState randomState) {
        this.level = level;
        this.generator = generator;
        this.randomState = randomState;
    }

    public static AccurateHeightSampler create(ServerLevel level) {
        var chunkSource = level.getChunkSource();
        return new AccurateHeightSampler(
                level,
                chunkSource.getGenerator(),
                chunkSource.getGeneratorState().randomState());
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
        int h = generator.getBaseHeight(x, z, type, level, randomState);
        cache.put(key, h);
        return h;
    }

    public void clear() {
        motionBlockingCache.clear();
        worldSurfaceCache.clear();
        oceanFloorCache.clear();
    }

    /**
     * 对原始折线路径节点进行精采样，未采样点做线性插值
     */
    public List<BlockPos> samplePathHeights(List<BlockPos> path) {
        if (path == null || path.isEmpty()) return path;

        int n = path.size();
        int stride;
        if (n <= 512) stride = 1;
        else if (n <= 2048) stride = 2;
        else if (n <= 8192) stride = 4;
        else stride = 8;

        int[] sampledY = new int[n];
        boolean[] sampled = new boolean[n];

        for (int i = 0; i < n; i += stride) {
            BlockPos p = path.get(i);
            sampledY[i] = surfaceHeight(p.getX(), p.getZ());
            sampled[i] = true;
        }

        int lastIdx = n - 1;
        if (!sampled[lastIdx]) {
            BlockPos p = path.get(lastIdx);
            sampledY[lastIdx] = surfaceHeight(p.getX(), p.getZ());
            sampled[lastIdx] = true;
        }

        int prev = -1;
        for (int i = 0; i < n; i++) {
            if (!sampled[i]) continue;
            if (prev >= 0 && i > prev + 1) {
                int y0 = sampledY[prev];
                int y1 = sampledY[i];
                int span = i - prev;
                for (int j = prev + 1; j < i; j++) {
                    double t = (j - prev) / (double) span;
                    sampledY[j] = (int) Math.round(y0 + (y1 - y0) * t);
                }
            }
            prev = i;
        }

        List<BlockPos> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BlockPos p = path.get(i);
            out.add(new BlockPos(p.getX(), sampledY[i], p.getZ()));
        }
        return out;
    }
}
