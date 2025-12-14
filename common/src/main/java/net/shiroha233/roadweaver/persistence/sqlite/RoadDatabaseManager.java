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
 * SQLite 数据库连接管理器
 * 
 * 每个维度一个独立的 SQLite 数据库文件，使用 WAL 模式支持并发读写。
 * WAL (Write-Ahead Logging) 模式的优势：
 * - 读操作不阻塞写操作
 * - 写操作不阻塞读操作
 * - 更好的并发性能
 */
public final class RoadDatabaseManager {
    private RoadDatabaseManager() {}
    
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    // NeoForge/Fabric 的 dev 环境下偶发不会自动触发 JDBC Service Provider 的加载，
    // 导致 DriverManager 找不到 sqlite 驱动（No suitable driver）。这里做一次显式加载兜底。
    private static volatile boolean SQLITE_DRIVER_LOADED = false;
    
    // 按维度存储的数据库连接（每个维度一个连接池）
    private static final ConcurrentHashMap<String, Connection> CONNECTIONS = new ConcurrentHashMap<>();
    
    // 数据库文件目录
    private static final String DB_DIR = "data/roadweaver";
    private static final String DB_NAME = "roads.db";
    
    /**
     * 获取维度的唯一键
     */
    private static String dimKey(ServerLevel level) {
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
     * 获取数据库文件路径
     */
    private static Path getDbPath(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve(DB_DIR).resolve(dimKey(level)).resolve(DB_NAME);
    }

    private static void ensureSqliteDriverLoaded() throws SQLException {
        if (SQLITE_DRIVER_LOADED) return;
        synchronized (RoadDatabaseManager.class) {
            if (SQLITE_DRIVER_LOADED) return;
            try {
                Class.forName("org.sqlite.JDBC");
                SQLITE_DRIVER_LOADED = true;
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite JDBC driver not found. Dependency org.xerial:sqlite-jdbc may be missing.", e);
            }
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
            // Double-check
            conn = CONNECTIONS.get(key);
            if (conn != null && !conn.isClosed()) {
                return conn;
            }
            
            try {
                // 确保目录存在
                Path dbPath = getDbPath(level);
                Files.createDirectories(dbPath.getParent());

                ensureSqliteDriverLoaded();
                
                // 创建连接
                String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
                conn = DriverManager.getConnection(url);
                
                // 配置 SQLite 优化
                try (Statement stmt = conn.createStatement()) {
                    // WAL 模式：支持并发读写
                    stmt.execute("PRAGMA journal_mode=WAL");
                    // 同步模式：NORMAL 平衡性能和安全
                    stmt.execute("PRAGMA synchronous=NORMAL");
                    // 缓存大小：8MB
                    stmt.execute("PRAGMA cache_size=-8000");
                    // 临时表存储在内存
                    stmt.execute("PRAGMA temp_store=MEMORY");
                    // 启用外键约束
                    stmt.execute("PRAGMA foreign_keys=ON");
                }
                
                // 初始化表结构
                initTables(conn);
                
                // 先放入连接池，避免迁移过程中递归调用 getConnection 时重复创建
                CONNECTIONS.put(key, conn);
                LOGGER.debug("RoadDatabaseManager: 已创建维度 {} 的数据库连接", dimKey(level));
                
                // 检查并执行旧数据迁移（从分片 NBT 到 SQLite）
                // 注意：必须在连接放入 CONNECTIONS 之后调用，因为迁移过程会调用 addRoad
                try {
                    int migrated = LegacyShardMigration.migrateIfNeeded(level);
                    if (migrated > 0) {
                        LOGGER.info("RoadDatabaseManager: 维度 {} 已迁移 {} 条旧道路数据", dimKey(level), migrated);
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
            // 使用 fingerprint 作为唯一标识，避免重复插入
            // min_x, min_z, max_x, max_z 用于空间查询
            stmt.execute("""
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
            """);
            
            // 空间索引：用于矩形范围查询
            // SQLite 的 R-tree 索引非常适合空间查询
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_roads_spatial 
                ON roads (min_x, max_x, min_z, max_z)
            """);
            
            // fingerprint 索引：用于去重检查
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_roads_fingerprint 
                ON roads (fingerprint)
            """);
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
     * 关闭所有数据库连接（服务器停止时调用）
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
     * 执行检查点（将 WAL 日志合并到主数据库）
     * 在服务器停止或维度卸载时调用
     */
    public static void checkpoint(ServerLevel level) {
        String key = worldKey(level);
        Connection conn = CONNECTIONS.get(key);
        if (conn != null) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                LOGGER.debug("RoadDatabaseManager: 维度 {} WAL 检查点完成", dimKey(level));
            } catch (SQLException e) {
                LOGGER.warn("RoadDatabaseManager: WAL 检查点失败", e);
            }
        }
    }
    
    /**
     * 执行所有连接的检查点
     */
    public static void checkpointAll() {
        for (var entry : CONNECTIONS.entrySet()) {
            try (Statement stmt = entry.getValue().createStatement()) {
                stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            } catch (SQLException e) {
                LOGGER.warn("RoadDatabaseManager: WAL 检查点失败: {}", entry.getKey(), e);
            }
        }
    }
}
