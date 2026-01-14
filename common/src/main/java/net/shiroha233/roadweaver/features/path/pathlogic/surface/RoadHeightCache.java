package net.shiroha233.roadweaver.features.path.pathlogic.surface;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 道路高度缓存服务 - 跨区块高度衔接
 * 
 * 核心问题：
 * - 道路跨越多个区块，但区块生成顺序不确定
 * - 无法在当前区块获取下一个区块的实际地形高度
 * - 导致相邻区块的道路高度不连贯
 * 
 * 解决方案：
 * - 每个区块放置道路后，缓存该区块内道路的边界高度
 * - 下一个区块生成时，查询相邻区块的边界高度进行衔接
 */
public final class RoadHeightCache {
    private RoadHeightCache() {}
    
    // 缓存：维度 -> (道路指纹 + 区块位置 + 方向) -> 边界高度
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> dimensionCaches = new ConcurrentHashMap<>();
    
    // 缓存：2D坐标 -> 已放置的道路高度
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> placedHeightCaches = new ConcurrentHashMap<>();
    
    public enum Direction {
        NORTH, SOUTH, EAST, WEST
    }
    
    public static void cacheBoundaryHeight(ServerLevel level, long roadId, ChunkPos chunkPos, Direction direction, int height) {
        String dimKey = level.dimension().location().toString();
        var cache = dimensionCaches.computeIfAbsent(dimKey, k -> new ConcurrentHashMap<>());
        long key = packKey(roadId, chunkPos, direction);
        cache.put(key, height);
    }
    
    public static Integer getAdjacentBoundaryHeight(ServerLevel level, long roadId, ChunkPos chunkPos, Direction direction) {
        String dimKey = level.dimension().location().toString();
        var cache = dimensionCaches.get(dimKey);
        if (cache == null) return null;
        
        ChunkPos adjacentChunk = getAdjacentChunk(chunkPos, direction);
        Direction oppositeDir = getOppositeDirection(direction);
        
        long key = packKey(roadId, adjacentChunk, oppositeDir);
        return cache.get(key);
    }
    
    public static void cachePlacedHeight(ServerLevel level, int x, int z, int height) {
        String dimKey = level.dimension().location().toString();
        var cache = placedHeightCaches.computeIfAbsent(dimKey, k -> new ConcurrentHashMap<>());
        long key = packXZ(x, z);
        cache.put(key, height);
    }
    
    public static Integer getPlacedHeight(ServerLevel level, int x, int z) {
        String dimKey = level.dimension().location().toString();
        var cache = placedHeightCaches.get(dimKey);
        if (cache == null) return null;
        return cache.get(packXZ(x, z));
    }
    
    public static Integer getNearbyPlacedHeight(ServerLevel level, int x, int z, int radius) {
        String dimKey = level.dimension().location().toString();
        var cache = placedHeightCaches.get(dimKey);
        if (cache == null) return null;
        
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    Integer h = cache.get(packXZ(x + dx, z + dz));
                    if (h != null) return h;
                }
            }
        }
        return null;
    }
    
    public static void clearDimension(ServerLevel level) {
        String dimKey = level.dimension().location().toString();
        var cache = dimensionCaches.remove(dimKey);
        if (cache != null) cache.clear();
        var placedCache = placedHeightCaches.remove(dimKey);
        if (placedCache != null) placedCache.clear();
    }
    
    public static void clearAll() {
        dimensionCaches.clear();
        placedHeightCaches.clear();
    }
    
    private static long packKey(long roadId, ChunkPos chunk, Direction dir) {
        return (roadId & 0xFFFFFFFFL) << 32 
             | ((long)(chunk.x & 0xFFFF)) << 16 
             | ((long)(chunk.z & 0xFFFF)) << 2 
             | (dir.ordinal() & 0x3);
    }
    
    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
    
    private static ChunkPos getAdjacentChunk(ChunkPos chunk, Direction dir) {
        switch (dir) {
            case NORTH: return new ChunkPos(chunk.x, chunk.z - 1);
            case SOUTH: return new ChunkPos(chunk.x, chunk.z + 1);
            case EAST: return new ChunkPos(chunk.x + 1, chunk.z);
            case WEST: return new ChunkPos(chunk.x - 1, chunk.z);
            default: return chunk;
        }
    }
    
    private static Direction getOppositeDirection(Direction dir) {
        switch (dir) {
            case NORTH: return Direction.SOUTH;
            case SOUTH: return Direction.NORTH;
            case EAST: return Direction.WEST;
            case WEST: return Direction.EAST;
            default: return dir;
        }
    }
}
