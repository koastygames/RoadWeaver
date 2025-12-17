package net.shiroha233.roadweaver.persistence.sqlite

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.LevelResource
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.ConcurrentHashMap

/**
 * SQLite 数据库连接管理器
 */
object RoadDatabaseManager {
    private val LOGGER = LoggerFactory.getLogger("roadweaver")

    @Volatile
    private var SQLITE_DRIVER_LOADED = false

    private val CONNECTIONS: ConcurrentHashMap<String, Connection> = ConcurrentHashMap()

    private const val DB_DIR = "data/roadweaver"
    private const val DB_NAME = "roads.db"

    private fun dimKey(level: ServerLevel): String {
        val rl: ResourceLocation = level.dimension().location()
        return rl.namespace + "_" + rl.path
    }

    private fun worldKey(level: ServerLevel): String {
        val worldRoot: Path? = level.server.getWorldPath(LevelResource.ROOT)
        val worldId = worldRoot?.toAbsolutePath()?.normalize()?.toString() ?: "unknown"
        return worldId + "|" + dimKey(level)
    }

    private fun getDbPath(level: ServerLevel): Path {
        val worldRoot = level.server.getWorldPath(LevelResource.ROOT)
        return worldRoot.resolve(DB_DIR).resolve(dimKey(level)).resolve(DB_NAME)
    }

    @Throws(SQLException::class)
    private fun ensureSqliteDriverLoaded() {
        if (SQLITE_DRIVER_LOADED) return
        synchronized(this) {
            if (SQLITE_DRIVER_LOADED) return
            try {
                Class.forName("org.sqlite.JDBC")
                SQLITE_DRIVER_LOADED = true
            } catch (e: ClassNotFoundException) {
                throw SQLException(
                    "SQLite JDBC driver not found. Dependency org.xerial:sqlite-jdbc may be missing.",
                    e
                )
            }
        }
    }

    /**
     * 获取或创建数据库连接
     */
    @JvmStatic
    @Throws(SQLException::class)
    fun getConnection(level: ServerLevel): Connection {
        val key = worldKey(level)

        var conn = CONNECTIONS[key]
        if (conn != null && !conn.isClosed) {
            return conn
        }

        synchronized(CONNECTIONS) {
            conn = CONNECTIONS[key]
            if (conn != null && !conn.isClosed) {
                return conn!!
            }

            try {
                val dbPath = getDbPath(level)
                Files.createDirectories(dbPath.parent)

                ensureSqliteDriverLoaded()

                val url = "jdbc:sqlite:" + dbPath.toAbsolutePath()
                conn = DriverManager.getConnection(url)

                conn!!.createStatement().use { stmt ->
                    stmt.execute("PRAGMA journal_mode=WAL")
                    stmt.execute("PRAGMA synchronous=NORMAL")
                    stmt.execute("PRAGMA cache_size=-8000")
                    stmt.execute("PRAGMA temp_store=MEMORY")
                    stmt.execute("PRAGMA foreign_keys=ON")
                }

                initTables(conn!!)

                CONNECTIONS[key] = conn!!
                LOGGER.debug("RoadDatabaseManager: 已创建维度 {} 的数据库连接", dimKey(level))

                try {
                    val migrated = LegacyShardMigration.migrateIfNeeded(level)
                    if (migrated > 0) {
                        LOGGER.info("RoadDatabaseManager: 维度 {} 已迁移 {} 条旧道路数据", dimKey(level), migrated)
                    }
                } catch (e: Exception) {
                    LOGGER.warn("RoadDatabaseManager: 旧数据迁移失败，不影响正常使用", e)
                }

                return conn!!
            } catch (e: Exception) {
                LOGGER.error("RoadDatabaseManager: 创建数据库连接失败", e)
                throw SQLException("Failed to create database connection", e)
            }
        }
    }

    @Throws(SQLException::class)
    private fun initTables(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS roads (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fingerprint INTEGER NOT NULL UNIQUE,
                    width INTEGER NOT NULL,
                    road_type INTEGER NOT NULL,
                    min_x INTEGER NOT NULL,
                    min_z INTEGER NOT NULL,
                    max_x INTEGER NOT NULL,
                    max_z INTEGER NOT NULL,
                    data BLOB NOT NULL,
                    created_at INTEGER DEFAULT (strftime('%s', 'now'))
                )
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_roads_spatial
                ON roads (min_x, max_x, min_z, max_z)
                """.trimIndent()
            )

            stmt.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_roads_fingerprint
                ON roads (fingerprint)
                """.trimIndent()
            )
        }
    }

    @JvmStatic
    fun closeConnection(level: ServerLevel) {
        val key = worldKey(level)
        val conn = CONNECTIONS.remove(key)
        if (conn != null) {
            try {
                conn.close()
                LOGGER.debug("RoadDatabaseManager: 已关闭维度 {} 的数据库连接", dimKey(level))
            } catch (e: SQLException) {
                LOGGER.warn("RoadDatabaseManager: 关闭数据库连接失败", e)
            }
        }
    }

    @JvmStatic
    fun closeAll() {
        for ((key, value) in CONNECTIONS.entries) {
            try {
                value.close()
            } catch (e: SQLException) {
                LOGGER.warn("RoadDatabaseManager: 关闭数据库连接失败: {}", key, e)
            }
        }
        CONNECTIONS.clear()
        LOGGER.debug("RoadDatabaseManager: 所有数据库连接已关闭")
    }

    @JvmStatic
    fun checkpoint(level: ServerLevel) {
        val key = worldKey(level)
        val conn = CONNECTIONS[key]
        if (conn != null) {
            try {
                conn.createStatement().use { stmt ->
                    stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)")
                }
                LOGGER.debug("RoadDatabaseManager: 维度 {} WAL 检查点完成", dimKey(level))
            } catch (e: SQLException) {
                LOGGER.warn("RoadDatabaseManager: WAL 检查点失败", e)
            }
        }
    }

    @JvmStatic
    fun checkpointAll() {
        for ((key, conn) in CONNECTIONS.entries) {
            try {
                conn.createStatement().use { stmt ->
                    stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)")
                }
            } catch (e: SQLException) {
                LOGGER.warn("RoadDatabaseManager: WAL 检查点失败: {}", key, e)
            }
        }
    }
}
