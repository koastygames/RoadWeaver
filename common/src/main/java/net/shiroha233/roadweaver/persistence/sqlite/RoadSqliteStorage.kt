package net.shiroha233.roadweaver.persistence.sqlite

import com.mojang.serialization.DataResult
import com.mojang.serialization.Dynamic
import com.mojang.serialization.DynamicOps
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.shiroha233.roadweaver.helpers.Records
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import kotlin.math.max
import kotlin.math.min

/**
 * 基于 SQLite 的道路数据存储
 */
object RoadSqliteStorage {
    private val LOGGER = LoggerFactory.getLogger("roadweaver")
    private val OPS: DynamicOps<Tag> = NbtOps.INSTANCE

    private const val SQL_INSERT = """
        INSERT OR IGNORE INTO roads (fingerprint, width, road_type, min_x, min_z, max_x, max_z, data)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """

    private const val SQL_QUERY_RECT = """
        SELECT data FROM roads
        WHERE max_x >= ? AND min_x <= ? AND max_z >= ? AND min_z <= ?
    """

    private const val SQL_EXISTS = """
        SELECT 1 FROM roads WHERE fingerprint = ? LIMIT 1
    """

    @JvmStatic
    fun addRoad(level: ServerLevel, rd: Records.RoadData) {
        val segs = rd.roadSegmentList
        if (segs.isEmpty()) return

        var minX = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        for (seg in segs) {
            val p = seg.middlePos
            val x = p.x
            val z = p.z
            if (x < minX) minX = x
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (z > maxZ) maxZ = z
        }

        val fingerprint = fingerprint(rd)

        try {
            val conn = RoadDatabaseManager.getConnection(level)

            // 先检查是否已存在（快速路径）
            conn.prepareStatement(SQL_EXISTS).use { checkStmt ->
                checkStmt.setLong(1, fingerprint)
                checkStmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return
                    }
                }
            }

            val data = serializeRoadData(rd)
            if (data == null) {
                LOGGER.warn("RoadSqliteStorage: 序列化道路数据失败")
                return
            }

            conn.prepareStatement(SQL_INSERT).use { stmt ->
                stmt.setLong(1, fingerprint)
                stmt.setInt(2, rd.width)
                stmt.setInt(3, rd.roadType)
                stmt.setInt(4, minX)
                stmt.setInt(5, minZ)
                stmt.setInt(6, maxX)
                stmt.setInt(7, maxZ)
                stmt.setBytes(8, data)
                stmt.executeUpdate()
            }
        } catch (e: SQLException) {
            LOGGER.error("RoadSqliteStorage: 添加道路数据失败", e)
        }
    }

    @JvmStatic
    fun queryRect(level: ServerLevel, minBlockX: Int, minBlockZ: Int, maxBlockX: Int, maxBlockZ: Int): List<Records.RoadData> {
        val result = ArrayList<Records.RoadData>()
        val seen = HashSet<Long>()

        try {
            val conn = RoadDatabaseManager.getConnection(level)
            conn.prepareStatement(SQL_QUERY_RECT).use { stmt ->
                stmt.setInt(1, minBlockX)
                stmt.setInt(2, maxBlockX)
                stmt.setInt(3, minBlockZ)
                stmt.setInt(4, maxBlockZ)

                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        val data = rs.getBytes("data")
                        val rd = deserializeRoadData(data)
                        if (rd != null) {
                            val fp = fingerprint(rd)
                            if (seen.add(fp)) {
                                if (intersects(rd, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
                                    result.add(rd)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            LOGGER.error("RoadSqliteStorage: 查询道路数据失败", e)
        }

        return result
    }

    private fun intersects(rd: Records.RoadData, minX: Int, minZ: Int, maxX: Int, maxZ: Int): Boolean {
        val segs = rd.roadSegmentList
        if (segs.isEmpty()) return false

        var rminX = Int.MAX_VALUE
        var rminZ = Int.MAX_VALUE
        var rmaxX = Int.MIN_VALUE
        var rmaxZ = Int.MIN_VALUE

        for (seg in segs) {
            val p = seg.middlePos
            val x = p.x
            val z = p.z
            if (x < rminX) rminX = x
            if (z < rminZ) rminZ = z
            if (x > rmaxX) rmaxX = x
            if (z > rmaxZ) rmaxZ = z
        }

        return !(rmaxX < minX || rminX > maxX || rmaxZ < minZ || rminZ > maxZ)
    }

    private fun fingerprint(rd: Records.RoadData): Long {
        val segs = rd.roadSegmentList
        if (segs.isEmpty()) return 0L
        val a = segs[0].middlePos
        val b = segs[segs.size - 1].middlePos
        val ka = (a.x.toLong() shl 32) xor (a.z.toLong() and 0xffffffffL)
        val kb = (b.x.toLong() shl 32) xor (b.z.toLong() and 0xffffffffL)
        val lo = min(ka, kb)
        val hi = max(ka, kb)
        var f = (hi shl 1) xor lo
        f = f xor (rd.width.toLong() and 0xffffffffL)
        f = f xor ((rd.roadType.toLong() and 0xffffffffL) shl 33)
        return f
    }

    private fun serializeRoadData(rd: Records.RoadData): ByteArray? {
        return try {
            val result: DataResult<Tag> = Records.RoadData.CODEC.encodeStart(OPS, rd)
            val tag = result.result().orElse(null) ?: return null

            val compound = CompoundTag()
            compound.put("road", tag)

            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)
            NbtIo.write(compound, dos)
            baos.toByteArray()
        } catch (e: Exception) {
            LOGGER.error("RoadSqliteStorage: 序列化失败", e)
            null
        }
    }

    private fun deserializeRoadData(data: ByteArray?): Records.RoadData? {
        if (data == null || data.isEmpty()) return null

        return try {
            val bais = ByteArrayInputStream(data)
            val dis = DataInputStream(bais)
            val compound = NbtIo.read(dis) ?: return null
            if (!compound.contains("road")) return null

            val tag = compound.get("road")
            val result: DataResult<Records.RoadData> = Records.RoadData.CODEC.parse(Dynamic(OPS, tag))
            result.result().orElse(null)
        } catch (e: Exception) {
            LOGGER.error("RoadSqliteStorage: 反序列化失败", e)
            null
        }
    }

    @JvmStatic
    fun flushAll(level: ServerLevel) {
        RoadDatabaseManager.checkpoint(level)
    }

    @JvmStatic
    fun clearAll(level: ServerLevel) {
        // 这里保持原 Java 行为：不实现（除非你明确需要删库功能）
        // 目前项目里也没有调用路径。
    }

    @JvmStatic
    fun shutdown() {
        RoadDatabaseManager.checkpointAll()
        RoadDatabaseManager.closeAll()
    }
}
