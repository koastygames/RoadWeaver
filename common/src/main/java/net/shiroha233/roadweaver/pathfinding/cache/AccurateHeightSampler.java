/* 文件职责：提供同步精确高度查询，并统一管理区块批量采样、LRU 与并发去重。 */
package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 精确高度采样同步门面。
 */
public final class AccurateHeightSampler {
    private static final int CHUNK_CACHE_CAPACITY = 256;
    private static final int COLUMN_CACHE_CAPACITY = 8_192;
    private static final Map<ServerLevel, AccurateHeightSampler> SHARED_SAMPLERS = new IdentityHashMap<>();

    private final ServerLevel level;
    private final int seaLevel;
    private final AccurateHeightBackend backend;
    private final Object cacheLock = new Object();
    private final Map<Long, AccurateHeightChunk> chunkCache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, AccurateHeightChunk> eldest) {
            return size() > CHUNK_CACHE_CAPACITY;
        }
    };
    private final Map<Long, AccurateHeightSample> columnCache = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, AccurateHeightSample> eldest) {
            return size() > COLUMN_CACHE_CAPACITY;
        }
    };
    private final ConcurrentHashMap<Long, CompletableFuture<AccurateHeightChunk>> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CompletableFuture<AccurateHeightSample>> columnInFlight = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    AccurateHeightSampler(ServerLevel level, AccurateHeightBackend backend) {
        this.level = level;
        this.seaLevel = level == null ? 63 : level.getSeaLevel();
        this.backend = backend;
    }

    public static AccurateHeightSampler create(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        synchronized (SHARED_SAMPLERS) {
            AccurateHeightSampler existing = SHARED_SAMPLERS.get(level);
            if (existing != null && !existing.closed.get()) {
                return existing;
            }
            CpuAccurateHeightBackend cpu = CpuAccurateHeightBackend.create(level);
            AccurateHeightSampler created = new AccurateHeightSampler(level, AccurateHeightBackends.create(level, cpu));
            SHARED_SAMPLERS.put(level, created);
            return created;
        }
    }

    public int motionBlockingNoLeaves(int x, int z) {
        return chunkAt(x, z).motionBlockingNoLeaves(x, z);
    }

    public int worldSurfaceWg(int x, int z) {
        return chunkAt(x, z).worldSurfaceWg(x, z);
    }

    public int oceanFloorWg(int x, int z) {
        return chunkAt(x, z).oceanFloorWg(x, z);
    }

    public int surfaceHeight(int x, int z) {
        AccurateHeightChunk chunk = chunkAt(x, z);
        int motion = chunk.motionBlockingNoLeaves(x, z);
        if (motion > seaLevel + 2) {
            return motion;
        }
        return chunk.worldSurfaceWg(x, z);
    }

    public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            return Map.of();
        }
        if (closed.get()) {
            throw new IllegalStateException("Accurate height sampler is closed");
        }

        LinkedHashSet<Long> uniqueKeys = new LinkedHashSet<>(chunkKeys);
        LinkedHashMap<Long, AccurateHeightChunk> result = new LinkedHashMap<>(uniqueKeys.size());
        LinkedHashMap<Long, CompletableFuture<AccurateHeightChunk>> owned = new LinkedHashMap<>();
        LinkedHashMap<Long, CompletableFuture<AccurateHeightChunk>> waiting = new LinkedHashMap<>();

        for (long key : uniqueKeys) {
            AccurateHeightChunk cached = getCached(key);
            if (cached != null) {
                AccurateSamplingStats.recordCacheHit();
                result.put(key, cached);
                continue;
            }

            AccurateSamplingStats.recordCacheMiss();
            CompletableFuture<AccurateHeightChunk> future = new CompletableFuture<>();
            CompletableFuture<AccurateHeightChunk> existing = inFlight.putIfAbsent(key, future);
            if (existing == null) {
                owned.put(key, future);
            } else {
                waiting.put(key, existing);
            }
        }

        if (!owned.isEmpty()) {
            completeOwned(owned, result);
        }
        joinWaiting(waiting, result);

        LinkedHashMap<Long, AccurateHeightChunk> ordered = new LinkedHashMap<>(uniqueKeys.size());
        for (long key : uniqueKeys) {
            AccurateHeightChunk chunk = result.get(key);
            if (chunk != null) {
                ordered.put(key, chunk);
            }
        }
        return Collections.unmodifiableMap(ordered);
    }

    public List<BlockPos> samplePathHeights(List<BlockPos> path, int divisor) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        List<BlockPos> positions = interpolatePositions(path, Math.max(1, divisor));
        Map<Long, AccurateHeightSample> samples = samplePositions(positions);
        List<BlockPos> result = new ArrayList<>(positions.size());
        for (BlockPos position : positions) {
            AccurateHeightSample sample = samples.get(AccurateHeightSample.key(position.getX(), position.getZ()));
            int height = sample != null ? surfaceHeight(sample) : surfaceHeight(position.getX(), position.getZ());
            result.add(new BlockPos(position.getX(), height, position.getZ()));
        }
        return result;
    }

    /**
     * 仅采样请求的世界列，适用于量化走廊与路径后处理。
     */
    public Map<Long, AccurateHeightSample> samplePositions(Collection<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return Map.of();
        }
        if (closed.get()) {
            throw new IllegalStateException("Accurate height sampler is closed");
        }

        LinkedHashMap<Long, BlockPos> unique = uniquePositions(positions);
        LinkedHashMap<Long, AccurateHeightSample> result = new LinkedHashMap<>(unique.size());
        LinkedHashMap<Long, CompletableFuture<AccurateHeightSample>> owned = new LinkedHashMap<>();
        LinkedHashMap<Long, CompletableFuture<AccurateHeightSample>> waiting = new LinkedHashMap<>();

        for (Map.Entry<Long, BlockPos> entry : unique.entrySet()) {
            long key = entry.getKey();
            AccurateHeightSample cached = cachedSample(entry.getValue());
            if (cached != null) {
                AccurateSamplingStats.recordColumnCacheHit();
                result.put(key, cached);
                continue;
            }

            AccurateSamplingStats.recordColumnCacheMiss();
            CompletableFuture<AccurateHeightSample> future = new CompletableFuture<>();
            CompletableFuture<AccurateHeightSample> existing = columnInFlight.putIfAbsent(key, future);
            if (existing == null) {
                owned.put(key, future);
            } else {
                waiting.put(key, existing);
            }
        }

        if (!owned.isEmpty()) {
            completeOwnedColumns(owned, unique, result);
        }
        joinWaitingColumns(waiting, result);
        return Collections.unmodifiableMap(result);
    }

    /**
     * 一次性区域批量不进入列 LRU，也不为每列创建并发去重 future；结果生命周期由区域持有者负责。
     */
    public Map<Long, AccurateHeightSample> sampleTransientPositions(Collection<BlockPos> positions) {
        return sampleTransientPositions(positions, AccurateSamplingProgress.NONE);
    }

    public Map<Long, AccurateHeightSample> sampleTransientPositions(Collection<BlockPos> positions,
                                                                     AccurateSamplingProgress progress) {
        if (positions == null || positions.isEmpty()) {
            return Map.of();
        }
        if (closed.get()) {
            throw new IllegalStateException("Accurate height sampler is closed");
        }
        Map<Long, AccurateHeightSample> sampled = backend.samplePositions(positions, progress);
        AccurateSamplingStats.recordTransientColumns(sampled.size());
        return Collections.unmodifiableMap(new LinkedHashMap<>(sampled));
    }

    public AccurateHeightGrid sampleTransientGrid(AccurateHeightGridRequest request,
                                                   AccurateSamplingProgress progress) {
        if (closed.get()) {
            throw new IllegalStateException("Accurate height sampler is closed");
        }
        AccurateHeightGrid sampled = backend.sampleGrid(request, progress);
        AccurateSamplingStats.recordTransientColumns(request.sampleCount());
        return sampled;
    }

    public void prefetchPositions(Collection<BlockPos> positions) {
        samplePositions(positions);
    }

    public String backendName() {
        return backend.backendName();
    }

    public String deviceName() {
        return backend.deviceName();
    }

    public void clear() {
        synchronized (SHARED_SAMPLERS) {
            if (level != null && SHARED_SAMPLERS.get(level) == this) {
                SHARED_SAMPLERS.remove(level);
            }
        }
        closeInternal();
    }

    private void closeInternal() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (cacheLock) {
            chunkCache.clear();
            columnCache.clear();
        }
        IllegalStateException failure = new IllegalStateException("Accurate height sampler is closed");
        inFlight.values().forEach(future -> future.completeExceptionally(failure));
        inFlight.clear();
        columnInFlight.values().forEach(future -> future.completeExceptionally(failure));
        columnInFlight.clear();
        backend.close();
    }

    public static void clearForLevel(ServerLevel level) {
        if (level == null) {
            return;
        }
        AccurateHeightSampler sampler;
        synchronized (SHARED_SAMPLERS) {
            sampler = SHARED_SAMPLERS.remove(level);
        }
        if (sampler != null) {
            sampler.closeInternal();
        }
    }

    public static void clearAll() {
        List<AccurateHeightSampler> samplers;
        synchronized (SHARED_SAMPLERS) {
            samplers = new ArrayList<>(SHARED_SAMPLERS.values());
            SHARED_SAMPLERS.clear();
        }
        for (AccurateHeightSampler sampler : samplers) {
            sampler.closeInternal();
        }
    }

    private AccurateHeightChunk chunkAt(int x, int z) {
        long key = ChunkPos.asLong(x >> 4, z >> 4);
        AccurateHeightChunk cached = getCached(key);
        if (cached != null) {
            AccurateSamplingStats.recordCacheHit();
            return cached;
        }
        AccurateHeightChunk sampled = sampleChunks(List.of(key)).get(key);
        if (sampled == null) {
            throw new IllegalStateException("Accurate height backend returned no chunk for " + (x >> 4) + "," + (z >> 4));
        }
        return sampled;
    }

    private void completeOwned(Map<Long, CompletableFuture<AccurateHeightChunk>> owned,
                               Map<Long, AccurateHeightChunk> result) {
        try {
            Map<Long, AccurateHeightChunk> sampled = backend.sampleChunks(owned.keySet());
            for (Map.Entry<Long, CompletableFuture<AccurateHeightChunk>> entry : owned.entrySet()) {
                long key = entry.getKey();
                AccurateHeightChunk chunk = sampled.get(key);
                if (chunk == null) {
                    throw new IllegalStateException("Missing accurate height chunk " + ChunkPos.getX(key) + "," + ChunkPos.getZ(key));
                }
                putCached(key, chunk);
                result.put(key, chunk);
                entry.getValue().complete(chunk);
            }
        } catch (Throwable failure) {
            for (CompletableFuture<AccurateHeightChunk> future : owned.values()) {
                future.completeExceptionally(failure);
            }
            throw failure;
        } finally {
            for (Map.Entry<Long, CompletableFuture<AccurateHeightChunk>> entry : owned.entrySet()) {
                inFlight.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void joinWaiting(Map<Long, CompletableFuture<AccurateHeightChunk>> waiting,
                                    Map<Long, AccurateHeightChunk> result) {
        for (Map.Entry<Long, CompletableFuture<AccurateHeightChunk>> entry : waiting.entrySet()) {
            result.put(entry.getKey(), entry.getValue().join());
        }
    }

    private void completeOwnedColumns(Map<Long, CompletableFuture<AccurateHeightSample>> owned,
                                      Map<Long, BlockPos> positions,
                                      Map<Long, AccurateHeightSample> result) {
        try {
            List<BlockPos> requested = new ArrayList<>(owned.size());
            for (long key : owned.keySet()) {
                requested.add(positions.get(key));
            }
            Map<Long, AccurateHeightSample> sampled = backend.samplePositions(requested);
            for (Map.Entry<Long, CompletableFuture<AccurateHeightSample>> entry : owned.entrySet()) {
                long key = entry.getKey();
                AccurateHeightSample sample = sampled.get(key);
                if (sample == null) {
                    BlockPos position = positions.get(key);
                    throw new IllegalStateException("Missing accurate height column " + position.getX() + "," + position.getZ());
                }
                putColumnCached(key, sample);
                result.put(key, sample);
                entry.getValue().complete(sample);
            }
        } catch (Throwable failure) {
            for (CompletableFuture<AccurateHeightSample> future : owned.values()) {
                future.completeExceptionally(failure);
            }
            throw failure;
        } finally {
            for (Map.Entry<Long, CompletableFuture<AccurateHeightSample>> entry : owned.entrySet()) {
                columnInFlight.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void joinWaitingColumns(Map<Long, CompletableFuture<AccurateHeightSample>> waiting,
                                           Map<Long, AccurateHeightSample> result) {
        for (Map.Entry<Long, CompletableFuture<AccurateHeightSample>> entry : waiting.entrySet()) {
            result.put(entry.getKey(), entry.getValue().join());
        }
    }

    private AccurateHeightChunk getCached(long key) {
        synchronized (cacheLock) {
            return chunkCache.get(key);
        }
    }

    private void putCached(long key, AccurateHeightChunk chunk) {
        synchronized (cacheLock) {
            chunkCache.put(key, chunk);
        }
    }

    private AccurateHeightSample cachedSample(BlockPos position) {
        AccurateHeightChunk chunk = getCached(ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
        if (chunk != null) {
            return new AccurateHeightSample(
                    chunk.worldSurfaceWg(position.getX(), position.getZ()),
                    chunk.oceanFloorWg(position.getX(), position.getZ()),
                    chunk.motionBlockingNoLeaves(position.getX(), position.getZ()));
        }
        synchronized (cacheLock) {
            return columnCache.get(AccurateHeightSample.key(position.getX(), position.getZ()));
        }
    }

    private void putColumnCached(long key, AccurateHeightSample sample) {
        synchronized (cacheLock) {
            columnCache.put(key, sample);
        }
    }

    private int surfaceHeight(AccurateHeightChunk chunk, int x, int z) {
        int motion = chunk.motionBlockingNoLeaves(x, z);
        return motion > seaLevel + 2 ? motion : chunk.worldSurfaceWg(x, z);
    }

    public int surfaceHeight(AccurateHeightSample sample) {
        return sample.motionBlockingNoLeaves() > seaLevel + 2
                ? sample.motionBlockingNoLeaves()
                : sample.worldSurfaceWg();
    }

    private static LinkedHashMap<Long, BlockPos> uniquePositions(Collection<BlockPos> positions) {
        LinkedHashMap<Long, BlockPos> unique = new LinkedHashMap<>();
        for (BlockPos position : positions) {
            if (position != null) {
                unique.putIfAbsent(AccurateHeightSample.key(position.getX(), position.getZ()), position);
            }
        }
        return unique;
    }

    private static Collection<Long> chunkKeysFor(Collection<BlockPos> positions) {
        LinkedHashSet<Long> keys = new LinkedHashSet<>();
        if (positions != null) {
            for (BlockPos position : positions) {
                if (position != null) {
                    keys.add(ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
                }
            }
        }
        return keys;
    }

    private static List<BlockPos> interpolatePositions(List<BlockPos> path, int divisor) {
        if (divisor <= 1 || path.size() == 1) {
            return new ArrayList<>(path);
        }
        List<BlockPos> result = new ArrayList<>((path.size() - 1) * divisor + 1);
        for (int i = 0; i < path.size() - 1; i++) {
            BlockPos start = path.get(i);
            BlockPos end = path.get(i + 1);
            for (int part = 0; part < divisor; part++) {
                double t = (double) part / divisor;
                int x = (int) Math.round(start.getX() + (end.getX() - start.getX()) * t);
                int z = (int) Math.round(start.getZ() + (end.getZ() - start.getZ()) * t);
                result.add(new BlockPos(x, 0, z));
            }
        }
        BlockPos last = path.get(path.size() - 1);
        result.add(new BlockPos(last.getX(), 0, last.getZ()));
        return result;
    }
}
