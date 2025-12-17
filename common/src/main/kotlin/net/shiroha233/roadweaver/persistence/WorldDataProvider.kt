package net.shiroha233.roadweaver.persistence

import dev.architectury.injectables.annotations.ExpectPlatform
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.shiroha233.roadweaver.helpers.Records

object WorldDataProviderAccess {
    @JvmStatic
    @ExpectPlatform
    fun getInstance(): WorldDataProvider {
        throw AssertionError()
    }
}

/**
 * 跨平台世界数据访问抽象（Common）。
 * 使用 @ExpectPlatform 提供平台端实现提供者。
 */
abstract class WorldDataProvider {
    companion object {
        @JvmStatic
        fun getInstance(): WorldDataProvider = WorldDataProviderAccess.getInstance()
    }

    // 结构位置（用于幂等性检查）
    abstract fun getStructureLocations(level: ServerLevel): Records.StructureLocationData
    abstract fun setStructureLocations(level: ServerLevel, data: Records.StructureLocationData)

    // 结构连接（道路规划）
    abstract fun getStructureConnections(level: ServerLevel): List<Records.StructureConnection>
    abstract fun setStructureConnections(level: ServerLevel, connections: List<Records.StructureConnection>)

    // 规划覆盖：tile 键集合与中心点映射
    abstract fun getPlannedTileKeys(level: ServerLevel): Set<Long>
    abstract fun setPlannedTileKeys(level: ServerLevel, keys: Set<Long>)
    abstract fun getPlannedTileCenters(level: ServerLevel): Map<Long, Long>
    abstract fun setPlannedTileCenters(level: ServerLevel, centers: Map<Long, Long>)

    // 便捷方法：添加单个结构位置
    fun addStructureLocation(level: ServerLevel, pos: BlockPos) {
        val data = getStructureLocations(level)
        val locations = ArrayList(data.structureLocations)
        if (!locations.contains(pos)) {
            locations.add(pos)
            setStructureLocations(level, Records.StructureLocationData(locations))
        }
    }
}
