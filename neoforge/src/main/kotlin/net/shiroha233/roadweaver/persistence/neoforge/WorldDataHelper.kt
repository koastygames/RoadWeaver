package net.shiroha233.roadweaver.persistence.neoforge

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.WorldDataProvider

/**
 * NeoForge 端便捷访问工具。
 * 注意：客户端(CLIENT)世界为 ClientLevel，不可直接持久化访问，若传入非 ServerLevel 将返回空数据。
 */
object WorldDataHelper {
    @JvmStatic
    fun getStructureLocations(level: Level): Records.StructureLocationData {
        return if (level is ServerLevel) {
            WorldDataProvider.getInstance().getStructureLocations(level)
        } else {
            Records.StructureLocationData(ArrayList())
        }
    }

    @JvmStatic
    fun getConnectedStructures(level: Level): List<Records.StructureConnection> {
        return if (level is ServerLevel) {
            WorldDataProvider.getInstance().getStructureConnections(level)
        } else {
            ArrayList()
        }
    }

    @JvmStatic
    fun setStructureLocations(level: Level, data: Records.StructureLocationData) {
        if (level is ServerLevel) {
            WorldDataProvider.getInstance().setStructureLocations(level, data)
        }
    }

    @JvmStatic
    fun setStructureConnections(level: Level, connections: List<Records.StructureConnection>) {
        if (level is ServerLevel) {
            WorldDataProvider.getInstance().setStructureConnections(level, connections)
        }
    }
}
