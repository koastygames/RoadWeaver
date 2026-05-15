/* 文件职责：管理每个世界维度的道路数据库连接与串行访问锁。 */
package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.resources.Identifier;
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
 * H2 数据库连接管理器，每个维度共享一个连接，并提供同维度串行访问锁。
 */
public final class RoadDatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final ConcurrentHashMap<String, Connection> CONNECTIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Object> CONNECTION_MUTEXES = new ConcurrentHashMap<>();
    private static volatile java.sql.Driver h2DriverInstance;

    private static final String DB_DIR = "data/roadweaver";
    private static final String DB_NAME = "roads";

    private RoadDatabaseManager() {
    }

    static String dimKey(ServerLevel level) {
        Identifier id = level.dimension().identifier();
        return id.getNamespace() + "_" + id.getPath();
    }

    private static String worldKey(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        String worldId = worldRoot == null ? "unknown" : worldRoot.toAbsolutePath().normalize().toString();
        return worldId + "|" + dimKey(level);
    }

    static Object connectionMutex(ServerLevel level) {
        return CONNECTION_MUTEXES.computeIfAbsent(worldKey(level), ignored -> new Object());
    }

    static Path getDbPath(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve(DB_DIR).resolve(dimKey(level)).resolve(DB_NAME);
    }

    private static void ensureH2DriverAvailable() throws SQLException {
        try {
            if (DriverManager.getDriver("jdbc:h2:") != null) {
                return;
            }
        } catch (SQLException ignored) {
        }

        if (h2DriverInstance != null) {
            return;
        }
        synchronized (RoadDatabaseManager.class) {
            if (h2DriverInstance != null) {
                return;
            }
            try {
                h2DriverInstance = new org.h2.Driver();
                DriverManager.registerDriver(h2DriverInstance);
            } catch (Exception e) {
                throw new SQLException("Failed to register H2 driver", e);
            }
        }
    }

    public static Connection getConnection(ServerLevel level) throws SQLException {
        String key = worldKey(level);
        Connection existing = CONNECTIONS.get(key);
        if (existing != null && !existing.isClosed()) {
            return existing;
        }

        synchronized (CONNECTIONS) {
            existing = CONNECTIONS.get(key);
            if (existing != null && !existing.isClosed()) {
                return existing;
            }

            try {
                Path dbPath = getDbPath(level);
                Files.createDirectories(dbPath.getParent());
                ensureH2DriverAvailable();

                String url = "jdbc:h2:" + dbPath.toAbsolutePath()
                        + ";MODE=LEGACY"
                        + ";FILE_LOCK=NO"
                        + ";AUTO_SERVER=FALSE"
                        + ";CACHE_SIZE=8192";

                Connection connection = DriverManager.getConnection(url, "sa", "");
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SET WRITE_DELAY 1000");
                    stmt.execute("SET LOCK_TIMEOUT 10000");
                }
                initTables(connection);
                CONNECTIONS.put(key, connection);
                return connection;
            } catch (Exception e) {
                LOGGER.error("Failed to create road database connection", e);
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
                LOGGER.warn("Failed to close road database connection", e);
            }
        }
        CONNECTION_MUTEXES.remove(key);
    }

    public static void closeAll() {
        for (var entry : CONNECTIONS.entrySet()) {
            try {
                entry.getValue().close();
            } catch (SQLException e) {
                LOGGER.warn("Failed to close road database connection {}", entry.getKey(), e);
            }
        }
        CONNECTIONS.clear();
        CONNECTION_MUTEXES.clear();
    }

    public static void checkpoint(ServerLevel level) {
        String key = worldKey(level);
        synchronized (connectionMutex(level)) {
            Connection conn = CONNECTIONS.get(key);
            if (conn == null) {
                return;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CHECKPOINT SYNC");
            } catch (SQLException e) {
                LOGGER.warn("Road database checkpoint failed", e);
            }
        }
    }

    public static void checkpointAll() {
        for (var entry : CONNECTIONS.entrySet()) {
            Object mutex = CONNECTION_MUTEXES.computeIfAbsent(entry.getKey(), ignored -> new Object());
            synchronized (mutex) {
                try (Statement stmt = entry.getValue().createStatement()) {
                    stmt.execute("CHECKPOINT SYNC");
                } catch (SQLException e) {
                    LOGGER.warn("Road database checkpoint failed: {}", entry.getKey(), e);
                }
            }
        }
    }
}
