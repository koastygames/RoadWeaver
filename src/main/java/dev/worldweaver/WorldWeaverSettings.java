package dev.worldweaver;

/**
 * Runtime-safe settings kept as JVM properties so WorldWeaver does not require
 * a config library or add another reload lifecycle to world generation.
 */
public final class WorldWeaverSettings {
    public static final boolean HEIGHT_CACHE_ENABLED =
            !Boolean.getBoolean("worldweaver.disableHeightCache");

    public static final int HEIGHT_CACHE_MAX_ENTRIES = clamp(
            Integer.getInteger("worldweaver.heightCacheMaxEntries", 65_536),
            1_024,
            1_048_576);

    public static final boolean LOG_SLOW_WORLDGEN =
            Boolean.getBoolean("worldweaver.logSlowWorldgen");

    public static final long SLOW_WORLDGEN_MS = clamp(
            Long.getLong("worldweaver.slowWorldgenMs", 250L),
            25L,
            60_000L);

    private WorldWeaverSettings() {
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
