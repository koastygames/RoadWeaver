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
 * - 使用道路ID + 2D坐标作为缓存键，支持多条道路交叉
 * 
 * 缓存结构：
 * - 键：roadId + chunkPos + direction (NORTH/SOUTH/EAST/WEST)
 * - 值：边界处的道路高度
 */
public final class RoadHeightCache {
    private RoadHeightCache() {}
    
    // 缓存：维度 -> (道路指纹 + 区块位置 + 方向) -> 边界高度
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> dimensionCaches = new ConcurrentHashMap<>();
    
    // 缓存：2D坐标 -> 已放置的道路高度（用于同一区块内的衔接）
    private static final ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> placedHeightCaches = new ConcurrentHashMap<>();
    
    /**
     * 边界方向
     */
    public enum Direction {
        NORTH,  // Z-
        SOUTH,  // Z+
        EAST,   // X+
        WEST    // X-
    }
    
    /**
     * 缓存区块边界处的道路高度
     * 
     * @param level     服务端世界
     * @param roadId    道路指纹（用于区分不同道路）
     * @param chunkPos  区块位置
     * @param direction 边界方向
     * @param height    边界处的道路高度
     */
    public static void cacheBoundaryHeight(ServerLevel level, long roadId, ChunkPos chunkPos, Direction direction, int height) {
        String dimKey = level.dimension().location().toString();
        var cache = dimensionCaches.computeIfAbsent(dimKey, k -> new ConcurrentHashMap<>());
        long key = packKey(roadId, chunkPos, direction);
        cache.put(key, height);
    }
    
    /**
     * 查询相邻区块的边界高度
     * 
     * @param level     服务端世界
     * @param roadId    道路指纹
     * @param chunkPos  当前区块位置
     * @param direction 查询方向（从当前区块看向相邻区块）
     * @return 相邻区块边界处的道路高度，如果未缓存则返回 null
     */
    public static Integer getAdjacentBoundaryHeight(ServerLevel level, long roadId, ChunkPos chunkPos, Direction direction) {
        String dimKey = level.dimension().location().toString();
        var cache = dimensionCaches.get(dimKey);
        if (cache == null) return null;
        
        // 计算相邻区块的位置和对应的边界方向
        ChunkPos adjacentChunk = getAdjacentChunk(chunkPos, direction);
        Direction oppositeDir = getOppositeDirection(direction);
        
        long key = packKey(roadId, adjacentChunk, oppositeDir);
        return cache.get(key);
    }
    
    /**
     * 缓存已放置的道路高度（用于同一区块内的衔接）
     */
    public static void cachePlacedHeight(ServerLevel level, int x, int z, int height) {
        String dimKey = level.dimension().location().toString();
        var cache = placedHeightCaches.computeIfAbsent(dimKey, k -> new ConcurrentHashMap<>());
        long key = packXZ(x, z);
        cache.put(key, height);
    }
    
    /**
     * 查询已放置的道路高度
     */
    public static Integer getPlacedHeight(ServerLevel level, int x, int z) {
        String dimKey = level.dimension().location().toString();
        var cache = placedHeightCaches.get(dimKey);
        if (cache == null) return null;
        return cache.get(packXZ(x, z));
    }
    
    /**
     * 查询指定位置附近的已放置道路高度（用于衔接）
     * 
     * @param level  服务端世界
     * @param x      X坐标
     * @param z      Z坐标
     * @param radius 搜索半径
     * @return 最近的已放置高度，如果未找到则返回 null
     */
    public static Integer getNearbyPlacedHeight(ServerLevel level, int x, int z, int radius) {
        String dimKey = level.dimension().location().toString();
        var cache = placedHeightCaches.get(dimKey);
        if (cache == null) return null;
        
        // 从近到远搜索
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // 只搜索边界
                    Integer h = cache.get(packXZ(x + dx, z + dz));
                    if (h != null) return h;
                }
            }
        }
        return null;
    }
    
    /**
     * 清理指定维度的缓存
     */
    public static void clearDimension(ServerLevel level) {
        String dimKey = level.dimension().location().toString();
        var cache = dimensionCaches.remove(dimKey);
        if (cache != null) cache.clear();
        var placedCache = placedHeightCaches.remove(dimKey);
        if (placedCache != null) placedCache.clear();
    }
    
    /**
     * 清理所有缓存
     */
    public static void clearAll() {
        dimensionCaches.clear();
        placedHeightCaches.clear();
    }
    
    // 打包缓存键
    private static long packKey(long roadId, ChunkPos chunk, Direction dir) {
        // roadId 的低 32 位 + chunk 的 16 位 X + 16 位 Z + 2 位方向
        return (roadId & 0xFFFFFFFFL) << 32 
             | ((long)(chunk.x & 0xFFFF)) << 16 
             | ((long)(chunk.z & 0xFFFF)) << 2 
             | (dir.ordinal() & 0x3);
    }
    
    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
    
    private static ChunkPos getAdjacentChunk(ChunkPos chunk, Direction dir) {
        return switch (dir) {
            case NORTH -> new ChunkPos(chunk.x, chunk.z - 1);
            case SOUTH -> new ChunkPos(chunk.x, chunk.z + 1);
            case EAST -> new ChunkPos(chunk.x + 1, chunk.z);
            case WEST -> new ChunkPos(chunk.x - 1, chunk.z);
        };
    }
    
    private static Direction getOppositeDirection(Direction dir) {
        return switch (dir) {
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
        };
    }
}
