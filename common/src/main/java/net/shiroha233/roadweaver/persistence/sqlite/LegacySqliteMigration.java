package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQLite 到 H2 数据迁移服务
 * 
 * 使用纯 JDBC 方式读取 SQLite 文件（如果 SQLite 驱动可用），
 * 或者使用 H2 的 CSV 导入功能作为备用方案。
 * 迁移完成后重命名旧文件，避免重复迁移。
 */
public final class LegacySqliteMigration {
    private LegacySqliteMigration() {}
    
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    
    // 已迁移的维度（避免同一会话重复检查）
    private static final Set<String> MIGRATED_DIMS = ConcurrentHashMap.newKeySet();
    
    // 旧文件重命名后缀
    private static final String MIGRATED_SUFFIX = ".migrated_to_h2";
    
    /**
     * 检查并执行迁移（如果需要）
     * 
     * @param level 服务器世界
     * @return 迁移的道路数量（0 表示无需迁移或已迁移）
     */
    public static int migrateIfNeeded(ServerLevel level) {
        String cacheKey = RoadDatabaseManager.dimKey(level);
        
        if (MIGRATED_DIMS.contains(cacheKey)) {
            return 0;
        }
        
        Path sqlitePath = RoadDatabaseManager.getLegacySqliteDbPath(level);
        
        // 检查旧 SQLite 文件是否存在
        if (!Files.exists(sqlitePath)) {
            MIGRATED_DIMS.add(cacheKey);
            return 0;
        }
        
        // 检查是否已经迁移过（文件已重命名）
        Path migratedPath = Path.of(sqlitePath.toString() + MIGRATED_SUFFIX);
        if (Files.exists(migratedPath)) {
            MIGRATED_DIMS.add(cacheKey);
            return 0;
        }
        
        LOGGER.info("LegacySqliteMigration: 发现旧 SQLite 数据库，开始迁移 - 维度: {}", level.dimension().location());
        notifyPlayers(level, Component.translatable("message.roadweaver.sqlite_migration.start"));
        
        // 尝试迁移
        int migrated = performMigration(level, sqlitePath);
        
        if (migrated >= 0) {
            // 迁移成功，重命名旧文件
            try {
                Files.move(sqlitePath, migratedPath);
                LOGGER.info("LegacySqliteMigration: 旧 SQLite 文件已重命名为: {}", migratedPath.getFileName());
            } catch (Exception e) {
                LOGGER.warn("LegacySqliteMigration: 无法重命名旧文件", e);
            }
            
            notifyPlayers(level, Component.translatable("message.roadweaver.sqlite_migration.done", migrated));
        }
        
        MIGRATED_DIMS.add(cacheKey);
        return migrated;
    }
    
    /**
     * 执行实际的数据迁移
     * 使用 SQLJet 纯 Java 读取器，无需 native 库
     */
    private static int performMigration(ServerLevel level, Path sqlitePath) {
        // 直接使用 SQLJet 纯 Java 方案，避免动态类加载
        return migrateUsingSqlJet(level, sqlitePath);
    }
    
