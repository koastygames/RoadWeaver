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
 */
public final class ChunkGenTracker {
    private ChunkGenTracker() {}
    
    private static final Set<String> GENERATING_CHUNKS = ConcurrentHashMap.newKeySet();
    
    public static boolean isWorldGenPhase(WorldGenLevel level) {
        return level instanceof WorldGenRegion;
    }
    
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
    
    public static void markGenerating(ServerLevel level, int chunkX, int chunkZ) {
        String key = makeKey(level, chunkX, chunkZ);
        GENERATING_CHUNKS.add(key);
    }
    
    public static void markComplete(ServerLevel level, int chunkX, int chunkZ) {
        String key = makeKey(level, chunkX, chunkZ);
        GENERATING_CHUNKS.remove(key);
    }
    
    public static void clearAll() {
        GENERATING_CHUNKS.clear();
    }
    
    private static String makeKey(ServerLevel level, int chunkX, int chunkZ) {
        return level.dimension().location() + ":" + chunkX + "," + chunkZ;
    }
}
