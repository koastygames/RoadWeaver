package net.shiroha233.roadweaver.persistence;

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
    private RoadSpatialIndex() {}

    // 网格大小（格子边长），8 是一个平衡点：足够小以减少每格点数，足够大以减少网格数量
    private static final int GRID_SIZE = 8;
    private static final int GRID_SHIFT = 3; // log2(8) = 3

    // 维度 -> 区块坐标 -> 该区块的网格索引（网格坐标 -> 道路点集合）
    private static final Map<String, Map<Long, ChunkGridIndex>> CHUNK_INDEX = new ConcurrentHashMap<>();

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
        if (level == null || pos == null) return false;
        if (!ConfigService.get().preventTreesOnRoad()) return false;

        int margin = (ConfigService.get().roadWidth() / 2) + 1;
        return isNearRoadInternal(level, pos, margin, Y_CHECK_ABOVE, Y_CHECK_BELOW);
    }

    /**
     * 内部查询方法，支持自定义边距和 Y 轴范围
     */
    private static boolean isNearRoadInternal(ServerLevel level, BlockPos pos, int margin, int yAbove, int yBelow) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        String dimKey = dimKey(level);

        // 获取或构建区块的网格索引
        Map<Long, ChunkGridIndex> dimIndex = CHUNK_INDEX.computeIfAbsent(dimKey, k -> new ConcurrentHashMap<>());
        long chunkKey = chunkKey(cx, cz);
        ChunkGridIndex gridIndex = dimIndex.computeIfAbsent(chunkKey, k -> buildChunkGridIndex(level, cx, cz));

        if (gridIndex.isEmpty()) return false;

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
                Set<Long> points = gridIndex.getPoints(gridKey);
                if (points == null || points.isEmpty()) continue;

                // 检查该网格内的道路点
                for (long packed : points) {
                    BlockPos road = BlockPos.of(packed);
                    int rdx = Math.abs(px - road.getX());
                    int rdz = Math.abs(pz - road.getZ());

                    if (rdx <= margin && rdz <= margin) {
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
        List<Records.RoadData> roads = RoadShardStorage.queryRect(level, minX - 8, minZ - 8, maxX + 8, maxZ + 8);
        if (roads.isEmpty()) {
            return ChunkGridIndex.EMPTY;
        }

        ChunkGridIndex index = new ChunkGridIndex();

        for (Records.RoadData rd : roads) {
            if (rd.roadSegmentList() == null) continue;

            for (Records.RoadSegmentPlacement seg : rd.roadSegmentList()) {
                // 添加中心点
                addToIndex(index, seg.middlePos(), minX, minZ, maxX, maxZ);

                // 添加所有位置点
                if (seg.positions() != null) {
                    for (BlockPos p : seg.positions()) {
                        addToIndex(index, p, minX, minZ, maxX, maxZ);
                    }
                }
            }
        }

        return index.isEmpty() ? ChunkGridIndex.EMPTY : index;
    }

    /**
     * 将道路点添加到网格索引
     */
    private static void addToIndex(ChunkGridIndex index, BlockPos p, int minX, int minZ, int maxX, int maxZ) {
        if (p == null) return;

        int x = p.getX(), z = p.getZ();
        // 扩展边界检查
        if (x >= minX - 8 && x <= maxX + 8 && z >= minZ - 8 && z <= maxZ + 8) {
            int gridX = x >> GRID_SHIFT;
            int gridZ = z >> GRID_SHIFT;
            long gridKey = gridKey(gridX, gridZ);
            index.addPoint(gridKey, p.asLong());
        }
    }

    // ==================== 工具方法 ====================

    @SuppressWarnings("deprecation")
    private static ServerLevel extractServerLevel(WorldGenLevel level) {
        if (level instanceof ServerLevel sl) return sl;
        if (level instanceof WorldGenRegion region) return region.getLevel();
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
        if (level == null) return;
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
        private final Map<Long, Set<Long>> grids;

        ChunkGridIndex() {
            this.grids = new HashMap<>();
        }

        void addPoint(long gridKey, long packedPos) {
            grids.computeIfAbsent(gridKey, k -> new HashSet<>()).add(packedPos);
        }

        Set<Long> getPoints(long gridKey) {
            return grids.get(gridKey);
        }

        boolean isEmpty() {
            return grids.isEmpty();
        }
    }
}
