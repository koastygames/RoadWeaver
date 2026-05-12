/* 文件职责：管理九宫格地形采样的生命周期，提供异步采样和缓存淘汰。 */
package net.shiroha233.roadweaver.features.highway.terrain;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.util.ComputeService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highway 九宫格地形采样服务
 */
public final class HighwayTerrainSamplingService {
    private HighwayTerrainSamplingService() {}

    private static final ConcurrentHashMap<Long, HighwayCellTerrainField> CACHE = new ConcurrentHashMap<>();

    public static void resetAll() {
        CACHE.clear();
    }

    /**
     * 异步采样九宫格地形
     */
    public static CompletableFuture<Map<Long, HighwayCellTerrainField>> sampleNineGridAsync(
            ServerLevel level,
            TerrainSamplingCache cache,
            int centerCellGx,
            int centerCellGz,
            int gridBlocks) {
        return ComputeService.supplyAsync(() -> sampleNineGrid(level, cache, centerCellGx, centerCellGz, gridBlocks));
    }

    /**
     * 同步采样九宫格地形（在计算线程调用）
     */
    public static Map<Long, HighwayCellTerrainField> sampleNineGrid(
            ServerLevel level,
            TerrainSamplingCache cache,
            int centerCellGx,
            int centerCellGz,
            int gridBlocks) {
        Map<Long, HighwayCellTerrainField> result = new HashMap<>();

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (Thread.currentThread().isInterrupted()) return result;

                int gx = centerCellGx + dx;
                int gz = centerCellGz + dz;
                long cellKey = packCellKey(gx, gz);

                HighwayCellTerrainField cached = CACHE.get(cellKey);
                if (cached != null) {
                    result.put(cellKey, cached);
                    continue;
                }

                int cellMinX = gx * gridBlocks;
                int cellMinZ = gz * gridBlocks;
                HighwayCellTerrainField field = HighwayCellTerrainField.build(
                        level, cache, cellMinX, cellMinZ, gridBlocks);
                if (field != null) {
                    CACHE.put(cellKey, field);
                    result.put(cellKey, field);
                }
            }
        }

        evictOutsideWindow(centerCellGx, centerCellGz);
        return result;
    }

    /**
     * 获取已缓存的单元格地形
     */
    public static HighwayCellTerrainField getCached(int cellGx, int cellGz) {
        return CACHE.get(packCellKey(cellGx, cellGz));
    }

    private static void evictOutsideWindow(int centerGx, int centerGz) {
        CACHE.entrySet().removeIf(entry -> {
            long key = entry.getKey();
            int gx = (int) (key >> 32);
            int gz = (int) key;
            return Math.abs(gx - centerGx) > 1 || Math.abs(gz - centerGz) > 1;
        });
    }

    public static long packCellKey(int gx, int gz) {
        return ((long) gx << 32) | (gz & 0xFFFFFFFFL);
    }

    public static int cellKeyGx(long key) {
        return (int) (key >> 32);
    }

    public static int cellKeyGz(long key) {
        return (int) key;
    }
}
