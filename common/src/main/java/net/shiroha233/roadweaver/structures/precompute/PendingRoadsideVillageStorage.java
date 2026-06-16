package net.shiroha233.roadweaver.structures.precompute;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路边村庄待注入存储
 */
public final class PendingRoadsideVillageStorage {
    private PendingRoadsideVillageStorage() {}

    private static final int MAX_INJECTED_PER_DIM = 2048;
    private static final Map<ResourceLocation, Map<Long, List<PendingRoadsideVillage>>> PENDING = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Set<ResourceLocation>> PLACED = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Set<Long>> INJECTED_CHUNKS = new ConcurrentHashMap<>();

    private static Set<ResourceLocation> createPlacementSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    private static Set<Long> createLimitedChunkSet() {
        return Collections.newSetFromMap(
            Collections.synchronizedMap(
                new java.util.LinkedHashMap<Long, Boolean>(256, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(java.util.Map.Entry<Long, Boolean> eldest) {
                        return size() > MAX_INJECTED_PER_DIM;
                    }
                }
            )
        );
    }

    public static boolean addPendingVillage(ServerLevel level, PendingRoadsideVillage village) {
        ResourceLocation dimKey = level.dimension().location();
        Set<ResourceLocation> placed = PLACED.computeIfAbsent(dimKey, k -> createPlacementSet());
        if (!placed.add(village.placementId())) {
            return false;
        }

        long chunkKey = village.chunkKey();
        PENDING.computeIfAbsent(dimKey, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(chunkKey, k -> Collections.synchronizedList(new ArrayList<>()))
            .add(village);
        return true;
    }

    public static List<PendingRoadsideVillage> getPendingVillages(ServerLevel level, ChunkPos chunkPos) {
        ResourceLocation dimKey = level.dimension().location();
        long chunkKey = chunkPos.toLong();

        Set<Long> injected = INJECTED_CHUNKS.get(dimKey);
        if (injected != null && injected.contains(chunkKey)) {
            return Collections.emptyList();
        }

        Map<Long, List<PendingRoadsideVillage>> dimMap = PENDING.get(dimKey);
        if (dimMap == null) {
            return Collections.emptyList();
        }

        List<PendingRoadsideVillage> villages = dimMap.get(chunkKey);
        return villages == null ? Collections.emptyList() : new ArrayList<>(villages);
    }

    public static void markAsInjected(ServerLevel level, ChunkPos chunkPos) {
        ResourceLocation dimKey = level.dimension().location();
        long chunkKey = chunkPos.toLong();
        INJECTED_CHUNKS.computeIfAbsent(dimKey, k -> createLimitedChunkSet()).add(chunkKey);

        Map<Long, List<PendingRoadsideVillage>> dimMap = PENDING.get(dimKey);
        if (dimMap != null) {
            dimMap.remove(chunkKey);
        }
    }

    public static void clearDimension(ResourceLocation dimension) {
        PENDING.remove(dimension);
        PLACED.remove(dimension);
        INJECTED_CHUNKS.remove(dimension);
    }

    public static void clearAll() {
        PENDING.clear();
        PLACED.clear();
        INJECTED_CHUNKS.clear();
    }
}