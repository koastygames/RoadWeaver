package net.shiroha233.roadweaver.persistence.sharded;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.persistence.sqlite.SignTextSqliteStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路牌文本延迟写入存储门面
 */
public final class SignTextShardStorage {
    private SignTextShardStorage() {}

    public static final int TYPE_DISTANCE = SignTextSqliteStorage.TYPE_DISTANCE;
    public static final int TYPE_SEA_QUESTION = SignTextSqliteStorage.TYPE_SEA_QUESTION;
    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, ConcurrentHashMap<Long, PendingSignWrite>>> MEMORY_PENDING = new ConcurrentHashMap<>();

    public record PendingSignWrite(BlockPos pos, int signType, String payload, int retryCount, long firstQueuedAtMs) {
        public PendingSignWrite withRetry() {
            return new PendingSignWrite(pos, signType, payload, retryCount + 1, firstQueuedAtMs);
        }

        private SignTextSqliteStorage.PendingSignWrite toPersistentWrite() {
            return new SignTextSqliteStorage.PendingSignWrite(pos, signType, payload);
        }
    }

    public static void upsert(ServerLevel level, BlockPos pos, int signType, String payload) {
        if (level == null || pos == null) return;
        putMemory(level, new PendingSignWrite(pos, signType, payload, 0, System.currentTimeMillis()));
    }

    public static void upsertBatch(ServerLevel level, Collection<PendingSignWrite> writes) {
        if (level == null || writes == null || writes.isEmpty()) return;
        ArrayList<SignTextSqliteStorage.PendingSignWrite> batch = new ArrayList<>(writes.size());
        for (PendingSignWrite write : writes) {
            if (write == null || write.pos() == null) continue;
            batch.add(write.toPersistentWrite());
        }
        SignTextSqliteStorage.upsertBatch(level, batch);
    }

    public static List<SignTextSqliteStorage.PendingSignText> queryByChunk(ServerLevel level, int chunkX, int chunkZ, int limit) {
        return SignTextSqliteStorage.queryByChunk(level, chunkX, chunkZ, limit);
    }

    public static void deleteByIds(ServerLevel level, List<Long> ids) {
        SignTextSqliteStorage.deleteByIds(level, ids);
    }

    public static List<PendingSignWrite> takeMemoryByChunk(ServerLevel level, int chunkX, int chunkZ, int limit) {
        if (level == null || limit <= 0) return List.of();
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, PendingSignWrite>> levelBuckets = MEMORY_PENDING.get(level);
        if (levelBuckets == null) return List.of();
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        ConcurrentHashMap<Long, PendingSignWrite> chunkBucket = levelBuckets.get(chunkKey);
        if (chunkBucket == null || chunkBucket.isEmpty()) return List.of();

        ArrayList<PendingSignWrite> out = new ArrayList<>(Math.min(limit, chunkBucket.size()));
        for (var entry : chunkBucket.entrySet()) {
            if (out.size() >= limit) break;
            if (chunkBucket.remove(entry.getKey(), entry.getValue())) {
                out.add(entry.getValue());
            }
        }
        if (chunkBucket.isEmpty()) {
            levelBuckets.remove(chunkKey, chunkBucket);
        }
        if (levelBuckets.isEmpty()) {
            MEMORY_PENDING.remove(level, levelBuckets);
        }
        return out;
    }

    public static void requeue(ServerLevel level, PendingSignWrite write) {
        if (level == null || write == null || write.pos() == null) return;
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, PendingSignWrite>> levelBuckets =
                MEMORY_PENDING.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        long chunkKey = ChunkPos.asLong(write.pos().getX() >> 4, write.pos().getZ() >> 4);
        ConcurrentHashMap<Long, PendingSignWrite> chunkBucket =
                levelBuckets.computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<>());
        chunkBucket.putIfAbsent(write.pos().asLong(), write.withRetry());
    }

    public static boolean hasMemoryPending(ServerLevel level, int chunkX, int chunkZ) {
        if (level == null) return false;
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, PendingSignWrite>> levelBuckets = MEMORY_PENDING.get(level);
        if (levelBuckets == null) return false;
        ConcurrentHashMap<Long, PendingSignWrite> chunkBucket = levelBuckets.get(ChunkPos.asLong(chunkX, chunkZ));
        return chunkBucket != null && !chunkBucket.isEmpty();
    }

    public static void flushPending(ServerLevel level) {
        if (level == null) return;
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, PendingSignWrite>> levelBuckets = MEMORY_PENDING.get(level);
        if (levelBuckets == null || levelBuckets.isEmpty()) return;
        ArrayList<PendingSignWrite> batch = new ArrayList<>();
        for (ConcurrentHashMap<Long, PendingSignWrite> chunkBucket : levelBuckets.values()) {
            if (chunkBucket == null || chunkBucket.isEmpty()) continue;
            batch.addAll(chunkBucket.values());
        }
        upsertBatch(level, batch);
    }

    public static void clearLevel(ServerLevel level) {
        if (level == null) return;
        MEMORY_PENDING.remove(level);
    }

    public static void clearAllMemory() {
        MEMORY_PENDING.clear();
    }

    private static void putMemory(ServerLevel level, PendingSignWrite write) {
        ConcurrentHashMap<Long, ConcurrentHashMap<Long, PendingSignWrite>> levelBuckets =
                MEMORY_PENDING.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        long chunkKey = ChunkPos.asLong(write.pos().getX() >> 4, write.pos().getZ() >> 4);
        ConcurrentHashMap<Long, PendingSignWrite> chunkBucket =
                levelBuckets.computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<>());
        chunkBucket.put(write.pos().asLong(), write);
    }
}
