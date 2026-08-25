package dev.worldweaver;

import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.atomic.LongAdder;

/**
 * Very low-overhead counters used by the pipeline hooks. Logging is opt-in so
 * profiling cannot become a source of chunk-generation stutter by itself.
 */
public final class WorldgenMetrics {
    private static final LongAdder HEIGHT_CACHE_HITS = new LongAdder();
    private static final LongAdder HEIGHT_CACHE_MISSES = new LongAdder();
    private static final LongAdder TERRAIN_CHUNKS = new LongAdder();
    private static final LongAdder TERRAIN_NANOS = new LongAdder();
    private static final LongAdder DECORATION_CHUNKS = new LongAdder();
    private static final LongAdder DECORATION_NANOS = new LongAdder();
    private static final LongAdder STRUCTURE_PLAN_CHUNKS = new LongAdder();
    private static final LongAdder STRUCTURE_PLAN_NANOS = new LongAdder();

    private WorldgenMetrics() {
    }

    public static void cacheHit() {
        HEIGHT_CACHE_HITS.increment();
    }

    public static void cacheMiss() {
        HEIGHT_CACHE_MISSES.increment();
    }

    public static void recordTerrain(ChunkPos pos, long nanos, Throwable error) {
        TERRAIN_CHUNKS.increment();
        TERRAIN_NANOS.add(nanos);
        if (error != null) {
            WorldWeaver.LOGGER.error("Terrain generation failed for chunk {}", pos, error);
            return;
        }
        maybeLogSlow("terrain", pos, nanos);
    }

    public static void recordDecoration(ChunkPos pos, long nanos) {
        DECORATION_CHUNKS.increment();
        DECORATION_NANOS.add(nanos);
        maybeLogSlow("decoration/structure placement", pos, nanos);
    }

    public static void recordStructurePlanning(ChunkPos pos, long nanos) {
        STRUCTURE_PLAN_CHUNKS.increment();
        STRUCTURE_PLAN_NANOS.add(nanos);
        maybeLogSlow("structure planning", pos, nanos);
    }

    private static void maybeLogSlow(String phase, ChunkPos pos, long nanos) {
        if (!WorldWeaverSettings.LOG_SLOW_WORLDGEN) {
            return;
        }
        long millis = nanos / 1_000_000L;
        if (millis >= WorldWeaverSettings.SLOW_WORLDGEN_MS) {
            WorldWeaver.LOGGER.warn("Slow WorldWeaver-observed {} phase: {} ms at chunk {}", phase, millis, pos);
        }
    }

    public static String summary() {
        return "heightCacheHits=" + HEIGHT_CACHE_HITS.sum()
                + ", heightCacheMisses=" + HEIGHT_CACHE_MISSES.sum()
                + ", terrainChunks=" + TERRAIN_CHUNKS.sum()
                + ", terrainMs=" + TERRAIN_NANOS.sum() / 1_000_000L
                + ", decorationChunks=" + DECORATION_CHUNKS.sum()
                + ", decorationMs=" + DECORATION_NANOS.sum() / 1_000_000L
                + ", structurePlanChunks=" + STRUCTURE_PLAN_CHUNKS.sum()
                + ", structurePlanMs=" + STRUCTURE_PLAN_NANOS.sum() / 1_000_000L;
    }
}
