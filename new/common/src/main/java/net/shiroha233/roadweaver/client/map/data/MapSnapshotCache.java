package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 地图快照缓存 - 线程安全的缓存管理
 */
public final class MapSnapshotCache {
    private static final ConcurrentHashMap<ResourceLocation, MapSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
    private static final AtomicInteger CLEAR_SEQ = new AtomicInteger();

    private MapSnapshotCache() {}

    public static MapSnapshot peek(ResourceLocation dimensionId) {
        if (dimensionId == null) return null;
        return SNAPSHOTS.get(dimensionId);
    }

    public static void put(ResourceLocation dimensionId, MapSnapshot s) {
        if (dimensionId == null) return;
        if (s == null) SNAPSHOTS.remove(dimensionId);
        else SNAPSHOTS.put(dimensionId, s);
    }
    
    public static void scheduleClear(long delayMs) {
        int token = CLEAR_SEQ.incrementAndGet();
        long d = Math.max(0L, delayMs);
        Executor delayed = CompletableFuture.delayedExecutor(d, TimeUnit.MILLISECONDS);
        CompletableFuture.runAsync(() -> {
            if (CLEAR_SEQ.get() == token) {
                SNAPSHOTS.clear();
            }
        }, delayed);
    }
    
    public static void cancelClear() {
        CLEAR_SEQ.incrementAndGet();
    }
    
    public static void clearNow() {
        CLEAR_SEQ.incrementAndGet();
        SNAPSHOTS.clear();
    }
}