    /**
     * 使用 SQLJet 纯 Java 读取器进行迁移
     */
    private static int migrateUsingSqlJet(ServerLevel level, Path sqlitePath) {
        LOGGER.info("LegacySqliteMigration: 使用 SQLJet 纯 Java 读取器进行迁移");
        LOGGER.info("LegacySqliteMigration: SQLite 文件路径: {}", sqlitePath.toAbsolutePath());
        
        int totalMigrated = 0;
        File sqliteFile = sqlitePath.toFile();
        
        if (!sqliteFile.exists()) {
            LOGGER.error("LegacySqliteMigration: SQLite 文件不存在: {}", sqliteFile.getAbsolutePath());
            return 0;
        }
        
        LOGGER.info("LegacySqliteMigration: SQLite 文件大小: {} bytes", sqliteFile.length());
        
        // 检查是否存在 WAL 文件或文件头标记为 WAL 模式（SQLJet 不支持 WAL 模式）
        File walFile = new File(sqliteFile.getAbsolutePath() + "-wal");
        File shmFile = new File(sqliteFile.getAbsolutePath() + "-shm");
        boolean hasWalFiles = walFile.exists() || shmFile.exists();
        boolean isWalMode = checkIfWalMode(sqliteFile);
        
        if (hasWalFiles || isWalMode) {
            if (hasWalFiles) {
                LOGGER.warn("LegacySqliteMigration: 检测到 SQLite WAL 模式文件");
                LOGGER.warn("LegacySqliteMigration: WAL 文件: {} (存在: {})", walFile.getName(), walFile.exists());
                LOGGER.warn("LegacySqliteMigration: SHM 文件: {} (存在: {})", shmFile.getName(), shmFile.exists());
            }
            if (isWalMode) {
                LOGGER.warn("LegacySqliteMigration: 文件头标记为 WAL 模式");
            }
            
            // 尝试修复 WAL 模式
            if (!tryFixWalMode(sqlitePath)) {
                LOGGER.error("LegacySqliteMigration: 无法自动修复 WAL 模式数据库");
                notifyPlayers(level, Component.translatable("message.roadweaver.sqlite_migration.wal_mode"));
                return -1; // 返回 -1 表示需要手动处理，不重命名文件
            }
        }
        
        try {
            // 以只读模式打开 SQLite 文件
            LOGGER.info("LegacySqliteMigration: 正在打开 SQLite 数据库...");
            SqlJetDb sqliteDb = SqlJetDb.open(sqliteFile, false);
            LOGGER.info("LegacySqliteMigration: SQLite 数据库已打开");
            
            try {
                // 列出所有表
                Set<String> tableNames = sqliteDb.getSchema().getTableNames();
                LOGGER.info("LegacySqliteMigration: 发现表: {}", tableNames);
                
                Connection h2Conn = RoadDatabaseManager.getConnection(level);
                
                // 迁移 roads 表
                if (tableNames.contains("roads")) {
                    totalMigrated = migrateRoadsTableUsingSqlJet(sqliteDb, h2Conn);
                } else {
                    LOGGER.warn("LegacySqliteMigration: roads 表不存在");
                }
                
                // 迁移 structures 表（如果存在）
                if (tableNames.contains("structures")) {
                    migrateStructuresTableUsingSqlJet(sqliteDb, h2Conn);
                }
                
                // 迁移 structure_scan_tiles 表（如果存在）
                if (tableNames.contains("structure_scan_tiles")) {
                    migrateScanTilesTableUsingSqlJet(sqliteDb, h2Conn);
                }
                
                // 迁移 structure_cache_meta 表（如果存在）
                if (tableNames.contains("structure_cache_meta")) {
                    migrateCacheMetaTableUsingSqlJet(sqliteDb, h2Conn);
                }
                
                LOGGER.info("LegacySqliteMigration: SQLJet 迁移完成 - 维度: {}, 道路数: {}", 
                    level.dimension().location(), totalMigrated);
                    
            } finally {
                sqliteDb.close();
            }
            
        } catch (SqlJetException e) {
            LOGGER.error("LegacySqliteMigration: SQLJet 读取失败: {}", e.getMessage(), e);
            notifyPlayers(level, Component.translatable("message.roadweaver.sqlite_migration.failed"));
            return 0;
        } catch (SQLException e) {
            LOGGER.error("LegacySqliteMigration: H2 写入失败: {}", e.getMessage(), e);
            return 0;
        } catch (Exception e) {
            LOGGER.error("LegacySqliteMigration: 未知错误: {}", e.getMessage(), e);
            return 0;
        }
        
        return totalMigrated;
    }
    
