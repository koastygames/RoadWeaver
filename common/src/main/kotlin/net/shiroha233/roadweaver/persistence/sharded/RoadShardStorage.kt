package net.shiroha233.roadweaver.persistence.sharded

import net.minecraft.server.level.ServerLevel
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.sqlite.RoadDatabaseManager
import net.shiroha233.roadweaver.persistence.sqlite.RoadSqliteStorage

/**
 * 道路数据存储（SQLite 实现）
 *
 * 此类现在是 RoadSqliteStorage 的门面（Facade），
 * 保持向后兼容的 API，同时底层使用 SQLite 数据库。
 */
object RoadShardStorage {
    @JvmStatic
    fun addRoad(level: ServerLevel, rd: Records.RoadData) {
        RoadSqliteStorage.addRoad(level, rd)
    }

    @JvmStatic
    fun queryRect(level: ServerLevel, minBlockX: Int, minBlockZ: Int, maxBlockX: Int, maxBlockZ: Int): List<Records.RoadData> {
        return RoadSqliteStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ)
    }

    @JvmStatic
    fun flushAll(level: ServerLevel) {
        RoadSqliteStorage.flushAll(level)
    }

    @JvmStatic
    fun clearAll(level: ServerLevel) {
        RoadSqliteStorage.clearAll(level)
    }

    @JvmStatic
    fun shutdown() {
        RoadSqliteStorage.shutdown()
    }

    @JvmStatic
    fun closeConnection(level: ServerLevel) {
        RoadDatabaseManager.checkpoint(level)
        RoadDatabaseManager.closeConnection(level)
    }
}
