package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;

/**
 * H2 数据库连接管理器，每个维度一个独立数据库文件
 */
public final class RoadDatabaseManager {
    private RoadDatabaseManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static volatile java.sql.Driver H2_DRIVER_INSTANCE = null;
    private static final ConcurrentHashMap<String, Connection> CONNECTIONS = new ConcurrentHashMap<>();
    private static final String DB_DIR = "data/roadweaver";
    private static final String DB_NAME = "roads";

    static String dimKey(ServerLevel level) {
        ResourceLocation rl = level.dimension().location();
        return rl.getNamespace() + "_" + rl.getPath();
    }

    private static String worldKey(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        String worldId = worldRoot == null ? "unknown" : worldRoot.toAbsolutePath().normalize().toString();
        return worldId + "|" + dimKey(level);
    }

    static Path getDbPath(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve(DB_DIR).resolve(dimKey(level)).resolve(DB_NAME);
    }

    private static void ensureH2DriverAvailable() throws SQLException {
        try {
            java.sql.Driver driver = DriverManager.getDriver("jdbc:h2:");
            if (driver != null) return;
        } catch (SQLException ignored) {}

        if (H2_DRIVER_INSTANCE == null) {
            synchronized (RoadDatabaseManager.class) {
                if (H2_DRIVER_INSTANCE == null) {
                    try {
                        H2_DRIVER_INSTANCE = new org.h2.Driver();
                        DriverManager.registerDriver(H2_DRIVER_INSTANCE);
                    } catch (Exception e) {
                        throw new SQLException("Failed to register H2 driver", e);
                    }
                }
            }
        }
    }

    public static Connection getConnection(ServerLevel level) throws SQLException {
        String key = worldKey(level);

        Connection conn = CONNECTIONS.get(key);
        if (conn != null && !conn.isClosed()) return conn;

        synchronized (CONNECTIONS) {
            conn = CONNECTIONS.get(key);
            if (conn != null && !conn.isClosed()) return conn;

            try {
                Path dbPath = getDbPath(level);
                Files.createDirectories(dbPath.getParent());
                ensureH2DriverAvailable();

                String url = "jdbc:h2:" + dbPath.toAbsolutePath()
                        + ";MODE=LEGACY"
                        + ";FILE_LOCK=NO"
                        + ";AUTO_SERVER=FALSE"
                        + ";CACHE_SIZE=8192";

                conn = DriverManager.getConnection(url, "sa", "");

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SET WRITE_DELAY 1000");
                    stmt.execute("SET LOCK_TIMEOUT 10000");
                }

                initTables(conn);
                CONNECTIONS.put(key, conn);
                return conn;
            } catch (Exception e) {
                LOGGER.error("创建数据库连接失败", e);
                throw new SQLException("Failed to create database connection", e);
            }
        }
    }

    private static void initTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS roads ("
                    + "id IDENTITY PRIMARY KEY,"
                    + "fingerprint BIGINT NOT NULL UNIQUE,"
                    + "width INT NOT NULL,"
                    + "road_type INT NOT NULL,"
                    + "min_x INT NOT NULL,"
                    + "min_z INT NOT NULL,"
                    + "max_x INT NOT NULL,"
                    + "max_z INT NOT NULL,"
                    + "data BLOB NOT NULL,"
                    + "created_at BIGINT DEFAULT EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)"
                    + ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_roads_spatial ON roads (min_x, max_x, min_z, max_z)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_roads_fingerprint ON roads (fingerprint)");

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS structures ("
                    + "id IDENTITY PRIMARY KEY,"
                    + "x INT NOT NULL,"
                    + "z INT NOT NULL,"
                    + "structure_id VARCHAR(255) NOT NULL,"
                    + "source INT NOT NULL,"
                    + "verified_at BIGINT DEFAULT EXTRACT(EPOCH FROM CURRENT_TIMESTAMP),"
                    + "CONSTRAINT uq_structures UNIQUE (x, z, structure_id, source)"
                    + ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_structures_xz ON structures (x, z)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_structures_structure_id ON structures (structure_id)");

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS structure_scan_tiles ("
                    + "tile_x INT NOT NULL,"
                    + "tile_z INT NOT NULL,"
                    + "tile_size_chunks INT NOT NULL,"
                    + "scanned_at BIGINT DEFAULT EXTRACT(EPOCH FROM CURRENT_TIMESTAMP),"
                    + "PRIMARY KEY (tile_x, tile_z, tile_size_chunks)"
                    + ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_structure_scan_tiles_range ON structure_scan_tiles (tile_size_chunks, tile_x, tile_z)");

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS structure_cache_meta ("
                    + "k VARCHAR(255) PRIMARY KEY,"
                    + "v VARCHAR(4096) NOT NULL"
                    + ")");

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS pending_sign_texts ("
                    + "id IDENTITY PRIMARY KEY,"
                    + "chunk_x INT NOT NULL,"
                    + "chunk_z INT NOT NULL,"
                    + "x INT NOT NULL,"
                    + "y INT NOT NULL,"
                    + "z INT NOT NULL,"
                    + "sign_type INT NOT NULL,"
                    + "payload VARCHAR(255) NOT NULL,"
                    + "updated_at BIGINT NOT NULL,"
                    + "CONSTRAINT uq_pending_sign_pos UNIQUE (x, y, z)"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_pending_sign_chunk ON pending_sign_texts (chunk_x, chunk_z, id)");
        }
    }

    public static void closeConnection(ServerLevel level) {
        String key = worldKey(level);
        Connection conn = CONNECTIONS.remove(key);
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                LOGGER.warn("关闭数据库连接失败", e);
            }
        }
    }

    public static void closeAll() {
        for (var entry : CONNECTIONS.entrySet()) {
            try {
                entry.getValue().close();
            } catch (SQLException e) {
                LOGGER.warn("关闭数据库连接失败: {}", entry.getKey(), e);
            }
        }
        CONNECTIONS.clear();
    }

    public static void checkpoint(ServerLevel level) {
        String key = worldKey(level);
        Connection conn = CONNECTIONS.get(key);
        if (conn != null) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CHECKPOINT SYNC");
            } catch (SQLException e) {
                LOGGER.warn("检查点失败", e);
            }
        }
    }

    public static void checkpointAll() {
        for (var entry : CONNECTIONS.entrySet()) {
            try (Statement stmt = entry.getValue().createStatement()) {
                stmt.execute("CHECKPOINT SYNC");
            } catch (SQLException e) {
                LOGGER.warn("检查点失败: {}", entry.getKey(), e);
            }
        }
    }
}
