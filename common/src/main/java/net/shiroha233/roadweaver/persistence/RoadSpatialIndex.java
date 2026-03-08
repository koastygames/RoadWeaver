package net.shiroha233.roadweaver.persistence;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.WorldGenLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 道路空间索引，使用 8x8 网格划分实现 O(1)~O(k) 空间查询
 */
public final class RoadSpatialIndex {
    private RoadSpatialIndex() {}

    private static final int GRID_SIZE = RoadConstants.SPATIAL_INDEX_GRID_SIZE;
    private static final int GRID_SHIFT = RoadConstants.SPATIAL_INDEX_GRID_SHIFT;
    private static final int MAX_CACHED_CHUNKS_PER_DIM = RoadConstants.SPATIAL_INDEX_MAX_CACHED_CHUNKS;
    private static final Map<String, Map<Long, ChunkGridIndex>> CHUNK_INDEX = new ConcurrentHashMap<>();

    private static Map<Long, ChunkGridIndex> createLRUCache() {
        return Collections.synchronizedMap(
            new LinkedHashMap<Long, ChunkGridIndex>(
                RoadConstants.SPATIAL_INDEX_LRU_INITIAL_CAPACITY,
                RoadConstants.SPATIAL_INDEX_LRU_LOAD_FACTOR,
                true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, ChunkGridIndex> eldest) {
                    return size() > MAX_CACHED_CHUNKS_PER_DIM;
                }
            });
    }

    public static boolean isNearRoad(LevelSimulatedReader level, BlockPos pos) {
        ServerLevel serverLevel = extractServerLevel(level);
        return serverLevel != null && isNearRoad(serverLevel, pos);
    }

    public static boolean isNearRoad(WorldGenLevel level, BlockPos pos) {
        ServerLevel serverLevel = extractServerLevelFromWorldGen(level);
        return serverLevel != null && isNearRoad(serverLevel, pos);
    }

    public static boolean isNearRoad(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (!ConfigService.get().roadAppearance().preventTreesOnRoad()) return false;
        return isNearRoadXZ(level, pos, computeMaxPossibleMargin());
    }

    private static int computeMaxPossibleMargin() {
        int wRoad = Math.max(0, ConfigService.get().roadAppearance().roadWidth());
        int wHighway = Math.max(0, ConfigService.get().highway().roadWidth());
        return Math.max(1, (Math.max(wRoad, wHighway) / 2) + 1);
    }

    private static boolean isNearRoadXZ(ServerLevel level, BlockPos pos, int margin) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        String dimKey = dimKey(level);

        Map<Long, ChunkGridIndex> dimIndex = CHUNK_INDEX.computeIfAbsent(dimKey, k -> createLRUCache());
        long chunkKey = chunkKey(cx, cz);

        ChunkGridIndex gridIndex = dimIndex.get(chunkKey);
        if (gridIndex == null) {
            synchronized (dimIndex) {
                gridIndex = dimIndex.get(chunkKey);
                if (gridIndex == null) {
                    gridIndex = buildChunkGridIndex(level, cx, cz);
                    dimIndex.put(chunkKey, gridIndex);
                }
            }
        }

        if (gridIndex.isEmpty()) return false;

        int px = pos.getX(), pz = pos.getZ();
        int gridX = px >> GRID_SHIFT;
        int gridZ = pz >> GRID_SHIFT;
        int gridRadius = (margin >> GRID_SHIFT) + RoadConstants.SPATIAL_INDEX_SEARCH_MARGIN;

        for (int dx = -gridRadius; dx <= gridRadius; dx++) {
            for (int dz = -gridRadius; dz <= gridRadius; dz++) {
                long gridKey = gridKey(gridX + dx, gridZ + dz);
                Long2ByteMap points = gridIndex.getPoints(gridKey);
                if (points == null || points.isEmpty()) continue;

                for (Long2ByteMap.Entry e : points.long2ByteEntrySet()) {
                    long packed = e.getLongKey();
                    int pointMargin = e.getByteValue() & 0xFF;
                    BlockPos road = BlockPos.of(packed);
                    int rdx = Math.abs(px - road.getX());
                    int rdz = Math.abs(pz - road.getZ());
                    if (rdx <= pointMargin && rdz <= pointMargin) return true;
                }
            }
        }
        return false;
    }

    private static ChunkGridIndex buildChunkGridIndex(ServerLevel level, int cx, int cz) {
        int minX = cx << 4;
        int minZ = cz << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        List<RoadData> roads = RoadShardStorage.queryRect(level,
            minX - GRID_SIZE, minZ - GRID_SIZE, maxX + GRID_SIZE, maxZ + GRID_SIZE);
        if (roads.isEmpty()) return ChunkGridIndex.EMPTY;

        ChunkGridIndex index = new ChunkGridIndex();
        for (RoadData rd : roads) {
            if (rd.roadSegmentList() == null) continue;
            int pointMargin = Math.max(1, (Math.max(1, rd.width()) / 2) + 1);

            for (RoadSegmentPlacement seg : rd.roadSegmentList()) {
                addToIndex(index, seg.middlePos(), pointMargin, minX, minZ, maxX, maxZ);
                if (seg.positions() != null) {
                    for (BlockPos p : seg.positions()) {
                        addToIndex(index, p, pointMargin, minX, minZ, maxX, maxZ);
                    }
                }
            }
        }
        return index.isEmpty() ? ChunkGridIndex.EMPTY : index;
    }

    private static void addToIndex(ChunkGridIndex index, BlockPos p, int margin,
                                   int minX, int minZ, int maxX, int maxZ) {
        if (p == null) return;
        int x = p.getX(), z = p.getZ();
        if (x >= minX - GRID_SIZE && x <= maxX + GRID_SIZE &&
            z >= minZ - GRID_SIZE && z <= maxZ + GRID_SIZE) {
            index.addPoint(gridKey(x >> GRID_SHIFT, z >> GRID_SHIFT), p.asLong(), margin);
        }
    }

    @SuppressWarnings("deprecation")
    private static ServerLevel extractServerLevel(LevelSimulatedReader level) {
        if (level == null) return null;
        if (level instanceof ServerLevel sl) return sl;
        if (level instanceof WorldGenRegion region) return region.getLevel();
        if (level instanceof WorldGenLevel wgl) return extractServerLevelFromWorldGen(wgl);
        return null;
    }

    @SuppressWarnings("deprecation")
    private static ServerLevel extractServerLevelFromWorldGen(WorldGenLevel level) {
        if (level instanceof ServerLevel sl) return sl;
        if (level instanceof WorldGenRegion region) return region.getLevel();
        try {
            Object l = level.getLevel();
            if (l instanceof ServerLevel sl) return sl;
        } catch (Throwable ignored) {}
        return null;
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    private static long gridKey(int gx, int gz) {
        return (((long) gx) << 32) | (gz & 0xFFFFFFFFL);
    }

    private static String dimKey(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    public static void clearCache(ServerLevel level) {
        if (level != null) CHUNK_INDEX.remove(dimKey(level));
    }

    public static void clearAllCache() {
        CHUNK_INDEX.clear();
    }

    public static void invalidateChunk(ServerLevel level, int cx, int cz) {
        if (level == null) return;
        Map<Long, ChunkGridIndex> dimIndex = CHUNK_INDEX.get(dimKey(level));
        if (dimIndex != null) dimIndex.remove(chunkKey(cx, cz));
    }

    private static final class ChunkGridIndex {
        static final ChunkGridIndex EMPTY = new ChunkGridIndex();
        private final Map<Long, Long2ByteOpenHashMap> grids = new ConcurrentHashMap<>();

        void addPoint(long gridKey, long packedPos, int margin) {
            int m = Math.max(0, Math.min(RoadConstants.SPATIAL_INDEX_MAX_MARGIN, margin));
            Long2ByteOpenHashMap map = grids.computeIfAbsent(gridKey, k -> new Long2ByteOpenHashMap());
            byte existing = map.get(packedPos);
            if ((existing & 0xFF) < m) map.put(packedPos, (byte) m);
        }

        Long2ByteMap getPoints(long gridKey) {
            return grids.get(gridKey);
        }

        boolean isEmpty() {
            return grids.isEmpty();
        }
    }
}
