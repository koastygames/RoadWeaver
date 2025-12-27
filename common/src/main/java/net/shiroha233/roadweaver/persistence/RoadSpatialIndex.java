package net.shiroha233.roadweaver.persistence;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 道路空间索引，使用网格划分实现高效的空间查询。
 * <p>
 * 相比原来的 RoadPositionQuery 逐点遍历，网格索引可以将查询复杂度从 O(n) 降低到 O(1)~O(k)，
 * 其中 k 是单个网格内的道路点数量（通常很小）。
 * </p>
 * <p>
 * 网格大小设为 8，即每个网格覆盖 8x8 的水平区域。查询时只需检查目标位置所在的网格
 * 及其相邻网格（最多 9 个），大幅减少比较次数。
 * </p>
 */
public final class RoadSpatialIndex {
    private RoadSpatialIndex() {
    }

    // 网格大小（格子边长），8 是一个平衡点：足够小以减少每格点数，足够大以减少网格数量
    private static final int GRID_SIZE = 8;
    private static final int GRID_SHIFT = 3; // log2(8) = 3

    // 每个维度最多缓存的区块数量（LRU 淘汰）
    private static final int MAX_CACHED_CHUNKS_PER_DIM = 512;

    // 维度 -> 区块坐标 -> 该区块的网格索引（网格坐标 -> 道路点集合）
    // 使用 LRU 缓存，避免无限增长
    private static final Map<String, Map<Long, ChunkGridIndex>> CHUNK_INDEX = new ConcurrentHashMap<>();

