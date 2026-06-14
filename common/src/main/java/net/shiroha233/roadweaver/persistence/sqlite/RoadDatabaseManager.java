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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * H2 数据库连接管理器——分库架构。
 *
 * 三个独立数据库文件，互不阻塞：
 * - roadweaver (roads.db):  道路数据 + 路牌数据
 * - terrain   (terrain.db): 粗采样地形瓦片（大 BLOB，写入最慢，隔离防阻塞）
 * - map       (map.db):     结构点 + 扫描状态 + 元数据（地图渲染高频读）
 *
 * 每个库拥有独立的读写连接和单线程写执行器。
 */
public final class RoadDatabaseManager {
    private RoadDatabaseManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static volatile java.sql.Driver H2_DRIVER_INSTANCE = null;
    private static final String DB_DIR = "data/roadweaver";

    // ── 分库定义 ──────────────────────────────────────────────

    /** 道路+路牌库：roads, pending_sign_texts */
    public static final String DB_ROAD = "roads";
    /** 粗采样地形库：terrain_tiles */
    public static final String DB_TERRAIN = "terrain";
    /** 地图库：structures, structure_scan_tiles, structure_cache_meta */
    public static final String DB_MAP = "map";

    // ── 每库独立连接池 + 写执行器 ──────────────────────────────

    private static final ConcurrentHashMap<String, Connection> READ_CONNECTIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Connection> WRITE_CONNECTIONS = new ConcurrentHashMap<>();

    private static final ExecutorService ROAD_WRITER = createWriter("RW-RoadWriter");
    private static final ExecutorService TERRAIN_WRITER = createWriter("RW-TerrainWriter");
    private static final ExecutorService MAP_WRITER = createWriter("RW-MapWriter");

    private static ExecutorService createWriter(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    // ── 保留兼容的全局锁和别名 ─────────────────────────────────

    public static final java.util.concurrent.locks.ReentrantReadWriteLock DB_RW_LOCK =
            new java.util.concurrent.locks.ReentrantReadWriteLock();
    public static final Object DB_LOCK = new Object();

    // ── 公开 API ──────────────────────────────────────────────

    /**
     * 获取默认（道路）库的读连接。保持向后兼容。
     */
    public static Connection getConnection(ServerLevel level) throws SQLException {
        return getReadConnection(level, DB_ROAD);
    }

    /** 获取指定库的读连接（别名） */
    public static Connection getConnection(ServerLevel level, String dbName) throws SQLException {
        return getReadConnection(level, dbName);
    }

    public static Connection getReadConnection(ServerLevel level) throws SQLException {
        return getReadConnection(level, DB_ROAD);
    }

    public static Connection getWriteConnection(ServerLevel level) throws SQLException {
        return getWriteConnection(level, DB_ROAD);
    }

    /** 获取指定库的读连接 */
    public static Connection getReadConnection(ServerLevel level, String dbName) throws SQLException {
        String key = connectionKey(level, dbName, "r");
        Connection conn = READ_CONNECTIONS.get(key);
        if (conn != null && !conn.isClosed()) return conn;

        synchronized (READ_CONNECTIONS) {
            conn = READ_CONNECTIONS.get(key);
            if (conn != null && !conn.isClosed()) return conn;
            conn = createConnection(level, dbName, true);
            READ_CONNECTIONS.put(key, conn);
            return conn;
        }
    }

    /** 获取指定库的写连接 */
    public static Connection getWriteConnection(ServerLevel level, String dbName) throws SQLException {
        String key = connectionKey(level, dbName, "w");
        Connection conn = WRITE_CONNECTIONS.get(key);
        if (conn != null && !conn.isClosed()) return conn;

        synchronized (WRITE_CONNECTIONS) {
            conn = WRITE_CONNECTIONS.get(key);
            if (conn != null && !conn.isClosed()) return conn;
            conn = createConnection(level, dbName, false);
            WRITE_CONNECTIONS.put(key, conn);
            return conn;
        }
    }

    /** 获取指定库的写执行器 */
    public static ExecutorService writeExecutor(String dbName) {
        return switch (dbName) {
            case DB_TERRAIN -> TERRAIN_WRITER;
            case DB_MAP -> MAP_WRITER;
            default -> ROAD_WRITER;
        };
    }

    /** 获取默认（道路）库的写执行器。保持向后兼容。 */
    public static ExecutorService writeExecutor() {
        return ROAD_WRITER;
    }

    static String dimKey(ServerLevel level) {
        ResourceLocation rl = level.dimension().location();
        return rl.getNamespace() + "_" + rl.getPath();
    }

    private static String worldKey(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        String worldId = worldRoot == null ? "unknown" : worldRoot.toAbsolutePath().normalize().toString();
        return worldId + "|" + dimKey(level);
    }

    private static String connectionKey(ServerLevel level, String dbName, String mode) {
        return worldKey(level) + "|" + dbName + "|" + mode;
    }

    static Path getDbPath(ServerLevel level) {
        return getDbPath(level, DB_ROAD);
    }

    static Path getDbPath(ServerLevel level, String dbName) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve(DB_DIR).resolve(dimKey(level)).resolve(dbName);
    }

