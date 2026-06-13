package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 地图快照缓存 - 按存档隔离的线程安全缓存管理
 */
public final class MapSnapshotCache {
    private static final ConcurrentHashMap<String, ConcurrentHashMap<ResourceLocation, MapSnapshot>> BY_WORLD = new ConcurrentHashMap<>();
    private static volatile String currentWorldId = null;
    private static final AtomicInteger CLEAR_SEQ = new AtomicInteger();

    private MapSnapshotCache() {}

    public static void setCurrentWorldId(String worldId) {
        currentWorldId = worldId;
    }

    public static String getCurrentWorldId() {
        return currentWorldId;
    }

    public static MapSnapshot peek(ResourceLocation dimensionId) {
        if (dimensionId == null) return null;
        String wid = currentWorldId;
        if (wid == null) return null;
        ConcurrentHashMap<ResourceLocation, MapSnapshot> bucket = BY_WORLD.get(wid);
        if (bucket == null) return null;
        return bucket.get(dimensionId);
    }

    public static void put(ResourceLocation dimensionId, MapSnapshot s) {
        if (dimensionId == null) return;
        String wid = currentWorldId;
        if (wid == null) return;
        ConcurrentHashMap<ResourceLocation, MapSnapshot> bucket = BY_WORLD.computeIfAbsent(wid, k -> new ConcurrentHashMap<>());
        if (s == null) bucket.remove(dimensionId);
        else bucket.put(dimensionId, s);
    }

    public static void scheduleClear(long delayMs) {
        int token = CLEAR_SEQ.incrementAndGet();
        long d = Math.max(0L, delayMs);
        Executor delayed = CompletableFuture.delayedExecutor(d, TimeUnit.MILLISECONDS);
        CompletableFuture.runAsync(() -> {
            if (CLEAR_SEQ.get() == token) {
                clearCurrentWorld();
            }
        }, delayed);
    }

    public static void cancelClear() {
        CLEAR_SEQ.incrementAndGet();
    }

    public static void clearNow() {
        CLEAR_SEQ.incrementAndGet();
        clearCurrentWorld();
    }

    public static void clearAll() {
        CLEAR_SEQ.incrementAndGet();
        BY_WORLD.clear();
    }

    public static void clearWorld(String worldId) {
        if (worldId == null) return;
        BY_WORLD.remove(worldId);
    }

    private static void clearCurrentWorld() {
        String wid = currentWorldId;
        if (wid != null) {
            BY_WORLD.remove(wid);
        }
    }
}


