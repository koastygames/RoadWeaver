package net.shiroha233.roadweaver.persistence;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 道路位置查询服务，用于阻止树木在道路上生成。
 */
public final class RoadPositionQuery {
    private RoadPositionQuery() {}

    // 缓存：维度 -> 区块 -> 该区块内的道路点集合（空集合表示无道路）
    // 使用轻量 LRU 限制单维度缓存规模，避免长时间运行内存上涨
    private static final Map<String, Map<Long, Set<Long>>> CHUNK_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CHUNKS_PER_DIM = 1024;
    private static final int EXTRA_MARGIN = 1;
    private static final int Y_CHECK_ABOVE = 12;
    private static final int Y_CHECK_BELOW = 2;

    public static boolean isOnRoad(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (!ConfigService.get().preventTreesOnRoad()) return false;

        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        long chunkKey = chunkKey(cx, cz);
        String dimKey = dimKey(level);
        Map<Long, Set<Long>> dimCache = CHUNK_CACHE.computeIfAbsent(dimKey, k -> createDimCache());

        // 获取或构建该区块的道路点缓存
        Set<Long> roadPoints = dimCache.computeIfAbsent(chunkKey, k -> buildChunkRoadPoints(level, cx, cz));
        if (roadPoints.isEmpty()) return false;

        // 快速检查：遍历道路点，判断树根是否在阻拦范围内
        int tx = pos.getX(), ty = pos.getY(), tz = pos.getZ();
        int margin = (ConfigService.get().roadWidth() / 2) + EXTRA_MARGIN;
        
        for (long packed : roadPoints) {
            BlockPos road = BlockPos.of(packed);
            if (Math.abs(tx - road.getX()) <= margin && Math.abs(tz - road.getZ()) <= margin) {
                int yDiff = ty - road.getY();
                if (yDiff >= -Y_CHECK_BELOW && yDiff <= Y_CHECK_ABOVE) return true;
            }
        }
        return false;
    }

    /** 构建指定区块的道路点集合（只在首次访问时执行一次） */
    private static Set<Long> buildChunkRoadPoints(ServerLevel level, int cx, int cz) {
        int minX = cx << 4, minZ = cz << 4;
        int maxX = minX + 15, maxZ = minZ + 15;
        // 查询范围稍微扩大，确保覆盖边缘道路
        List<Records.RoadData> roads = RoadShardStorage.queryRect(level, minX - 8, minZ - 8, maxX + 8, maxZ + 8);
        if (roads.isEmpty()) return Collections.emptySet();

        Set<Long> points = new HashSet<>();
        for (Records.RoadData rd : roads) {
            if (rd.roadSegmentList() == null) continue;
            for (Records.RoadSegmentPlacement seg : rd.roadSegmentList()) {
                addIfInChunk(points, seg.middlePos(), minX, minZ, maxX, maxZ);
                if (seg.positions() != null) {
                    for (BlockPos p : seg.positions()) {
                        addIfInChunk(points, p, minX, minZ, maxX, maxZ);
                    }
                }
            }
        }
        return points.isEmpty() ? Collections.emptySet() : points;
    }

    private static void addIfInChunk(Set<Long> set, BlockPos p, int minX, int minZ, int maxX, int maxZ) {
        int x = p.getX(), z = p.getZ();
        // 扩展边界检查，确保边缘道路也被缓存
        if (x >= minX - 8 && x <= maxX + 8 && z >= minZ - 8 && z <= maxZ + 8) {
            set.add(p.asLong());
        }
    }

    public static boolean isOnRoad(WorldGenLevel level, BlockPos pos) {
        ServerLevel sl = extractServerLevel(level);
        return sl != null && isOnRoad(sl, pos);
    }

    @SuppressWarnings("deprecation")
    private static ServerLevel extractServerLevel(WorldGenLevel level) {
        if (level instanceof ServerLevel sl) return sl;
        if (level instanceof WorldGenRegion region) return region.getLevel();
        return null;
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    private static String dimKey(ServerLevel level) {
        return level.dimension().location().toString();
    }

    public static void clearCache(ServerLevel level) {
        if (level != null) CHUNK_CACHE.remove(dimKey(level));
    }

    public static void clearAllCache() {
        CHUNK_CACHE.clear();
    }

    /** 为单个维度创建带上限的 LRU Map，防止缓存无限增长 */
    private static Map<Long, Set<Long>> createDimCache() {
        return java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<Long, Set<Long>> eldest) {
                return size() > MAX_CHUNKS_PER_DIM;
            }
        });
    }
}
