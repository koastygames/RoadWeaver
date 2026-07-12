package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainTile;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainTileKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 旧 H2 数据导入器。
 *
 * 只负责把旧库数据读出来，供文件型存储做一次性转换。
 */
public final class LegacyH2Importer {
    private LegacyH2Importer() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final String SQL_ROADS = "SELECT data FROM roads";
    private static final String SQL_PENDING_SIGN_TEXTS = "SELECT x, y, z, sign_type, payload FROM pending_sign_texts ORDER BY id";
    private static final String SQL_TERRAIN_KEYS =
            "SELECT DISTINCT tile_x, tile_z, tile_size_chunks, step, schema_version FROM terrain_tiles ORDER BY tile_x, tile_z, tile_size_chunks, step, schema_version";
    private static final String SQL_TERRAIN_TILE =
            "SELECT sample_width, sample_height, sea_level, data FROM terrain_tiles WHERE tile_x = ? AND tile_z = ? AND tile_size_chunks = ? AND step = ? AND schema_version = ?";
    private static final String SQL_STRUCTURES = "SELECT x, z, structure_id, source FROM structures";
    private static final String SQL_SCAN_TILES = "SELECT tile_x, tile_z, scanned_at FROM structure_scan_tiles";
    private static final String SQL_META = "SELECT k, v FROM structure_cache_meta";

    public record LegacyPendingSignText(BlockPos pos, int signType, String payload) {
        public LegacyPendingSignText {
            pos = Objects.requireNonNull(pos, "pos");
            payload = payload == null ? "" : payload;
        }
    }

    public record LegacyStructureState(StructureLocationData structureLocations,
                                        List<StructureConnection> connections,
                                        Set<Long> plannedTileKeys,
                                        Map<Long, Long> plannedTileCenters,
                                        Map<Long, Integer> structureSources,
                                        Map<String, String> meta,
                                        Map<Long, Long> scanTiles) {
        public LegacyStructureState {
            structureLocations = structureLocations == null
                    ? new StructureLocationData(new ArrayList<>(), new ArrayList<>())
                    : structureLocations;
            connections = connections == null ? new ArrayList<>() : new ArrayList<>(connections);
            plannedTileKeys = plannedTileKeys == null ? new HashSet<>() : new HashSet<>(plannedTileKeys);
            plannedTileCenters = plannedTileCenters == null ? new HashMap<>() : new HashMap<>(plannedTileCenters);
            structureSources = structureSources == null ? new HashMap<>() : new HashMap<>(structureSources);
            meta = meta == null ? new HashMap<>() : new HashMap<>(meta);
            scanTiles = scanTiles == null ? new HashMap<>() : new HashMap<>(scanTiles);
        }

        public boolean hasContent() {
            return !structureLocations.structureLocations().isEmpty()
                    || !structureLocations.structureInfos().isEmpty()
                    || !connections.isEmpty()
                    || !plannedTileKeys.isEmpty()
                    || !plannedTileCenters.isEmpty()
                    || !structureSources.isEmpty()
                    || !meta.isEmpty()
                    || !scanTiles.isEmpty();
        }
    }

