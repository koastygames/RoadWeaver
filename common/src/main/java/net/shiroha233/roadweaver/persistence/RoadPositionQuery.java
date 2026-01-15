package net.shiroha233.roadweaver.persistence;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.WorldGenLevel;

/**
 * 道路位置查询服务，用于阻止树木在道路上生成。
 * <p>
 * 此类作为 {@link RoadSpatialIndex} 的简单门面（Facade），
 * 保持向后兼容的 API，同时底层使用高效的网格空间索引。
 * </p>
 */
public final class RoadPositionQuery {
    private RoadPositionQuery() {}

    /**
     * 判断指定位置是否在道路上或道路附近（LevelSimulatedReader 版本）
     * 这是 TreeFeature.validTreePos 使用的接口类型
     */
    public static boolean isOnRoad(LevelSimulatedReader level, BlockPos pos) {
        return RoadSpatialIndex.isNearRoad(level, pos);
    }

    /**
     * 判断指定位置是否在道路上或道路附近
     */
    public static boolean isOnRoad(ServerLevel level, BlockPos pos) {
        return RoadSpatialIndex.isNearRoad(level, pos);
    }

    /**
     * 判断指定位置是否在道路上或道路附近（WorldGenLevel 版本）
     */
    public static boolean isOnRoad(WorldGenLevel level, BlockPos pos) {
        return RoadSpatialIndex.isNearRoad(level, pos);
    }

    /**
     * 清除指定维度的缓存
     */
    public static void clearCache(ServerLevel level) {
        RoadSpatialIndex.clearCache(level);
    }

    /**
     * 清除所有缓存
     */
    public static void clearAllCache() {
        RoadSpatialIndex.clearAllCache();
    }

    /**
     * 使指定区块的缓存失效
     */
    public static void invalidateChunk(ServerLevel level, int cx, int cz) {
        RoadSpatialIndex.invalidateChunk(level, cx, cz);
    }
}