    // ── 连接创建 ──────────────────────────────────────────────

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

    private static Connection createConnection(ServerLevel level, String dbName, boolean readOnly) throws SQLException {
        try {
            Path dbPath = getDbPath(level, dbName);
            Files.createDirectories(dbPath.getParent());
            ensureH2DriverAvailable();

            String url = "jdbc:h2:" + dbPath.toAbsolutePath()
                    + ";MODE=LEGACY"
                    + ";FILE_LOCK=NO"
                    + ";AUTO_SERVER=FALSE"
                    + ";CACHE_SIZE=8192"
                    + ";LOCK_MODE=0";

            Connection conn = DriverManager.getConnection(url, "sa", "");

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET WRITE_DELAY 100");
                stmt.execute("SET LOCK_TIMEOUT 5000");
            }

            if (readOnly) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SET READ_ONLY TRUE");
                } catch (SQLException ignored) {}
            }

            initTablesForDb(conn, dbName);
            return conn;
        } catch (Exception e) {
            LOGGER.error("创建数据库连接失败 db={}", dbName, e);
            throw new SQLException("Failed to create database connection: " + dbName, e);
        }
    }

    // ── 按库分表初始化 ─────────────────────────────────────────

    private static void initTablesForDb(Connection conn, String dbName) throws SQLException {
        switch (dbName) {
            case DB_ROAD -> initRoadTables(conn);
            case DB_TERRAIN -> initTerrainTables(conn);
            case DB_MAP -> initMapTables(conn);
        }
    }

    private static void initRoadTables(Connection conn) throws SQLException {
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

    private static void initTerrainTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS terrain_tiles ("
                    + "tile_x INT NOT NULL,"
                    + "tile_z INT NOT NULL,"
                    + "tile_size_chunks INT NOT NULL,"
                    + "step INT NOT NULL,"
                    + "schema_version INT NOT NULL,"
                    + "sample_width INT NOT NULL,"
                    + "sample_height INT NOT NULL,"
                    + "sea_level INT NOT NULL,"
                    + "data BLOB NOT NULL,"
                    + "updated_at BIGINT DEFAULT EXTRACT(EPOCH FROM CURRENT_TIMESTAMP),"
                    + "PRIMARY KEY (tile_x, tile_z, tile_size_chunks, step, schema_version)"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_terrain_tiles_version ON terrain_tiles (schema_version, tile_size_chunks, step)");
        }
    }

    private static void initMapTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
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
        }
    }

    // ── 生命周期管理 ──────────────────────────────────────────

    public static void closeConnection(ServerLevel level) {
        String wk = worldKey(level);
        for (String dbName : new String[]{DB_ROAD, DB_TERRAIN, DB_MAP}) {
            String rk = wk + "|" + dbName + "|r";
            Connection rc = READ_CONNECTIONS.remove(rk);
            if (rc != null) { try { rc.close(); } catch (SQLException e) { LOGGER.warn("关闭读连接失败", e); } }
            String wkKey = wk + "|" + dbName + "|w";
            Connection wc = WRITE_CONNECTIONS.remove(wkKey);
            if (wc != null) { try { wc.close(); } catch (SQLException e) { LOGGER.warn("关闭写连接失败", e); } }
        }
    }

    public static void closeAll() {
        for (var entry : READ_CONNECTIONS.entrySet()) {
            try { entry.getValue().close(); } catch (SQLException e) { LOGGER.warn("关闭读连接失败: {}", entry.getKey(), e); }
        }
        READ_CONNECTIONS.clear();
        for (var entry : WRITE_CONNECTIONS.entrySet()) {
            try { entry.getValue().close(); } catch (SQLException e) { LOGGER.warn("关闭写连接失败: {}", entry.getKey(), e); }
        }
        WRITE_CONNECTIONS.clear();
    }

    public static void checkpoint(ServerLevel level) {
        for (String dbName : new String[]{DB_ROAD, DB_TERRAIN, DB_MAP}) {
            String key = worldKey(level) + "|" + dbName + "|w";
            Connection wc = WRITE_CONNECTIONS.get(key);
            if (wc != null) {
                try (Statement stmt = wc.createStatement()) {
                    stmt.execute("CHECKPOINT SYNC");
                } catch (SQLException e) {
                    LOGGER.warn("检查点失败 db={}", dbName, e);
                }
            }
        }
    }

    public static void checkpointAll() {
        for (var entry : WRITE_CONNECTIONS.entrySet()) {
            try (Statement stmt = entry.getValue().createStatement()) {
                stmt.execute("CHECKPOINT SYNC");
            } catch (SQLException e) {
                LOGGER.warn("检查点失败: {}", entry.getKey(), e);
            }
        }
    }

    public static void shutdown() {
        ROAD_WRITER.shutdown();
        TERRAIN_WRITER.shutdown();
        MAP_WRITER.shutdown();
        try {
            ROAD_WRITER.awaitTermination(5, TimeUnit.SECONDS);
            TERRAIN_WRITER.awaitTermination(5, TimeUnit.SECONDS);
            MAP_WRITER.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
        closeAll();
    }
}
