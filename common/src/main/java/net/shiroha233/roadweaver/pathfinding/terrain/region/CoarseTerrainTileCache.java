package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.persistence.sqlite.CoarseTerrainTileSqliteStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 粗采样地形瓦片内存缓存和持久化加载入口。
 */
public final class CoarseTerrainTileCache {
    private CoarseTerrainTileCache() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final ConcurrentHashMap<CoarseTerrainTileKey, CompletableFuture<CoarseTerrainTile>> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<CoarseTerrainTileKey, CoarseTerrainTile> MEMORY = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CoarseTerrainTileKey, CoarseTerrainTile> eldest) {
            return size() > RoadConstants.COARSE_TERRAIN_TILE_CACHE_MAX_ENTRIES;
        }
    };

    public static CoarseTerrainTile getOrLoad(ServerLevel level, CoarseTerrainTileKey key) {
        if (level == null || key == null) return null;

        CoarseTerrainTile cached = getMemory(key);
        if (cached != null) return cached;

        CompletableFuture<CoarseTerrainTile> future = new CompletableFuture<>();
        CompletableFuture<CoarseTerrainTile> existing = IN_FLIGHT.putIfAbsent(key, future);
        if (existing != null) {
            try {
                return existing.join();
            } catch (Throwable ignored) {
                return null;
            }
        }

        try {
            CoarseTerrainTile loaded = loadOrSample(level, key);
            future.complete(loaded);
            return loaded;
        } catch (Throwable t) {
            future.completeExceptionally(t);
            LOGGER.warn("加载粗采样地形瓦片失败 tile=[{},{}]", key.tileX(), key.tileZ(), t);
            return null;
        } finally {
            IN_FLIGHT.remove(key, future);
        }
    }

    public static void clearAll() {
        synchronized (MEMORY) {
            MEMORY.clear();
        }
        IN_FLIGHT.clear();
    }

    private static CoarseTerrainTile loadOrSample(ServerLevel level, CoarseTerrainTileKey key) {
        CoarseTerrainTile cached = getMemory(key);
        if (cached != null) return cached;

        CoarseTerrainTile stored = CoarseTerrainTileSqliteStorage.loadTile(level, key);
        if (stored != null) {
            putMemory(key, stored);
            return stored;
        }

        CoarseTerrainTile sampled = CoarseTerrainTileSampler.sample(level, key);
        if (sampled != null) {
            CoarseTerrainTileSqliteStorage.saveTile(level, sampled);
            putMemory(key, sampled);
        }
        return sampled;
    }

    private static CoarseTerrainTile getMemory(CoarseTerrainTileKey key) {
        synchronized (MEMORY) {
            return MEMORY.get(key);
        }
    }

    private static void putMemory(CoarseTerrainTileKey key, CoarseTerrainTile tile) {
        synchronized (MEMORY) {
            MEMORY.put(key, tile);
        }
    }
}