    /**
     * 创建带 LRU 淘汰的区块缓存
     */
    private static Map<Long, ChunkGridIndex> createLRUCache() {
        return java.util.Collections.synchronizedMap(
                new java.util.LinkedHashMap<Long, ChunkGridIndex>(64, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Long, ChunkGridIndex> eldest) {
                        return size() > MAX_CACHED_CHUNKS_PER_DIM;
                    }
                });
    }

    // Y 轴检查范围
    private static final int Y_CHECK_ABOVE = 12;
    private static final int Y_CHECK_BELOW = 2;

    /**
     * 判断指定位置是否在道路附近（用于阻止树木生成）
     *
     * @param level 世界
     * @param pos   要检查的位置
     * @return 如果在道路附近返回 true
     */
    public static boolean isNearRoad(WorldGenLevel level, BlockPos pos) {
        ServerLevel serverLevel = extractServerLevel(level);
        return serverLevel != null && isNearRoad(serverLevel, pos);
    }

    /**
     * 判断指定位置是否在道路附近
     */
    public static boolean isNearRoad(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null)
            return false;
        if (!ConfigService.get().preventTreesOnRoad())
            return false;

        // 关键改进：树木阻拦范围应依据“实际道路宽度”，而不是单一全局 roadWidth。
        // 这里传入的是“最大可能边距”，仅用于决定需要检查的网格半径；
        // 精确判定会在 isNearRoadInternal 内按每个道路点的 margin 进行。
        int maxMargin = computeMaxPossibleMargin();
        return isNearRoadInternal(level, pos, maxMargin, Y_CHECK_ABOVE, Y_CHECK_BELOW);
    }

    private static int computeMaxPossibleMargin() {
        int wRoad = Math.max(0, ConfigService.get().roadWidth());
        int wHighway = Math.max(0, ConfigService.get().highwayRoadWidth());
        int maxW = Math.max(wRoad, wHighway);
        // margin = halfWidth + 1；至少给 1 格边距用于覆盖“中心点代表整条道路宽度”的情况。
        return Math.max(1, (maxW / 2) + 1);
    }

    /**
     * 内部查询方法，支持自定义边距和 Y 轴范围
     */
    private static boolean isNearRoadInternal(ServerLevel level, BlockPos pos, int margin, int yAbove, int yBelow) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        String dimKey = dimKey(level);

        // 获取或构建区块的网格索引（使用 LRU 缓存）
        Map<Long, ChunkGridIndex> dimIndex = CHUNK_INDEX.computeIfAbsent(dimKey, k -> createLRUCache());
        long chunkKey = chunkKey(cx, cz);

        // 双重检查锁定，确保高并发下同一个区块的索引只构建一次
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

        if (gridIndex.isEmpty())
            return false;

        // 计算需要检查的网格范围
        int px = pos.getX(), py = pos.getY(), pz = pos.getZ();
        int gridX = px >> GRID_SHIFT;
        int gridZ = pz >> GRID_SHIFT;

        // 计算需要检查的相邻网格数量（基于 margin）
        int gridRadius = (margin >> GRID_SHIFT) + 1;

        // 检查目标网格及其相邻网格
        for (int dx = -gridRadius; dx <= gridRadius; dx++) {
            for (int dz = -gridRadius; dz <= gridRadius; dz++) {
                long gridKey = gridKey(gridX + dx, gridZ + dz);
                Long2ByteMap points = gridIndex.getPoints(gridKey);
                if (points == null || points.isEmpty())
                    continue;

                // 检查该网格内的道路点
                // 注意：Long2ByteMap.EntrySet 遍历可能不是线程完全安全的，
                // 但由于 gridIndex 构建后是只读的，这里是安全的。
                for (Long2ByteMap.Entry e : points.long2ByteEntrySet()) {
                    long packed = e.getLongKey();
                    int pointMargin = e.getByteValue() & 0xFF;
                    BlockPos road = BlockPos.of(packed);
                    int rdx = Math.abs(px - road.getX());
                    int rdz = Math.abs(pz - road.getZ());

                    if (rdx <= pointMargin && rdz <= pointMargin) {
                        int yDiff = py - road.getY();
                        if (yDiff >= -yBelow && yDiff <= yAbove) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * 构建指定区块的网格索引
     */
    private static ChunkGridIndex buildChunkGridIndex(ServerLevel level, int cx, int cz) {
        int minX = cx << 4;
        int minZ = cz << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        // 查询范围扩大，确保覆盖边缘道路
        List<Records.RoadData> roads = RoadShardStorage.queryRect(level, minX - GRID_SIZE, minZ - GRID_SIZE,
                maxX + GRID_SIZE, maxZ + GRID_SIZE);
        if (roads.isEmpty()) {
            return ChunkGridIndex.EMPTY;
        }

        ChunkGridIndex index = new ChunkGridIndex();

        for (Records.RoadData rd : roads) {
            if (rd.roadSegmentList() == null)
                continue;

            // 依据实际道路宽度计算阻拦边距：半宽 + 1。
            int pointMargin = Math.max(1, (Math.max(1, rd.width()) / 2) + 1);

            for (Records.RoadSegmentPlacement seg : rd.roadSegmentList()) {
                // 添加中心点
                addToIndex(index, seg.middlePos(), pointMargin, minX, minZ, maxX, maxZ);

                // 添加所有位置点
                if (seg.positions() != null) {
                    for (BlockPos p : seg.positions()) {
                        addToIndex(index, p, pointMargin, minX, minZ, maxX, maxZ);
                    }
                }
            }
        }

        return index.isEmpty() ? ChunkGridIndex.EMPTY : index;
    }

    /**
     * 将道路点添加到网格索引
     */
    private static void addToIndex(ChunkGridIndex index, BlockPos p, int margin, int minX, int minZ, int maxX,
            int maxZ) {
        if (p == null)
            return;

        int x = p.getX(), z = p.getZ();
        // 扩展边界检查
        if (x >= minX - GRID_SIZE && x <= maxX + GRID_SIZE && z >= minZ - GRID_SIZE && z <= maxZ + GRID_SIZE) {
            int gridX = x >> GRID_SHIFT;
            int gridZ = z >> GRID_SHIFT;
            long gridKey = gridKey(gridX, gridZ);
            index.addPoint(gridKey, p.asLong(), margin);
        }
    }

    // ==================== 工具方法 ====================

    @SuppressWarnings("deprecation")
    private static ServerLevel extractServerLevel(WorldGenLevel level) {
        if (level instanceof ServerLevel sl)
            return sl;
        if (level instanceof WorldGenRegion region)
            return region.getLevel();
        // C2ME/并发环境下，getLevel() 通常能返回真正的 ServerLevel
        try {
            Object l = level.getLevel();
            if (l instanceof ServerLevel)
                return (ServerLevel) l;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    private static long gridKey(int gx, int gz) {
        return (((long) gx) << 32) | (gz & 0xFFFFFFFFL);
    }

    private static String dimKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

    // ==================== 缓存管理 ====================

    /**
     * 清除指定维度的缓存
     */
    public static void clearCache(ServerLevel level) {
        if (level != null) {
            CHUNK_INDEX.remove(dimKey(level));
        }
    }

    /**
     * 清除所有缓存
     */
    public static void clearAllCache() {
        CHUNK_INDEX.clear();
    }

    /**
     * 使指定区块的缓存失效（道路数据更新时调用）
     */
    public static void invalidateChunk(ServerLevel level, int cx, int cz) {
        if (level == null)
            return;
        Map<Long, ChunkGridIndex> dimIndex = CHUNK_INDEX.get(dimKey(level));
        if (dimIndex != null) {
            dimIndex.remove(chunkKey(cx, cz));
        }
    }

    // ==================== 内部类 ====================

    /**
     * 区块内的网格索引
     */
    private static final class ChunkGridIndex {
        static final ChunkGridIndex EMPTY = new ChunkGridIndex();

        // 网格坐标 -> 该网格内的道路点（packed BlockPos）
        // value: 每个道路点对应的阻拦边距（byte，足够覆盖 0-127）
        private final Map<Long, Long2ByteOpenHashMap> grids;

        ChunkGridIndex() {
            // 使用 ConcurrentHashMap 确保在高并发构建过程中的基本安全，
            // 虽然 build 过程目前由 synchronized(dimIndex) 保护，但 grids 内部操作保持严谨。
            this.grids = new ConcurrentHashMap<>();
        }

        void addPoint(long gridKey, long packedPos, int margin) {
            int m = Math.max(0, Math.min(127, margin));
            // computeIfAbsent 是线程安全的
            Long2ByteOpenHashMap map = grids.computeIfAbsent(gridKey, k -> {
                Long2ByteOpenHashMap m2 = new Long2ByteOpenHashMap();
                // 由于 Long2ByteOpenHashMap 本身不是并发安全的，
                // 但在 buildChunkGridIndex 期间只有一个线程会操作此 index 实例。
                return m2;
            });
            byte existing = map.get(packedPos);
            if ((existing & 0xFF) < m) {
                map.put(packedPos, (byte) m);
            }
        }

        Long2ByteMap getPoints(long gridKey) {
            return grids.get(gridKey);
        }

        boolean isEmpty() {
            return grids.isEmpty();
        }
    }

}
