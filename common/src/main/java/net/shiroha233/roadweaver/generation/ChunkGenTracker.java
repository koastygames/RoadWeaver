package net.shiroha233.roadweaver.generation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区块生成阶段追踪器
 * 
 * 只在区块首次生成阶段（WorldGenRegion）阻拦树木，
 * 生成完成后玩家种植的树木不受影响。
 * 
 * 原理：
 * - WorldGenRegion 是区块生成时使用的临时世界视图
 * - 如果 level 是 WorldGenRegion，说明正在进行区块生成
 * - 如果 level 是 ServerLevel，说明是玩家操作（树苗生长、骨粉等）
 */
public final class ChunkGenTracker {
    private ChunkGenTracker() {}
    
    // 正在生成中的区块（维度:区块坐标）
    // 使用 Set 而非 Map，因为只需要知道是否在生成中
    private static final Set<String> GENERATING_CHUNKS = ConcurrentHashMap.newKeySet();
    
    /**
     * 检查当前是否处于区块生成阶段
     * 
     * @param level 世界级别
     * @return 如果是区块生成阶段返回 true
     */
    public static boolean isWorldGenPhase(WorldGenLevel level) {
        // WorldGenRegion 是区块生成时的临时视图
        // 只有在这个阶段才需要阻拦树木
        return level instanceof WorldGenRegion;
    }
    
    /**
     * 从 WorldGenLevel 提取 ServerLevel
     */
    @SuppressWarnings("deprecation")
    public static ServerLevel extractServerLevel(WorldGenLevel level) {
        if (level instanceof ServerLevel sl) {
            return sl;
        }
        if (level instanceof WorldGenRegion region) {
            return region.getLevel();
        }
        return null;
    }
    
    /**
     * 标记区块开始生成（可选，用于更精细的控制）
     */
    public static void markGenerating(ServerLevel level, int chunkX, int chunkZ) {
        String key = makeKey(level, chunkX, chunkZ);
        GENERATING_CHUNKS.add(key);
    }
    
    /**
     * 标记区块生成完成
     */
    public static void markComplete(ServerLevel level, int chunkX, int chunkZ) {
        String key = makeKey(level, chunkX, chunkZ);
        GENERATING_CHUNKS.remove(key);
    }
    
    /**
     * 清理所有追踪数据（服务器关闭时调用）
     */
    public static void clearAll() {
        GENERATING_CHUNKS.clear();
    }
    
    private static String makeKey(ServerLevel level, int chunkX, int chunkZ) {
        return level.dimension().location() + ":" + chunkX + "," + chunkZ;
    }
}
