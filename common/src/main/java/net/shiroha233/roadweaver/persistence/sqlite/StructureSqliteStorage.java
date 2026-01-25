package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.helpers.Records;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构点 H2 缓存（DAO/Storage）。
 *
 * 职责：
 * - 结构点的持久化写入/查询
 * - 扫描 tile 标记（避免重复预测/验证）
 * - 缓存策略 hash 管理（配置变化时自动失效预测缓存）
 */
public final class StructureSqliteStorage {
    private StructureSqliteStorage() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    // 结构点来源：用于区分"预测缓存"与"手动/业务注册"
    public static final int SOURCE_PREDICTED = 0;
    public static final int SOURCE_MANUAL = 1;

    // 扫描 tile 的固定大小（chunk）
    public static final int SCAN_TILE_SIZE_CHUNKS = 128;

    // 扫描占用超时（秒）
    private static final int SCAN_CLAIM_TIMEOUT_SECONDS = 10 * 60;

    private static final String META_POLICY_HASH = "policy_hash";

    // H2 兼容的 SQL 语句
    private static final String SQL_INSERT_STRUCTURE =
            "MERGE INTO structures (x, z, structure_id, source) KEY (x, z, structure_id, source) VALUES (?, ?, ?, ?)";

    private static final String SQL_DELETE_POS_SOURCE =
            "DELETE FROM structures WHERE x = ? AND z = ? AND source = ?";

    private static final String SQL_QUERY_RECT =
            "SELECT x, z, structure_id FROM structures " +
            "WHERE x >= ? AND x <= ? AND z >= ? AND z <= ? AND source IN (%s)";

    private static final String SQL_CLAIM_SCAN_TILE =
            "MERGE INTO structure_scan_tiles (tile_x, tile_z, tile_size_chunks, scanned_at) " +
            "KEY (tile_x, tile_z, tile_size_chunks) VALUES (?, ?, ?, ?)";

    private static final String SQL_GET_SCAN_TILE =
            "SELECT scanned_at FROM structure_scan_tiles WHERE tile_x = ? AND tile_z = ? AND tile_size_chunks = ?";

    private static final String SQL_STEAL_SCAN_TILE =
            "UPDATE structure_scan_tiles SET scanned_at = ? WHERE tile_x = ? AND tile_z = ? AND tile_size_chunks = ? AND scanned_at = ?";

    private static final String SQL_MARK_SCAN_TILE_DONE =
            "UPDATE structure_scan_tiles SET scanned_at = EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) WHERE tile_x = ? AND tile_z = ? AND tile_size_chunks = ?";

    private static final String SQL_DELETE_SCAN_TILE =
            "DELETE FROM structure_scan_tiles WHERE tile_x = ? AND tile_z = ? AND tile_size_chunks = ?";

    private static final String SQL_GET_META =
            "SELECT v FROM structure_cache_meta WHERE k = ?";

    private static final String SQL_SET_META =
            "MERGE INTO structure_cache_meta (k, v) KEY (k) VALUES (?, ?)";

    private static final String SQL_CLEAR_PREDICTED =
            "DELETE FROM structures WHERE source = " + SOURCE_PREDICTED;

    private static final String SQL_CLEAR_SCAN_TILES =
            "DELETE FROM structure_scan_tiles";


