package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainTile;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainTileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * 持久化粗采样地形瓦片存储。
 */
public final class CoarseTerrainTileSqliteStorage {
    private CoarseTerrainTileSqliteStorage() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final Object DB_LOCK = new Object();
    private static final int PAYLOAD_MAGIC = 0x52575431;

    private static final String SQL_LOAD =
            "SELECT sample_width, sample_height, sea_level, data FROM terrain_tiles "
                    + "WHERE tile_x = ? AND tile_z = ? AND tile_size_chunks = ? AND step = ? AND schema_version = ?";

    private static final String SQL_SAVE =
            "MERGE INTO terrain_tiles (tile_x, tile_z, tile_size_chunks, step, schema_version, sample_width, sample_height, sea_level, data, updated_at) "
                    + "KEY (tile_x, tile_z, tile_size_chunks, step, schema_version) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, EXTRACT(EPOCH FROM CURRENT_TIMESTAMP))";

    private static final String SQL_DELETE_OLD_SCHEMA =
            "DELETE FROM terrain_tiles WHERE schema_version <> ?";

    private static final String SQL_PRUNE_OLD =
            "DELETE FROM terrain_tiles WHERE updated_at < ?";

    public static CoarseTerrainTile loadTile(ServerLevel level, CoarseTerrainTileKey key) {
        if (level == null || key == null) return null;
        try {
            LoadedPayload payload;
            synchronized (DB_LOCK) {
                Connection conn = RoadDatabaseManager.getConnection(level);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_LOAD)) {
                    bindKey(stmt, key);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) return null;
                        int sampleWidth = rs.getInt("sample_width");
                        int sampleHeight = rs.getInt("sample_height");
                        int seaLevel = rs.getInt("sea_level");
                        byte[] data = rs.getBytes("data");
                        if (data == null || data.length == 0) return null;
                        payload = new LoadedPayload(seaLevel, sampleWidth, sampleHeight, data);
                    }
                }
            }
            return decode(key, payload.seaLevel(), payload.sampleWidth(), payload.sampleHeight(), payload.data());
        } catch (SQLException e) {
            LOGGER.warn("读取粗采样地形瓦片失败 tile=[{},{}]", key.tileX(), key.tileZ(), e);
            return null;
        }
    }

    public static void saveTile(ServerLevel level, CoarseTerrainTile tile) {
        if (level == null || tile == null) return;
        CoarseTerrainTileKey key = tile.key();
        try {
            byte[] data = encode(tile);
            synchronized (DB_LOCK) {
                Connection conn = RoadDatabaseManager.getConnection(level);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_SAVE)) {
                    bindKey(stmt, key);
                    stmt.setInt(6, tile.sampleWidth());
                    stmt.setInt(7, tile.sampleHeight());
                    stmt.setInt(8, tile.seaLevel());
                    stmt.setBytes(9, data);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException | IOException e) {
            LOGGER.warn("写入粗采样地形瓦片失败 tile=[{},{}]", key.tileX(), key.tileZ(), e);
        }
    }

    public static void deleteBySchemaVersion(ServerLevel level, int currentSchemaVersion) {
        if (level == null) return;
        try {
            synchronized (DB_LOCK) {
                Connection conn = RoadDatabaseManager.getConnection(level);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_OLD_SCHEMA)) {
                    stmt.setInt(1, currentSchemaVersion);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("清理旧版粗采样地形瓦片失败", e);
        }
    }

    public static void pruneOldTiles(ServerLevel level, long olderThanEpochSeconds) {
        if (level == null) return;
        try {
            synchronized (DB_LOCK) {
                Connection conn = RoadDatabaseManager.getConnection(level);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_PRUNE_OLD)) {
                    stmt.setLong(1, olderThanEpochSeconds);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("清理过期粗采样地形瓦片失败", e);
        }
    }

    private record LoadedPayload(int seaLevel, int sampleWidth, int sampleHeight, byte[] data) {}

    private static void bindKey(PreparedStatement stmt, CoarseTerrainTileKey key) throws SQLException {
        stmt.setInt(1, key.tileX());
        stmt.setInt(2, key.tileZ());
        stmt.setInt(3, key.tileSizeChunks());
        stmt.setInt(4, key.step());
        stmt.setInt(5, key.schemaVersion());
    }

    private static byte[] encode(CoarseTerrainTile tile) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(tile.sampleCount() * 9 + 32);
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(raw))) {
            out.writeInt(PAYLOAD_MAGIC);
            out.writeInt(tile.sampleWidth());
            out.writeInt(tile.sampleHeight());
            for (short value : tile.heights()) out.writeShort(value);
            for (short value : tile.oceanFloors()) out.writeShort(value);
            out.write(tile.flags());
            for (int value : tile.terrainArgb()) out.writeInt(value);
        }
        return raw.toByteArray();
    }

    private static CoarseTerrainTile decode(CoarseTerrainTileKey key,
                                            int seaLevel,
                                            int sampleWidth,
                                            int sampleHeight,
                                            byte[] data) {
        int expected = Math.multiplyExact(sampleWidth, sampleHeight);
        short[] heights = new short[expected];
        short[] oceanFloors = new short[expected];
        byte[] flags = new byte[expected];
        int[] terrainArgb = new int[expected];

        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            int magic = in.readInt();
            if (magic != PAYLOAD_MAGIC) return null;
            int storedWidth = in.readInt();
            int storedHeight = in.readInt();
            if (storedWidth != sampleWidth || storedHeight != sampleHeight) return null;
            for (int i = 0; i < expected; i++) heights[i] = in.readShort();
            for (int i = 0; i < expected; i++) oceanFloors[i] = in.readShort();
            in.readFully(flags);
            for (int i = 0; i < expected; i++) terrainArgb[i] = in.readInt();
            return new CoarseTerrainTile(key, seaLevel, sampleWidth, sampleHeight, heights, oceanFloors, flags, terrainArgb);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("解码粗采样地形瓦片失败 tile=[{},{}]", key.tileX(), key.tileZ(), e);
            return null;
        }
    }
}