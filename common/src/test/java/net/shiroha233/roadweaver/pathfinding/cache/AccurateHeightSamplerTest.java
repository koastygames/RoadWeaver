/* 文件职责：验证精确高度门面的批量缓存、LRU 淘汰与并发去重。 */
package net.shiroha233.roadweaver.pathfinding.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccurateHeightSamplerTest {
    @Test
    void sampleChunksReturnsImmutableResultsAndEvictsLeastRecentlyUsedChunk() {
        FakeBackend backend = new FakeBackend();
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        List<Long> keys = new ArrayList<>();
        for (int chunkX = 0; chunkX <= 256; chunkX++) {
            keys.add(ChunkPos.asLong(chunkX, 0));
        }

        Map<Long, AccurateHeightChunk> first = sampler.sampleChunks(keys);
        assertEquals(1, backend.invocations.get());
        assertEquals(257, first.size());
        assertThrows(UnsupportedOperationException.class,
                () -> first.put(ChunkPos.asLong(999, 0), chunk(999, 0)));

        sampler.sampleChunks(List.of(keys.getFirst()));
        assertEquals(2, backend.invocations.get(), "the first chunk should have been evicted from the 256-entry LRU");
    }

    @Test
    void concurrentRequestsForSameChunkShareOneBackendBatch() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FakeBackend backend = new FakeBackend(entered, release);
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        long key = ChunkPos.asLong(-7, 11);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Map<Long, AccurateHeightChunk>> first = executor.submit(() -> sampler.sampleChunks(List.of(key)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<Map<Long, AccurateHeightChunk>> second = executor.submit(() -> sampler.sampleChunks(List.of(key)));
            release.countDown();

            assertEquals(key, first.get(5, TimeUnit.SECONDS).get(key).chunkKey());
            assertEquals(key, second.get(5, TimeUnit.SECONDS).get(key).chunkKey());
            assertEquals(1, backend.invocations.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void samplePathHeightsAlwaysReplacesCoarsePathY() {
        FakeBackend backend = new FakeBackend(96);
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);

        List<BlockPos> sampled = sampler.samplePathHeights(List.of(
                new BlockPos(1, -40, 1),
                new BlockPos(17, -20, 1)), 0);

        assertEquals(List.of(
                new BlockPos(1, 96, 1),
                new BlockPos(17, 96, 1)), sampled);
        assertEquals(1, backend.invocations.get());
    }

    @Test
    void samplePositionsUsesSparseBackendAndCachesExactColumns() {
        SparseBackend backend = new SparseBackend(96);
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        List<BlockPos> positions = List.of(new BlockPos(1, 0, 1), new BlockPos(9, 0, 3));

        Map<Long, AccurateHeightSample> first = sampler.samplePositions(positions);
        Map<Long, AccurateHeightSample> second = sampler.samplePositions(positions);

        assertEquals(2, first.size());
        assertEquals(96, first.get(AccurateHeightSample.key(1, 1)).worldSurfaceWg());
        assertEquals(first, second);
        assertEquals(1, backend.sparseInvocations.get());
        assertEquals(0, backend.chunkInvocations.get());
    }

    @Test
    void transientPositionsBypassColumnCache() {
        SparseBackend backend = new SparseBackend(96);
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        List<BlockPos> positions = List.of(new BlockPos(1, 0, 1), new BlockPos(9, 0, 3));

        Map<Long, AccurateHeightSample> first = sampler.sampleTransientPositions(positions);
        Map<Long, AccurateHeightSample> second = sampler.sampleTransientPositions(positions);

        assertEquals(first, second);
        assertEquals(2, backend.sparseInvocations.get());
        assertThrows(UnsupportedOperationException.class,
                () -> first.put(AccurateHeightSample.key(3, 3), new AccurateHeightSample(1, 1, 1)));
    }

    @Test
    void transientGridUsesBackendGridPortAndReportsProgress() {
        GridBackend backend = new GridBackend();
        AccurateHeightSampler sampler = new AccurateHeightSampler(null, backend);
        AccurateHeightGridRequest request = new AccurateHeightGridRequest(-16, 24, 3, 2, 8);
        AtomicInteger completed = new AtomicInteger();

        AccurateHeightGrid grid = sampler.sampleTransientGrid(
                request, batch -> completed.set(Math.toIntExact(batch.completedColumns())));

        assertEquals(1, backend.gridInvocations.get());
        assertEquals(request.sampleCount(), completed.get());
        assertEquals(-16, grid.worldSurface()[0]);
        assertEquals(32, grid.oceanFloor()[request.sampleCount() - 1]);
        assertEquals(32, grid.motionBlocking()[request.sampleCount() - 1]);
    }

    private static AccurateHeightChunk chunk(int chunkX, int chunkZ) {
        return chunk(chunkX, chunkZ, chunkX + chunkZ);
    }

    private static AccurateHeightChunk chunk(int chunkX, int chunkZ, int height) {
        int[] values = new int[AccurateHeightChunk.COLUMN_COUNT];
        Arrays.fill(values, height);
        return new AccurateHeightChunk(chunkX, chunkZ, values.clone(), values.clone(), values.clone());
    }

    private static final class FakeBackend implements AccurateHeightBackend {
        private final AtomicInteger invocations = new AtomicInteger();
        private final CountDownLatch entered;
        private final CountDownLatch release;
        private final Integer fixedHeight;

        private FakeBackend() {
            this(null, null, null);
        }

        private FakeBackend(int fixedHeight) {
            this(null, null, fixedHeight);
        }

        private FakeBackend(CountDownLatch entered, CountDownLatch release) {
            this(entered, release, null);
        }

        private FakeBackend(CountDownLatch entered, CountDownLatch release, Integer fixedHeight) {
            this.entered = entered;
            this.release = release;
            this.fixedHeight = fixedHeight;
        }

        @Override
        public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
            invocations.incrementAndGet();
            if (entered != null) {
                entered.countDown();
            }
            if (release != null) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            LinkedHashMap<Long, AccurateHeightChunk> result = new LinkedHashMap<>();
            for (long key : chunkKeys) {
                int chunkX = ChunkPos.getX(key);
                int chunkZ = ChunkPos.getZ(key);
                result.put(key, fixedHeight == null
                        ? chunk(chunkX, chunkZ)
                        : chunk(chunkX, chunkZ, fixedHeight));
            }
            return result;
        }

        @Override
        public String backendName() {
            return "TEST";
        }
    }

    private static final class SparseBackend implements AccurateHeightBackend {
        private final int height;
        private final AtomicInteger chunkInvocations = new AtomicInteger();
        private final AtomicInteger sparseInvocations = new AtomicInteger();

        private SparseBackend(int height) {
            this.height = height;
        }

        @Override
        public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
            chunkInvocations.incrementAndGet();
            return Map.of();
        }

        @Override
        public Map<Long, AccurateHeightSample> samplePositions(Collection<BlockPos> positions) {
            sparseInvocations.incrementAndGet();
            LinkedHashMap<Long, AccurateHeightSample> samples = new LinkedHashMap<>();
            for (BlockPos position : positions) {
                samples.put(AccurateHeightSample.key(position.getX(), position.getZ()),
                        new AccurateHeightSample(height, height, height));
            }
            return samples;
        }

        @Override
        public String backendName() {
            return "SPARSE_TEST";
        }
    }

    private static final class GridBackend implements AccurateHeightBackend {
        private final AtomicInteger gridInvocations = new AtomicInteger();

        @Override
        public Map<Long, AccurateHeightChunk> sampleChunks(Collection<Long> chunkKeys) {
            throw new AssertionError("grid sampling must not expand to chunk sampling");
        }

        @Override
        public AccurateHeightGrid sampleGrid(AccurateHeightGridRequest request,
                                              AccurateSamplingProgress progress) {
            gridInvocations.incrementAndGet();
            int[] worldSurface = new int[request.sampleCount()];
            int[] oceanFloor = new int[request.sampleCount()];
            int[] motionBlocking = new int[request.sampleCount()];
            for (int index = 0; index < request.sampleCount(); index++) {
                worldSurface[index] = request.blockX(index);
                oceanFloor[index] = request.blockZ(index);
                motionBlocking[index] = request.blockX(index) + request.blockZ(index);
            }
            progress.onBatch(new AccurateSamplingProgress.Batch(
                    request.sampleCount(), request.sampleCount(), request.sampleCount(), 1L, 1L, 0L));
            return new AccurateHeightGrid(request, worldSurface, oceanFloor, motionBlocking);
        }

        @Override
        public String backendName() {
            return "GRID_TEST";
        }
    }
}
