package net.shiroha233.roadweaver.persistence

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.WorldGenLevel

/**
 * 道路位置查询服务，用于阻止树木在道路上生成。
 *
 * 此类现在作为 RoadSpatialIndex 的简单门面（Facade），
 * 保持向后兼容的 API，同时底层使用高效的网格空间索引。
 */
object RoadPositionQuery {
    @JvmStatic
    fun isOnRoad(level: ServerLevel, pos: BlockPos): Boolean {
        return RoadSpatialIndex.isNearRoadServer(level, pos)
    }

    @JvmStatic
    fun isOnRoad(level: WorldGenLevel, pos: BlockPos): Boolean {
        return RoadSpatialIndex.isNearRoad(level, pos)
    }

    @JvmStatic
    fun clearCache(level: ServerLevel) {
        RoadSpatialIndex.clearCache(level)
    }

    @JvmStatic
    fun clearAllCache() {
        RoadSpatialIndex.clearAllCache()
    }

    @JvmStatic
    fun invalidateChunk(level: ServerLevel, cx: Int, cz: Int) {
        RoadSpatialIndex.invalidateChunk(level, cx, cz)
    }
}