    /**
     * 检查 SQLite 文件头是否标记为 WAL 模式
     */
    private static boolean checkIfWalMode(File sqliteFile) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(sqliteFile, "r")) {
            raf.seek(18);
            int writeVersion = raf.read();
            int readVersion = raf.read();
            // WAL 模式的版本号是 2
            return writeVersion == 2 || readVersion == 2;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 尝试修复 WAL 模式数据库，使其可以被 SQLJet 读取
     * 通过直接修改文件头将 WAL 模式改为传统模式
     */
    private static boolean tryFixWalMode(Path sqlitePath) {
        File sqliteFile = sqlitePath.toFile();
        File walFile = new File(sqliteFile.getAbsolutePath() + "-wal");
        File shmFile = new File(sqliteFile.getAbsolutePath() + "-shm");
        
        LOGGER.warn("LegacySqliteMigration: 尝试手动修复 WAL 模式数据库...");
        
        // 删除 WAL 和 SHM 文件
        if (walFile.exists() && !walFile.delete()) {
            LOGGER.error("LegacySqliteMigration: 无法删除 WAL 文件");
            return false;
        }
        if (shmFile.exists() && !shmFile.delete()) {
            LOGGER.error("LegacySqliteMigration: 无法删除 SHM 文件");
            return false;
        }
        
        // 修改 SQLite 文件头，将 WAL 模式标记改为传统模式
        // 字节 18-19 是 write/read version，WAL 模式是 2，传统模式是 1
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(sqliteFile, "rw")) {
            // 读取当前版本
            raf.seek(18);
            int writeVersion = raf.read();
            int readVersion = raf.read();
            
            LOGGER.info("LegacySqliteMigration: 当前文件版本 - write: {}, read: {}", writeVersion, readVersion);
            
            if (writeVersion == 2 || readVersion == 2) {
                // 修改为传统模式 (version 1)
                raf.seek(18);
                raf.write(1); // write version
                raf.write(1); // read version
                LOGGER.info("LegacySqliteMigration: 已将文件版本修改为传统模式 (1, 1)");
            }
            
            return true;
        } catch (Exception e) {
            LOGGER.error("LegacySqliteMigration: 修改文件头失败: {}", e.getMessage());
            return false;
        }
    }
    
        
    /**
     * 使用 SQLJet 迁移 roads 表
     */
    private static int migrateRoadsTableUsingSqlJet(SqlJetDb sqliteDb, Connection h2Conn) throws SqlJetException, SQLException {
        int count = 0;
        
        ISqlJetTable table = sqliteDb.getTable("roads");
        
        String insertSql = "MERGE INTO roads (fingerprint, width, road_type, min_x, min_z, max_x, max_z, data) " +
                           "KEY (fingerprint) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = h2Conn.prepareStatement(insertSql)) {
            sqliteDb.beginTransaction(org.tmatesoft.sqljet.core.SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetCursor cursor = table.open();
                try {
                    while (!cursor.eof()) {
                        try {
                            long fingerprint = cursor.getInteger("fingerprint");
                            int width = (int) cursor.getInteger("width");
                            int roadType = (int) cursor.getInteger("road_type");
                            int minX = (int) cursor.getInteger("min_x");
                            int minZ = (int) cursor.getInteger("min_z");
                            int maxX = (int) cursor.getInteger("max_x");
                            int maxZ = (int) cursor.getInteger("max_z");
                            byte[] data = cursor.getBlobAsArray("data");
                            
                            pstmt.setLong(1, fingerprint);
                            pstmt.setInt(2, width);
                            pstmt.setInt(3, roadType);
                            pstmt.setInt(4, minX);
                            pstmt.setInt(5, minZ);
                            pstmt.setInt(6, maxX);
                            pstmt.setInt(7, maxZ);
                            pstmt.setBytes(8, data);
                            pstmt.addBatch();
                            count++;
                            
                            if (count % 1000 == 0) {
                                pstmt.executeBatch();
                                LOGGER.info("LegacySqliteMigration: 已迁移 roads 表 {} 条记录...", count);
                            }
                        } catch (SqlJetException e) {
                            LOGGER.warn("LegacySqliteMigration: 跳过无效的 roads 记录", e);
                        }
                        cursor.next();
                    }
                } finally {
                    cursor.close();
                }
            } finally {
                sqliteDb.commit();
            }
            pstmt.executeBatch();
        }
        
        LOGGER.info("LegacySqliteMigration: roads 表迁移完成，共 {} 条记录", count);
        return count;
    }
    
    /**
     * 使用 SQLJet 迁移 structures 表
     */
    private static void migrateStructuresTableUsingSqlJet(SqlJetDb sqliteDb, Connection h2Conn) throws SqlJetException, SQLException {
        int count = 0;
        
        ISqlJetTable table = sqliteDb.getTable("structures");
        
        String insertSql = "MERGE INTO structures (x, z, structure_id, source) " +
                           "KEY (x, z, structure_id, source) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = h2Conn.prepareStatement(insertSql)) {
            sqliteDb.beginTransaction(org.tmatesoft.sqljet.core.SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetCursor cursor = table.open();
                try {
                    while (!cursor.eof()) {
                        try {
                            int x = (int) cursor.getInteger("x");
                            int z = (int) cursor.getInteger("z");
                            String structureId = cursor.getString("structure_id");
                            int source = (int) cursor.getInteger("source");
                            
                            pstmt.setInt(1, x);
                            pstmt.setInt(2, z);
                            pstmt.setString(3, structureId);
                            pstmt.setInt(4, source);
                            pstmt.addBatch();
                            count++;
                            
                            if (count % 1000 == 0) {
                                pstmt.executeBatch();
                            }
                        } catch (SqlJetException e) {
                            LOGGER.warn("LegacySqliteMigration: 跳过无效的 structures 记录", e);
                        }
                        cursor.next();
                    }
                } finally {
                    cursor.close();
                }
            } finally {
                sqliteDb.commit();
            }
            pstmt.executeBatch();
        }
        
        LOGGER.info("LegacySqliteMigration: structures 表迁移完成，共 {} 条记录", count);
    }
    
    /**
     * 使用 SQLJet 迁移 structure_scan_tiles 表
     */
    private static void migrateScanTilesTableUsingSqlJet(SqlJetDb sqliteDb, Connection h2Conn) throws SqlJetException, SQLException {
        int count = 0;
        
        ISqlJetTable table = sqliteDb.getTable("structure_scan_tiles");
        
        String insertSql = "MERGE INTO structure_scan_tiles (tile_x, tile_z, tile_size_chunks, scanned_at) " +
                           "KEY (tile_x, tile_z, tile_size_chunks) VALUES (?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = h2Conn.prepareStatement(insertSql)) {
            sqliteDb.beginTransaction(org.tmatesoft.sqljet.core.SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetCursor cursor = table.open();
                try {
                    while (!cursor.eof()) {
                        try {
                            int tileX = (int) cursor.getInteger("tile_x");
                            int tileZ = (int) cursor.getInteger("tile_z");
                            int tileSizeChunks = (int) cursor.getInteger("tile_size_chunks");
                            long scannedAt = cursor.getInteger("scanned_at");
                            
                            pstmt.setInt(1, tileX);
                            pstmt.setInt(2, tileZ);
                            pstmt.setInt(3, tileSizeChunks);
                            pstmt.setLong(4, scannedAt);
                            pstmt.addBatch();
                            count++;
                            
                            if (count % 1000 == 0) {
                                pstmt.executeBatch();
                            }
                        } catch (SqlJetException e) {
                            LOGGER.warn("LegacySqliteMigration: 跳过无效的 structure_scan_tiles 记录", e);
                        }
                        cursor.next();
                    }
                } finally {
                    cursor.close();
                }
            } finally {
                sqliteDb.commit();
            }
            pstmt.executeBatch();
        }
        
        LOGGER.info("LegacySqliteMigration: structure_scan_tiles 表迁移完成，共 {} 条记录", count);
    }
    
    /**
     * 使用 SQLJet 迁移 structure_cache_meta 表
     */
    private static void migrateCacheMetaTableUsingSqlJet(SqlJetDb sqliteDb, Connection h2Conn) throws SqlJetException, SQLException {
        int count = 0;
        
        ISqlJetTable table = sqliteDb.getTable("structure_cache_meta");
        
        String insertSql = "MERGE INTO structure_cache_meta (k, v) KEY (k) VALUES (?, ?)";
        
        try (PreparedStatement pstmt = h2Conn.prepareStatement(insertSql)) {
            sqliteDb.beginTransaction(org.tmatesoft.sqljet.core.SqlJetTransactionMode.READ_ONLY);
            try {
                ISqlJetCursor cursor = table.open();
                try {
                    while (!cursor.eof()) {
                        try {
                            String k = cursor.getString("k");
                            String v = cursor.getString("v");
                            
                            pstmt.setString(1, k);
                            pstmt.setString(2, v);
                            pstmt.addBatch();
                            count++;
                        } catch (SqlJetException e) {
                            LOGGER.warn("LegacySqliteMigration: 跳过无效的 structure_cache_meta 记录", e);
                        }
                        cursor.next();
                    }
                } finally {
                    cursor.close();
                }
            } finally {
                sqliteDb.commit();
            }
            pstmt.executeBatch();
        }
        
        LOGGER.info("LegacySqliteMigration: structure_cache_meta 表迁移完成，共 {} 条记录", count);
    }
    
    /**
     * 通知玩家
     */
    private static void notifyPlayers(ServerLevel level, Component message) {
        if (level == null || level.getServer() == null) return;
        level.getServer().execute(() -> {
            try {
                level.getServer().getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(message));
            } catch (Exception ignored) {
            }
        });
    }
    
    /**
     * 重置迁移状态（服务器停止时调用）
     */
    public static void reset() {
        MIGRATED_DIMS.clear();
    }
}