    private static String getMetaValue(ServerLevel level, String key) throws SQLException {
        Connection conn = RoadDatabaseManager.getConnection(level);
        try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_META)) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void setMetaValue(ServerLevel level, String key, String value) throws SQLException {
        Connection conn = RoadDatabaseManager.getConnection(level);
        try (PreparedStatement stmt = conn.prepareStatement(SQL_SET_META)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        }
    }

    private static int[] normalizeSources(int[] sources) {
        if (sources == null || sources.length == 0) {
            return new int[]{SOURCE_PREDICTED, SOURCE_MANUAL};
        }
        return sources;
    }

    private static String sourcesPlaceholders(int[] sources) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sources.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.toString();
    }

    public static void ensurePolicy(ServerLevel level, String policyHash) {
        if (level == null) return;
        if (policyHash == null) policyHash = "";

        try {
            String current = getMetaValue(level, META_POLICY_HASH);

            if (current != null && current.equals(policyHash)) {
                return;
            }

            Connection conn = RoadDatabaseManager.getConnection(level);
            try (var stmt = conn.createStatement()) {
                stmt.execute(SQL_CLEAR_PREDICTED);
                stmt.execute(SQL_CLEAR_SCAN_TILES);
            }

            setMetaValue(level, META_POLICY_HASH, policyHash);

        } catch (SQLException e) {
            LOGGER.error("StructureSqliteStorage: ensurePolicy failed", e);
        }
    }

    public static boolean claimScanTile(ServerLevel level, int tileX, int tileZ) {
        if (level == null) return false;

        try {
            Connection conn = RoadDatabaseManager.getConnection(level);
            long now = System.currentTimeMillis() / 1000L;
            
            // 先检查是否已存在
            try (PreparedStatement q = conn.prepareStatement(SQL_GET_SCAN_TILE)) {
                q.setInt(1, tileX);
                q.setInt(2, tileZ);
                q.setInt(3, SCAN_TILE_SIZE_CHUNKS);
                try (ResultSet rs = q.executeQuery()) {
                    if (rs.next()) {
                        long scannedAt = rs.getLong(1);
                        
                        // 已完成
                        if (scannedAt > 0) return false;
                        
                        long start = scannedAt < 0 ? -scannedAt : now - SCAN_CLAIM_TIMEOUT_SECONDS - 1;
                        if (now - start < SCAN_CLAIM_TIMEOUT_SECONDS) {
                            return false; // 正在扫描且未超时
                        }
                        
                        // 超时抢占
                        try (PreparedStatement steal = conn.prepareStatement(SQL_STEAL_SCAN_TILE)) {
                            steal.setLong(1, -now);
                            steal.setInt(2, tileX);
                            steal.setInt(3, tileZ);
                            steal.setInt(4, SCAN_TILE_SIZE_CHUNKS);
                            steal.setLong(5, scannedAt);
                            return steal.executeUpdate() > 0;
                        }
                    }
                }
            }
            
            // 不存在，插入新记录
            try (PreparedStatement stmt = conn.prepareStatement(SQL_CLAIM_SCAN_TILE)) {
                stmt.setInt(1, tileX);
                stmt.setInt(2, tileZ);
                stmt.setInt(3, SCAN_TILE_SIZE_CHUNKS);
                stmt.setLong(4, -now);
                stmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            LOGGER.error("StructureSqliteStorage: claimScanTile failed", e);
            return false;
        }
    }

    public static void markScanTileDone(ServerLevel level, int tileX, int tileZ) {
        if (level == null) return;

        try {
            Connection conn = RoadDatabaseManager.getConnection(level);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_MARK_SCAN_TILE_DONE)) {
                stmt.setInt(1, tileX);
                stmt.setInt(2, tileZ);
                stmt.setInt(3, SCAN_TILE_SIZE_CHUNKS);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("StructureSqliteStorage: markScanTileDone failed", e);
        }
    }

    public static void releaseScanTile(ServerLevel level, int tileX, int tileZ) {
        if (level == null) return;

        try {
            Connection conn = RoadDatabaseManager.getConnection(level);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_SCAN_TILE)) {
                stmt.setInt(1, tileX);
                stmt.setInt(2, tileZ);
                stmt.setInt(3, SCAN_TILE_SIZE_CHUNKS);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("StructureSqliteStorage: releaseScanTile failed", e);
        }
    }

    public static void addStructures(ServerLevel level, List<Records.StructureInfo> infos, int source) {
        if (level == null || infos == null || infos.isEmpty()) return;

        try {
            Connection conn = RoadDatabaseManager.getConnection(level);
            try (PreparedStatement del = conn.prepareStatement(SQL_DELETE_POS_SOURCE);
                 PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_STRUCTURE)) {
                for (Records.StructureInfo info : infos) {
                    if (info == null || info.pos() == null) continue;
                    BlockPos p = info.pos();

                    int x = p.getX();
                    int z = p.getZ();
                    String id = info.structureId() == null ? "unknown" : info.structureId();

                    if (source != SOURCE_PREDICTED) {
                        del.setInt(1, x);
                        del.setInt(2, z);
                        del.setInt(3, source);
                        del.addBatch();
                    }

                    stmt.setInt(1, x);
                    stmt.setInt(2, z);
                    stmt.setString(3, id);
                    stmt.setInt(4, source);
                    stmt.addBatch();
                }

                if (source != SOURCE_PREDICTED) {
                    del.executeBatch();
                }
                stmt.executeBatch();
            }
        } catch (SQLException e) {
            LOGGER.error("StructureSqliteStorage: addStructures failed", e);
        }
    }

    public static List<Records.StructureInfo> queryRect(ServerLevel level,
                                                        int minBlockX, int minBlockZ,
                                                        int maxBlockX, int maxBlockZ,
                                                        int... sources) {
        if (level == null) return List.of();

        int[] src = normalizeSources(sources);
        String sql = String.format(SQL_QUERY_RECT, sourcesPlaceholders(src));

        try {
            Connection conn = RoadDatabaseManager.getConnection(level);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, minBlockX);
                stmt.setInt(2, maxBlockX);
                stmt.setInt(3, minBlockZ);
                stmt.setInt(4, maxBlockZ);
                for (int i = 0; i < src.length; i++) {
                    stmt.setInt(5 + i, src[i]);
                }

                ArrayList<Records.StructureInfo> out = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int x = rs.getInt("x");
                        int z = rs.getInt("z");
                        String id = rs.getString("structure_id");
                        out.add(new Records.StructureInfo(new BlockPos(x, 0, z), id));
                    }
                }
                return out;
            }
        } catch (SQLException e) {
            LOGGER.error("StructureSqliteStorage: queryRect failed", e);
            return List.of();
        }
    }

    public static boolean hasAnyStructure(ServerLevel level) {
        if (level == null) return false;
        try {
            Connection conn = RoadDatabaseManager.getConnection(level);
            try (var stmt = conn.createStatement(); var rs = stmt.executeQuery("SELECT 1 FROM structures LIMIT 1")) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.error("StructureSqliteStorage: hasAnyStructure failed", e);
            return false;
        }
    }
}
