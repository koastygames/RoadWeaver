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
 * H2 数据库连接管理器
 * 
 * 每个维度一个独立的 H2 数据库文件，使用 MVStore 引擎。
 * H2 是纯 Java 实现，无需 native 库，避免平台审核问题。
 */
public final class RoadDatabaseManager {
    private RoadDatabaseManager() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private static volatile boolean H2_DRIVER_LOADED = false;

    // H2 驱动类名（支持重定位后的包名）
    private static final String[] H2_DRIVERS = {
            "net.shiroha233.roadweaver.libs.h2.Driver", // 生产环境 (Relocated)
            "org.h2.Driver" // 开发环境 (Original)
    };

    // 按维度存储的数据库连接
    private static final ConcurrentHashMap<String, Connection> CONNECTIONS = new ConcurrentHashMap<>();

    // 数据库文件目录和名称
    private static final String DB_DIR = "data/roadweaver";
    private static final String DB_NAME = "roads"; // H2 会自动添加 .mv.db 后缀

    /**
     * 获取维度的唯一键
     */
    static String dimKey(ServerLevel level) {
        ResourceLocation rl = level.dimension().location();
        return rl.getNamespace() + "_" + rl.getPath();
    }

    /**
     * 获取世界的唯一键（包含世界路径）
     */
    private static String worldKey(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        String worldId = worldRoot == null ? "unknown" : worldRoot.toAbsolutePath().normalize().toString();
        return worldId + "|" + dimKey(level);
    }

    /**
     * 获取数据库文件路径（不含扩展名，H2会自动添加）
     */
    static Path getDbPath(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve(DB_DIR).resolve(dimKey(level)).resolve(DB_NAME);
    }

    /**
     * 获取旧 SQLite 数据库文件路径（用于迁移）
     */
    static Path getLegacySqliteDbPath(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve(DB_DIR).resolve(dimKey(level)).resolve("roads.db");
    }

    private static void ensureH2DriverLoaded() throws SQLException {
        if (H2_DRIVER_LOADED)
            return;
        synchronized (RoadDatabaseManager.class) {
            if (H2_DRIVER_LOADED)
                return;

            ClassNotFoundException lastException = null;

            for (String driverName : H2_DRIVERS) {
                try {
                    Class.forName(driverName, true, RoadDatabaseManager.class.getClassLoader());
                    H2_DRIVER_LOADED = true;
                    LOGGER.info("RoadDatabaseManager: H2 驱动已加载: {}", driverName);
                    return;
                } catch (ClassNotFoundException e) {
                    lastException = e;
                }

                try {
                    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
                    if (contextLoader != null) {
                        Class.forName(driverName, true, contextLoader);
                        H2_DRIVER_LOADED = true;
                        LOGGER.info("RoadDatabaseManager: H2 驱动已通过上下文类加载器加载: {}", driverName);
                        return;
                    }
                } catch (ClassNotFoundException e) {
                    lastException = e;
                }

                try {
                    Class.forName(driverName);
                    H2_DRIVER_LOADED = true;
                    LOGGER.info("RoadDatabaseManager: H2 驱动已通过系统类加载器加载: {}", driverName);
                    return;
                } catch (ClassNotFoundException e) {
                    lastException = e;
                }
            }

            throw new SQLException("H2 driver not found. Tried: " + java.util.Arrays.toString(H2_DRIVERS),
                    lastException);
        }
    }

    /**
     * 获取或创建数据库连接
     */
    public static Connection getConnection(ServerLevel level) throws SQLException {
        String key = worldKey(level);

        Connection conn = CONNECTIONS.get(key);
        if (conn != null && !conn.isClosed()) {
            return conn;
        }

        synchronized (CONNECTIONS) {
            conn = CONNECTIONS.get(key);
            if (conn != null && !conn.isClosed()) {
                return conn;
            }

            try {
                Path dbPath = getDbPath(level);
                Files.createDirectories(dbPath.getParent());

                ensureH2DriverLoaded();

                // H2 连接 URL，使用 MVStore 引擎，SQLite 兼容模式
                // FILE_LOCK=NO 避免多进程锁问题（Minecraft 单进程）
                // AUTO_SERVER=FALSE 禁用自动服务器模式
                String url = "jdbc:h2:" + dbPath.toAbsolutePath() 
                        + ";MODE=LEGACY"
                        + ";FILE_LOCK=NO"
                        + ";AUTO_SERVER=FALSE"
                        + ";CACHE_SIZE=8192";
                
                conn = DriverManager.getConnection(url, "sa", "");

                // H2 优化配置
                try (Statement stmt = conn.createStatement()) {
                    // 写延迟（毫秒），平衡性能和安全
                    stmt.execute("SET WRITE_DELAY 1000");
                    // 设置锁超时
                    stmt.execute("SET LOCK_TIMEOUT 10000");
                }

                // 初始化表结构
                initTables(conn);

                CONNECTIONS.put(key, conn);
                LOGGER.debug("RoadDatabaseManager: 已创建维度 {} 的 H2 数据库连接", dimKey(level));

                // 检查并执行旧数据迁移
                try {
                    // 1. 先检查 SQLite 迁移
                    int sqliteMigrated = LegacySqliteMigration.migrateIfNeeded(level);
                    if (sqliteMigrated > 0) {
                        LOGGER.info("RoadDatabaseManager: 维度 {} 已从 SQLite 迁移 {} 条道路数据", dimKey(level), sqliteMigrated);
                    }
                    
                    // 2. 再检查 NBT 分片迁移
                    int nbtMigrated = LegacyShardMigration.migrateIfNeeded(level);
                    if (nbtMigrated > 0) {
                        LOGGER.info("RoadDatabaseManager: 维度 {} 已从 NBT 迁移 {} 条道路数据", dimKey(level), nbtMigrated);
                    }
                } catch (Exception e) {
                    LOGGER.warn("RoadDatabaseManager: 旧数据迁移失败，不影响正常使用", e);
                }

                return conn;

            } catch (Exception e) {
                LOGGER.error("RoadDatabaseManager: 创建数据库连接失败", e);
                throw new SQLException("Failed to create database connection", e);
            }
        }
    }

