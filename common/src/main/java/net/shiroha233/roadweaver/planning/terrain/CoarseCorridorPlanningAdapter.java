/* 文件职责：以粗路径为骨架构建局部自适应精采走廊并计算道路原始路径。 */
package net.shiroha233.roadweaver.planning.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.config.sub.TerrainSamplingMode;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationProgressTracker;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationCoarseSamplingProgress;
import net.shiroha233.roadweaver.generation.progress.InitialGenerationStage;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSample;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainPngWriter;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainRegion;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainRegionSampler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

final class CoarseCorridorPlanningAdapter implements RoadTerrainPlanningPort {
    private static final int FLAT_RELIEF_MAX = 8;
    private static final int ROLLING_RELIEF_MAX = 24;
    private static final int MAX_RETRY_RADIUS_CHUNKS = 8;
    private static final int MAX_FAILURE_CONTACTS = 8;

    @Override
    public Result plan(Request request) {
        if (request.connections().isEmpty()) {
            return Result.empty(TerrainSamplingMode.COARSE_CORRIDOR);
        }
        TerrainSamplingCache cache = new TerrainSamplingCache();
        CoarseTerrainRegion coarse = null;
        try {
            Bounds bounds = request.bounds();
            int step = request.pathfinding().effectiveAStarStep();
            coarse = CoarseTerrainRegionSampler.sample(
                    request.level(), bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(), step,
                    InitialGenerationCoarseSamplingProgress.INSTANCE);
            try {
                CoarseTerrainPngWriter.writeTerrainTiles(request.level(), coarse);
            } catch (RuntimeException ignored) {
                // 地图写入失败不影响道路规划。
            }

            InitialGenerationProgressTracker.enterStage(
                    InitialGenerationStage.COARSE_PATHING, "computing_coarse_paths");
            InitialGenerationProgressTracker.setCoarsePathPlan(
                    request.connections().size(), "computing_coarse_paths");
            LinkedHashMap<StructureConnection, CorridorPlan> plans = new LinkedHashMap<>();
            for (StructureConnection connection : request.connections()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException("corridor planning interrupted");
                }
                try {
                    List<BlockPos> coarsePath = RawPathSearch.find(
                            request.level(), connection, request.pathfinding(), cache, coarse);
                    if (coarsePath != null && !coarsePath.isEmpty()) {
                        plans.put(connection, CorridorPlan.initial(coarsePath, coarse, step));
                    }
                } finally {
                    InitialGenerationProgressTracker.recordCoarsePathDone();
                }
            }
            if (plans.isEmpty()) {
                return Result.empty(TerrainSamplingMode.COARSE_CORRIDOR);
            }

            AccurateHeightSampler accurate = cache.getAccurateSampler(request.level());
            Map<Long, AccurateHeightSample> firstSamples = samplePlans(accurate, plans.values(), step);
            LinkedHashMap<StructureConnection, List<BlockPos>> paths = new LinkedHashMap<>();
            LinkedHashMap<StructureConnection, CorridorPlan> retryPlans = new LinkedHashMap<>();

            InitialGenerationProgressTracker.enterStage(
                    InitialGenerationStage.EXACT_PATHING, "computing_corridor_paths");
            InitialGenerationProgressTracker.setExactPathPlan(plans.size(), "computing_corridor_paths");
            for (Map.Entry<StructureConnection, CorridorPlan> entry : plans.entrySet()) {
                try {
                    AdaptiveCorridorTerrainField field = entry.getValue().field(
                            request.level(), cache, firstSamples);
                    List<BlockPos> exactPath = RawPathSearch.find(
                            request.level(), entry.getKey(), request.pathfinding(), cache, field);
                    Set<Long> contacts = retryContacts(
                            exactPath,
                            field.boundaryChunks(exactPath),
                            field.rejectedHotspots(MAX_FAILURE_CONTACTS));
                    CorridorPlan expanded = entry.getValue().expandAt(contacts);
                    if (expanded != entry.getValue()) {
                        retryPlans.put(entry.getKey(), expanded);
                    } else {
                        paths.put(entry.getKey(), exactPath == null || exactPath.isEmpty()
                                ? entry.getValue().coarsePath()
                                : exactPath);
                    }
                } finally {
                    InitialGenerationProgressTracker.recordExactPathDone();
                }
            }

            if (!retryPlans.isEmpty()) {
                Map<Long, AccurateHeightSample> retrySamples = samplePlans(accurate, retryPlans.values(), step);
                InitialGenerationProgressTracker.enterStage(
                        InitialGenerationStage.EXACT_PATHING, "retrying_corridor_paths");
                InitialGenerationProgressTracker.setExactPathPlan(
                        retryPlans.size(), "retrying_corridor_paths");
                for (Map.Entry<StructureConnection, CorridorPlan> entry : retryPlans.entrySet()) {
                    try {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new java.util.concurrent.CancellationException("corridor retry interrupted");
                        }
                        AdaptiveCorridorTerrainField field = entry.getValue().field(
                                request.level(), cache, retrySamples);
                        List<BlockPos> retried = RawPathSearch.find(
                                request.level(), entry.getKey(), request.pathfinding(), cache, field);
                        paths.put(entry.getKey(), retried == null || retried.isEmpty()
                                ? plans.get(entry.getKey()).coarsePath()
                                : retried);
                    } finally {
                        InitialGenerationProgressTracker.recordExactPathDone();
                    }
                }
            }
            return new Result(TerrainSamplingMode.COARSE_CORRIDOR, paths);
        } finally {
            if (coarse != null) {
                coarse.dispose();
            }
            cache.clear();
        }
    }

    private static Map<Long, AccurateHeightSample> samplePlans(AccurateHeightSampler sampler,
                                                                Collection<CorridorPlan> plans,
                                                                int step) {
        LinkedHashMap<Long, BlockPos> positions = new LinkedHashMap<>();
        for (CorridorPlan plan : plans) {
            for (BlockPos position : AdaptiveCorridorTerrainField.gridPositions(plan.chunks(), step)) {
                positions.putIfAbsent(AccurateHeightSample.key(position.getX(), position.getZ()), position);
            }
        }
        InitialGenerationProgressTracker.enterStage(
                InitialGenerationStage.EXACT_SAMPLING, "sampling_accurate_corridors");
        InitialGenerationProgressTracker.setExactSamplingPlan(positions.size(), "sampling_accurate_corridors");
        long startedAt = System.nanoTime();
        AtomicLong backendColumns = new AtomicLong();
        Map<Long, AccurateHeightSample> samples = sampler.samplePositions(positions.values(), batch -> {
            backendColumns.addAndGet(batch.batchColumns());
            InitialGenerationProgressTracker.recordExactSampleBatch(
                    batch.batchColumns(),
                    Math.max(1L, batch.batchNanos() / 1_000_000L),
                    sampler.backendName(), sampler.deviceName());
        });
        long resolvedWithoutBackend = Math.max(0L, samples.size() - backendColumns.get());
        if (resolvedWithoutBackend > 0L) {
            InitialGenerationProgressTracker.recordExactSampleBatch(
                    Math.toIntExact(Math.min(Integer.MAX_VALUE, resolvedWithoutBackend)),
                    Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L),
                    sampler.backendName(), sampler.deviceName());
        }
        InitialGenerationProgressTracker.completeExactSampling();
        return samples;
    }

    static Set<Long> retryContacts(List<BlockPos> exactPath,
                                   Set<Long> boundaryContacts,
                                   Set<Long> rejectedContacts) {
        if (exactPath != null && !exactPath.isEmpty()) {
            return boundaryContacts == null ? Set.of() : Set.copyOf(boundaryContacts);
        }
        if (rejectedContacts != null && !rejectedContacts.isEmpty()) {
            return Set.copyOf(rejectedContacts);
        }
        return Set.of();
    }

    static int radiusForRelief(int relief) {
        if (relief <= FLAT_RELIEF_MAX) return 1;
        if (relief <= ROLLING_RELIEF_MAX) return 2;
        return 4;
    }

    private static final class CorridorPlan {
        private final List<BlockPos> coarsePath;
        private final int[] radii;
        private final Set<Long> chunks;
        private final Map<Long, Integer> ownerByChunk;
        private final int step;

        private CorridorPlan(List<BlockPos> coarsePath, int[] radii, int step) {
            this.coarsePath = List.copyOf(coarsePath);
            this.radii = radii.clone();
            this.step = step;
            LinkedHashSet<Long> selected = new LinkedHashSet<>();
            HashMap<Long, Integer> owners = new HashMap<>();
            HashMap<Long, Integer> ownerDistance = new HashMap<>();
            for (int index = 0; index < coarsePath.size(); index++) {
                BlockPos point = coarsePath.get(index);
                int centerX = point.getX() >> 4;
                int centerZ = point.getZ() >> 4;
                int radius = radii[index];
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        long key = ChunkPos.asLong(centerX + dx, centerZ + dz);
                        int distance = Math.max(Math.abs(dx), Math.abs(dz));
                        selected.add(key);
                        Integer previous = ownerDistance.get(key);
                        if (previous == null || distance < previous) {
                            ownerDistance.put(key, distance);
                            owners.put(key, index);
                        }
                    }
                }
            }
            this.chunks = Set.copyOf(selected);
            this.ownerByChunk = Map.copyOf(owners);
        }

        static CorridorPlan initial(List<BlockPos> coarsePath, CoarseTerrainRegion terrain, int step) {
            int[] radii = new int[coarsePath.size()];
            for (int index = 0; index < coarsePath.size(); index++) {
                BlockPos point = coarsePath.get(index);
                radii[index] = radiusForRelief(localRelief(terrain, point, step));
            }
            return new CorridorPlan(coarsePath, radii, step);
        }

        List<BlockPos> coarsePath() {
            return coarsePath;
        }

        Set<Long> chunks() {
            return chunks;
        }

        AdaptiveCorridorTerrainField field(net.minecraft.server.level.ServerLevel level,
                                           TerrainSamplingCache cache,
                                           Map<Long, AccurateHeightSample> samples) {
            return new AdaptiveCorridorTerrainField(level, cache, step, chunks, samples);
        }

        CorridorPlan expandAt(Set<Long> contactedChunks) {
            if (contactedChunks == null || contactedChunks.isEmpty()) {
                return this;
            }
            LinkedHashSet<Integer> owners = new LinkedHashSet<>();
            for (long chunk : contactedChunks) {
                Integer owner = ownerByChunk.get(chunk);
                if (owner == null) {
                    owner = nearestOwner(chunk);
                }
                if (owner != null) {
                    for (int index = Math.max(0, owner - 1);
                         index <= Math.min(radii.length - 1, owner + 1); index++) {
                        owners.add(index);
                    }
                }
            }
            if (owners.isEmpty()) {
                return this;
            }
            int[] expanded = radii.clone();
            boolean changed = false;
            for (int index : owners) {
                int next = expanded[index] <= 1 ? 2 : expanded[index] <= 2 ? 4 : MAX_RETRY_RADIUS_CHUNKS;
                if (next > expanded[index]) {
                    expanded[index] = next;
                    changed = true;
                }
            }
            return changed ? new CorridorPlan(coarsePath, expanded, step) : this;
        }

        private Integer nearestOwner(long chunk) {
            int chunkX = ChunkPos.getX(chunk);
            int chunkZ = ChunkPos.getZ(chunk);
            int bestIndex = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int index = 0; index < coarsePath.size(); index++) {
                BlockPos point = coarsePath.get(index);
                int distance = Math.max(Math.abs(chunkX - (point.getX() >> 4)),
                        Math.abs(chunkZ - (point.getZ() >> 4)));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = index;
                }
            }
            return bestIndex < 0 ? null : bestIndex;
        }

        private static int localRelief(CoarseTerrainRegion terrain, BlockPos point, int step) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            int sampleStep = Math.max(1, step);
            for (int z = point.getZ() - 16; z <= point.getZ() + 16; z += sampleStep) {
                for (int x = point.getX() - 16; x <= point.getX() + 16; x += sampleStep) {
                    if (!terrain.contains(x, z)) continue;
                    int height = terrain.height(x, z);
                    min = Math.min(min, height);
                    max = Math.max(max, height);
                }
            }
            return min == Integer.MAX_VALUE ? 0 : max - min;
        }
    }
}