    public static List<RoadData> loadRoads(ServerLevel level) {
        if (level == null) return List.of();
        ArrayList<RoadData> roads = new ArrayList<>();
        try (Connection conn = RoadDatabaseManager.openReadOnlyConnection(level, RoadDatabaseManager.DB_ROAD);
             PreparedStatement stmt = conn.prepareStatement(SQL_ROADS);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                byte[] data = rs.getBytes("data");
                if (data == null || data.length == 0) continue;
                RoadData road = decodeRoadData(data);
                if (road != null) roads.add(road);
            }
        } catch (SQLException e) {
            if (isMissingTable(e)) return List.of();
            throw new LegacyImportException("读取旧版道路数据失败", e);
        }
        return roads;
    }

    public static List<LegacyPendingSignText> loadPendingSignTexts(ServerLevel level) {
        if (level == null) return List.of();
        ArrayList<LegacyPendingSignText> out = new ArrayList<>();
        try (Connection conn = RoadDatabaseManager.openReadOnlyConnection(level, RoadDatabaseManager.DB_ROAD);
             PreparedStatement stmt = conn.prepareStatement(SQL_PENDING_SIGN_TEXTS);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                int signType = rs.getInt("sign_type");
                String payload = rs.getString("payload");
                out.add(new LegacyPendingSignText(new BlockPos(x, y, z), signType, payload));
            }
        } catch (SQLException e) {
            if (isMissingTable(e)) return List.of();
            throw new LegacyImportException("读取旧版路牌文本失败", e);
        }
        return out;
    }

    public static List<CoarseTerrainTileKey> loadTerrainTileKeys(ServerLevel level) {
        if (level == null) return List.of();
        ArrayList<CoarseTerrainTileKey> keys = new ArrayList<>();
        try (Connection conn = RoadDatabaseManager.openReadOnlyConnection(level, RoadDatabaseManager.DB_TERRAIN);
             PreparedStatement stmt = conn.prepareStatement(SQL_TERRAIN_KEYS);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                int tileX = rs.getInt("tile_x");
                int tileZ = rs.getInt("tile_z");
                int tileSizeChunks = rs.getInt("tile_size_chunks");
                int step = rs.getInt("step");
                int schemaVersion = rs.getInt("schema_version");
                keys.add(new CoarseTerrainTileKey(level.dimension().location(), tileX, tileZ, tileSizeChunks, step, schemaVersion));
            }
        } catch (SQLException e) {
            if (isMissingTable(e)) return List.of();
            throw new LegacyImportException("读取旧版粗采样地形索引失败", e);
        }
        return keys;
    }

    public static CoarseTerrainTile loadTerrainTile(ServerLevel level, CoarseTerrainTileKey key) {
        if (level == null || key == null) return null;
        try (Connection conn = RoadDatabaseManager.openReadOnlyConnection(level, RoadDatabaseManager.DB_TERRAIN);
             PreparedStatement stmt = conn.prepareStatement(SQL_TERRAIN_TILE)) {
            bindTerrainKey(stmt, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                int sampleWidth = rs.getInt("sample_width");
                int sampleHeight = rs.getInt("sample_height");
                int seaLevel = rs.getInt("sea_level");
                byte[] data = rs.getBytes("data");
                if (data == null || data.length == 0) return null;
                return decodeTerrainTile(key, seaLevel, sampleWidth, sampleHeight, data);
            }
        } catch (SQLException e) {
            if (isMissingTable(e)) return null;
            throw new LegacyImportException("读取旧版粗采样地形失败", e);
        }
    }

    public static LegacyStructureState loadStructureState(ServerLevel level) {
        if (level == null) return emptyStructureState();
        LegacyStructureState state = new LegacyStructureState(new StructureLocationData(new ArrayList<>(), new ArrayList<>()),
                new ArrayList<>(), new HashSet<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        try (Connection conn = RoadDatabaseManager.openReadOnlyConnection(level, RoadDatabaseManager.DB_MAP)) {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_STRUCTURES);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int x = rs.getInt("x");
                    int z = rs.getInt("z");
                    String id = rs.getString("structure_id");
                    if (id == null || id.isEmpty()) id = "unknown";
                    int source = rs.getInt("source");
                    if (source != 0) {
                        state.structureLocations().structureInfos().removeIf(existing -> samePos(existing.pos(), x, z));
                    }
                    StructureInfo info = new StructureInfo(new BlockPos(x, 0, z), id);
                    state.structureLocations().structureInfos().add(info);
                    state.structureSources().put(posKey(info.pos()), source);
                    if (!state.structureLocations().structureLocations().contains(info.pos())) {
                        state.structureLocations().structureLocations().add(info.pos());
                    }
                }
            } catch (SQLException e) {
                if (!isMissingTable(e)) throw e;
            }

            try (PreparedStatement stmt = conn.prepareStatement(SQL_SCAN_TILES);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int tileX = rs.getInt("tile_x");
                    int tileZ = rs.getInt("tile_z");
                    long scannedAt = rs.getLong("scanned_at");
                    state.scanTiles().put(chunkKey(tileX, tileZ), scannedAt);
                }
            } catch (SQLException e) {
                if (!isMissingTable(e)) throw e;
            }

            try (PreparedStatement stmt = conn.prepareStatement(SQL_META);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String k = rs.getString("k");
                    String v = rs.getString("v");
                    if (k != null && v != null) state.meta().put(k, v);
                }
            } catch (SQLException e) {
                if (!isMissingTable(e)) throw e;
            }
        } catch (SQLException e) {
            throw new LegacyImportException("读取旧版结构缓存失败", e);
        }
        return state;
    }

    private static LegacyStructureState emptyStructureState() {
        return new LegacyStructureState(new StructureLocationData(new ArrayList<>(), new ArrayList<>()),
                new ArrayList<>(), new HashSet<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    private static boolean samePos(BlockPos pos, int x, int z) {
        return pos != null && pos.getX() == x && pos.getZ() == z;
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static long posKey(BlockPos pos) {
        return (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
    }

    private static boolean isMissingTable(SQLException e) {
        String state = e.getSQLState();
        if ("42S02".equals(state)) {
            return true;
        }
        String message = e.getMessage();
        return message != null && message.contains("Table") && message.contains("not found");
    }

    public static final class LegacyImportException extends RuntimeException {
        public LegacyImportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static void bindTerrainKey(PreparedStatement stmt, CoarseTerrainTileKey key) throws SQLException {
        stmt.setInt(1, key.tileX());
        stmt.setInt(2, key.tileZ());
        stmt.setInt(3, key.tileSizeChunks());
        stmt.setInt(4, key.step());
        stmt.setInt(5, key.schemaVersion());
    }

    private static RoadData decodeRoadData(byte[] data) {
        if (data == null || data.length == 0) return null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            CompoundTag compound = NbtIo.read(in);
            if (compound == null || !compound.contains("road")) return null;
            Tag tag = compound.get("road");
            return RoadData.CODEC.parse(new com.mojang.serialization.Dynamic<>(NbtOps.INSTANCE, tag)).result().orElse(null);
        } catch (Exception e) {
            LOGGER.warn("反序列化道路数据失败", e);
            return null;
        }
    }

    private static CoarseTerrainTile decodeTerrainTile(CoarseTerrainTileKey key,
                                                       int seaLevel,
                                                       int sampleWidth,
                                                       int sampleHeight,
                                                       byte[] data) {
        try (DataInputStream in = new DataInputStream(new java.util.zip.InflaterInputStream(new ByteArrayInputStream(data)))) {
            int magic = in.readInt();
            if (magic != 0x52575431) return null;
            int storedWidth = in.readInt();
            int storedHeight = in.readInt();
            if (storedWidth != sampleWidth || storedHeight != sampleHeight) return null;
            if (sampleWidth != key.sampleWidth() || sampleHeight != key.sampleHeight()) return null;
            int expected = Math.multiplyExact(sampleWidth, sampleHeight);
            if (expected <= 0) return null;
            short[] heights = new short[expected];
            short[] oceanFloors = new short[expected];
            byte[] flags = new byte[expected];
            int[] terrainArgb = new int[expected];
            for (int i = 0; i < expected; i++) heights[i] = in.readShort();
            for (int i = 0; i < expected; i++) oceanFloors[i] = in.readShort();
            in.readFully(flags);
            for (int i = 0; i < expected; i++) terrainArgb[i] = in.readInt();
            return new CoarseTerrainTile(key, seaLevel, sampleWidth, sampleHeight, heights, oceanFloors, flags, terrainArgb);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("反序列化旧版粗采样地形失败", e);
            return null;
        }
    }
}