    /**
     * 初始化表结构
     */
    private static void initTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 道路数据表
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS roads (" +
                            "    id IDENTITY PRIMARY KEY," +
                            "    fingerprint BIGINT NOT NULL UNIQUE," +
                            "    width INT NOT NULL," +
                            "    road_type INT NOT NULL," +
                            "    min_x INT NOT NULL," +
                            "    min_z INT NOT NULL," +
                            "    max_x INT NOT NULL," +
                            "    max_z INT NOT NULL," +
                            "    data BLOB NOT NULL," +
                            "    created_at BIGINT DEFAULT EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)" +
                            ")");

            // 空间索引
            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_roads_spatial " +
                            "ON roads (min_x, max_x, min_z, max_z)");

            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_roads_fingerprint " +
                            "ON roads (fingerprint)");

            // 结构点缓存表
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS structures (" +
                            "    id IDENTITY PRIMARY KEY," +
                            "    x INT NOT NULL," +
                            "    z INT NOT NULL," +
                            "    structure_id VARCHAR(255) NOT NULL," +
                            "    source INT NOT NULL," +
                            "    verified_at BIGINT DEFAULT EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)," +
                            "    CONSTRAINT uq_structures UNIQUE (x, z, structure_id, source)" +
                            ")");

            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_structures_xz " +
                            "ON structures (x, z)");

            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_structures_structure_id " +
                            "ON structures (structure_id)");

            // 已扫描区域标记
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS structure_scan_tiles (" +
                            "    tile_x INT NOT NULL," +
                            "    tile_z INT NOT NULL," +
                            "    tile_size_chunks INT NOT NULL," +
                            "    scanned_at BIGINT DEFAULT EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)," +
                            "    PRIMARY KEY (tile_x, tile_z, tile_size_chunks)" +
                            ")");

            stmt.execute(
                    "CREATE INDEX IF NOT EXISTS idx_structure_scan_tiles_range " +
                            "ON structure_scan_tiles (tile_size_chunks, tile_x, tile_z)");

            // 缓存元数据
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS structure_cache_meta (" +
                            "    k VARCHAR(255) PRIMARY KEY," +
                            "    v VARCHAR(4096) NOT NULL" +
                            ")");
        }
    }

    /**
     * 关闭指定维度的数据库连接
     */
    public static void closeConnection(ServerLevel level) {
        String key = worldKey(level);
        Connection conn = CONNECTIONS.remove(key);
        if (conn != null) {
            try {
                conn.close();
                LOGGER.debug("RoadDatabaseManager: 已关闭维度 {} 的数据库连接", dimKey(level));
            } catch (SQLException e) {
                LOGGER.warn("RoadDatabaseManager: 关闭数据库连接失败", e);
            }
        }
    }

    /**
     * 关闭所有数据库连接
     */
    public static void closeAll() {
        for (var entry : CONNECTIONS.entrySet()) {
            try {
                entry.getValue().close();
            } catch (SQLException e) {
                LOGGER.warn("RoadDatabaseManager: 关闭数据库连接失败: {}", entry.getKey(), e);
            }
        }
        CONNECTIONS.clear();
        LOGGER.debug("RoadDatabaseManager: 所有数据库连接已关闭");
    }

    /**
     * 执行检查点（H2 使用 CHECKPOINT 命令）
     */
    public static void checkpoint(ServerLevel level) {
        String key = worldKey(level);
        Connection conn = CONNECTIONS.get(key);
        if (conn != null) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CHECKPOINT SYNC");
                LOGGER.debug("RoadDatabaseManager: 维度 {} 检查点完成", dimKey(level));
            } catch (SQLException e) {
                LOGGER.warn("RoadDatabaseManager: 检查点失败", e);
            }
        }
    }

    /**
     * 执行所有连接的检查点
     */
    public static void checkpointAll() {
        for (var entry : CONNECTIONS.entrySet()) {
            try (Statement stmt = entry.getValue().createStatement()) {
                stmt.execute("CHECKPOINT SYNC");
            } catch (SQLException e) {
                LOGGER.warn("RoadDatabaseManager: 检查点失败: {}", entry.getKey(), e);
            }
        }
    }
}
