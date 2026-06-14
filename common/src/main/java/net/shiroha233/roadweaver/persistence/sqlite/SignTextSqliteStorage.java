package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 路牌文本延迟写入存储
 */
public final class SignTextSqliteStorage {
    private SignTextSqliteStorage() {}

    public static final int TYPE_DISTANCE = 0;
    public static final int TYPE_SEA_QUESTION = 1;

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private static final String SQL_UPSERT =
            "MERGE INTO pending_sign_texts (chunk_x, chunk_z, x, y, z, sign_type, payload, updated_at) "
            + "KEY (x, y, z) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_QUERY_BY_CHUNK =
            "SELECT id, x, y, z, sign_type, payload "
            + "FROM pending_sign_texts WHERE chunk_x = ? AND chunk_z = ? ORDER BY id LIMIT ?";
    private static final String SQL_DELETE_BY_ID = "DELETE FROM pending_sign_texts WHERE id = ?";

    public record PendingSignText(long id, BlockPos pos, int signType, String payload) {}

    public record PendingSignWrite(BlockPos pos, int signType, String payload) {}

    public static void upsert(ServerLevel level, BlockPos pos, int signType, String payload) {
        if (pos == null) return;
        upsertBatch(level, List.of(new PendingSignWrite(pos, signType, payload)));
    }

    public static void upsertBatch(ServerLevel level, Collection<PendingSignWrite> writes) {
        if (level == null || writes == null || writes.isEmpty()) return;
        RoadDatabaseManager.writeExecutor(RoadDatabaseManager.DB_ROAD).execute(() -> {
            try {
                Connection conn = RoadDatabaseManager.getWriteConnection(level, RoadDatabaseManager.DB_ROAD);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_UPSERT)) {
                    boolean hasBatch = false;
                    for (PendingSignWrite write : writes) {
                        if (write == null || write.pos() == null) continue;
                        bindUpsert(stmt, write.pos(), write.signType(), write.payload());
                        stmt.addBatch();
                        hasBatch = true;
                    }
                    if (hasBatch) {
                        stmt.executeBatch();
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("upsert pending_sign_texts failed", e);
            }
        });
    }

    public static List<PendingSignText> queryByChunk(ServerLevel level, int chunkX, int chunkZ, int limit) {
        if (level == null || limit <= 0) return List.of();
        ArrayList<PendingSignText> out = new ArrayList<>();
        try {
            Connection conn = RoadDatabaseManager.getConnection(level, RoadDatabaseManager.DB_ROAD);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_QUERY_BY_CHUNK)) {
                stmt.setInt(1, chunkX);
                stmt.setInt(2, chunkZ);
                stmt.setInt(3, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        int x = rs.getInt("x");
                        int y = rs.getInt("y");
                        int z = rs.getInt("z");
                        int signType = rs.getInt("sign_type");
                        String payload = rs.getString("payload");
                        out.add(new PendingSignText(id, new BlockPos(x, y, z), signType, payload));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("queryByChunk pending_sign_texts failed", e);
        }
        return out;
    }

    public static void deleteByIds(ServerLevel level, List<Long> ids) {
        if (level == null || ids == null || ids.isEmpty()) return;
        RoadDatabaseManager.writeExecutor(RoadDatabaseManager.DB_ROAD).execute(() -> {
            try {
                Connection conn = RoadDatabaseManager.getWriteConnection(level, RoadDatabaseManager.DB_ROAD);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_BY_ID)) {
                    for (Long id : ids) {
                        if (id == null) continue;
                        stmt.setLong(1, id);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            } catch (SQLException e) {
                LOGGER.error("deleteByIds pending_sign_texts failed", e);
            }
        });
    }

    private static void bindUpsert(PreparedStatement stmt, BlockPos pos, int signType, String payload) throws SQLException {
        String safePayload = payload == null ? "" : payload;
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        long now = System.currentTimeMillis() / 1000L;
        stmt.setInt(1, chunkX);
        stmt.setInt(2, chunkZ);
        stmt.setInt(3, pos.getX());
        stmt.setInt(4, pos.getY());
        stmt.setInt(5, pos.getZ());
        stmt.setInt(6, signType);
        stmt.setString(7, safePayload);
        stmt.setLong(8, now);
    }
}
