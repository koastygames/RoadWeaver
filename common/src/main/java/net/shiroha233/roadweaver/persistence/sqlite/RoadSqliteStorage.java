/* 文件职责：负责道路数据的序列化、落库、查询与索引失效。 */
package net.shiroha233.roadweaver.persistence.sqlite;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.persistence.RoadSpatialIndex;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 H2 的道路数据存储，所有同维度访问都通过连接锁串行化。
 */
public final class RoadSqliteStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final DynamicOps<Tag> OPS = NbtOps.INSTANCE;
    private static final int ROAD_CACHE_MAX = 4096;

    private static final String SQL_INSERT =
            "MERGE INTO roads (fingerprint, width, road_type, min_x, min_z, max_x, max_z, data) "
                    + "KEY (fingerprint) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SQL_QUERY_RECT =
            "SELECT fingerprint, data FROM roads WHERE max_x >= ? AND min_x <= ? AND max_z >= ? AND min_z <= ?";
    private static final String SQL_EXISTS = "SELECT 1 FROM roads WHERE fingerprint = ? LIMIT 1";
    private static final String SQL_DELETE = "DELETE FROM roads WHERE fingerprint = ?";

    private static final Map<CacheKey, RoadData> ROAD_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, RoadData> eldest) {
                    return size() > ROAD_CACHE_MAX;
                }
            });

    private RoadSqliteStorage() {
    }

    private record Bounds(int minX, int minZ, int maxX, int maxZ) {
    }

    private record CacheKey(Identifier dimensionId, long fingerprint) {
    }

    public static void addRoad(ServerLevel level, RoadData road) {
        if (level == null || road == null || road.roadSegmentList() == null || road.roadSegmentList().isEmpty()) {
            return;
        }

        Bounds bounds = computeBounds(road);
        long fingerprint = fingerprint(road);
        byte[] data = serializeRoadData(road);
        if (data == null) {
            LOGGER.warn("Failed to serialize road data before insert");
            return;
        }

        try {
            synchronized (RoadDatabaseManager.connectionMutex(level)) {
                Connection conn = RoadDatabaseManager.getConnection(level);
                try (PreparedStatement exists = conn.prepareStatement(SQL_EXISTS)) {
                    exists.setLong(1, fingerprint);
                    try (ResultSet rs = exists.executeQuery()) {
                        if (rs.next()) {
                            return;
                        }
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT)) {
                    bindInsert(stmt, fingerprint, road, bounds, data);
                    stmt.executeUpdate();
                }
            }
            invalidateCoveredChunks(level, bounds);
        } catch (SQLException e) {
            LOGGER.error("Failed to insert road data", e);
        }
    }

    public static List<RoadData> queryRect(ServerLevel level,
                                           int minBlockX,
                                           int minBlockZ,
                                           int maxBlockX,
                                           int maxBlockZ) {
        if (level == null) {
            return List.of();
        }

        ArrayList<RoadData> result = new ArrayList<>();
        try {
            synchronized (RoadDatabaseManager.connectionMutex(level)) {
                Connection conn = RoadDatabaseManager.getConnection(level);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_QUERY_RECT)) {
                    stmt.setInt(1, minBlockX);
                    stmt.setInt(2, maxBlockX);
                    stmt.setInt(3, minBlockZ);
                    stmt.setInt(4, maxBlockZ);

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            long fp = rs.getLong("fingerprint");
                            CacheKey key = new CacheKey(level.dimension().identifier(), fp);
                            RoadData cached = ROAD_CACHE.get(key);
                            if (cached != null) {
                                result.add(cached);
                                continue;
                            }

                            byte[] bytes = rs.getBytes("data");
                            if (bytes == null || bytes.length == 0) {
                                continue;
                            }
                            RoadData decoded = deserializeRoadData(bytes);
                            if (decoded != null) {
                                ROAD_CACHE.put(key, decoded);
                                result.add(decoded);
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to query road data", e);
        }
        return result;
    }

    public static long computeFingerprint(RoadData road) {
        return fingerprint(road);
    }

    public static void deleteRoad(ServerLevel level, long fingerprint) {
        if (level == null) {
            return;
        }
        try {
            synchronized (RoadDatabaseManager.connectionMutex(level)) {
                Connection conn = RoadDatabaseManager.getConnection(level);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE)) {
                    stmt.setLong(1, fingerprint);
                    stmt.executeUpdate();
                }
                ROAD_CACHE.remove(new CacheKey(level.dimension().identifier(), fingerprint));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to delete road data", e);
        }
    }

    public static void replaceRoad(ServerLevel level, long oldFingerprint, RoadData newRoad) {
        if (level == null || newRoad == null || newRoad.roadSegmentList() == null || newRoad.roadSegmentList().isEmpty()) {
            return;
        }
        deleteRoad(level, oldFingerprint);
        addRoadForce(level, newRoad);
    }

    public static void flushAll(ServerLevel level) {
        if (level != null) {
            RoadDatabaseManager.checkpoint(level);
        }
    }

    public static void clearAll(ServerLevel level) {
        if (level == null) {
            return;
        }
        try {
            synchronized (RoadDatabaseManager.connectionMutex(level)) {
                Connection conn = RoadDatabaseManager.getConnection(level);
                try (var stmt = conn.createStatement()) {
                    stmt.execute("DELETE FROM roads");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to clear road data", e);
        }
    }

    public static void shutdown() {
        RoadDatabaseManager.checkpointAll();
        RoadDatabaseManager.closeAll();
    }

    private static void addRoadForce(ServerLevel level, RoadData road) {
        Bounds bounds = computeBounds(road);
        long fingerprint = fingerprint(road);
        byte[] data = serializeRoadData(road);
        if (data == null) {
            return;
        }

        try {
            synchronized (RoadDatabaseManager.connectionMutex(level)) {
                Connection conn = RoadDatabaseManager.getConnection(level);
                try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT)) {
                    bindInsert(stmt, fingerprint, road, bounds, data);
                    stmt.executeUpdate();
                }
                ROAD_CACHE.put(new CacheKey(level.dimension().identifier(), fingerprint), road);
            }
            invalidateCoveredChunks(level, bounds);
        } catch (SQLException e) {
            LOGGER.error("Failed to force insert road data", e);
        }
    }

    private static void bindInsert(PreparedStatement stmt,
                                   long fingerprint,
                                   RoadData road,
                                   Bounds bounds,
                                   byte[] data) throws SQLException {
        stmt.setLong(1, fingerprint);
        stmt.setInt(2, road.width());
        stmt.setInt(3, road.roadType());
        stmt.setInt(4, bounds.minX());
        stmt.setInt(5, bounds.minZ());
        stmt.setInt(6, bounds.maxX());
        stmt.setInt(7, bounds.maxZ());
        stmt.setBytes(8, data);
    }

    private static Bounds computeBounds(RoadData road) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (RoadSegmentPlacement segment : road.roadSegmentList()) {
            BlockPos pos = segment.middlePos();
            minX = Math.min(minX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    private static void invalidateCoveredChunks(ServerLevel level, Bounds bounds) {
        int minChunkX = bounds.minX() >> 4;
        int minChunkZ = bounds.minZ() >> 4;
        int maxChunkX = bounds.maxX() >> 4;
        int maxChunkZ = bounds.maxZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                RoadSpatialIndex.invalidateChunk(level, chunkX, chunkZ);
            }
        }
    }

    private static long fingerprint(RoadData road) {
        if (road == null || road.roadSegmentList() == null || road.roadSegmentList().isEmpty()) {
            return 0L;
        }
        BlockPos start = road.roadSegmentList().get(0).middlePos();
        BlockPos end = road.roadSegmentList().get(road.roadSegmentList().size() - 1).middlePos();
        long keyA = (((long) start.getX()) << 32) ^ (start.getZ() & 0xffffffffL);
        long keyB = (((long) end.getX()) << 32) ^ (end.getZ() & 0xffffffffL);
        long low = Math.min(keyA, keyB);
        long high = Math.max(keyA, keyB);
        long result = (high << 1) ^ low;
        result ^= ((long) road.width() & 0xffffffffL);
        result ^= ((long) road.roadType() & 0xffffffffL) << 33;
        return result;
    }

    private static byte[] serializeRoadData(RoadData road) {
        try {
            DataResult<Tag> encoded = RoadData.CODEC.encodeStart(OPS, road);
            Tag tag = encoded.result().orElse(null);
            if (tag == null) {
                return null;
            }

            CompoundTag root = new CompoundTag();
            root.put("road", tag);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.write(root, new DataOutputStream(output));
            return output.toByteArray();
        } catch (Exception e) {
            LOGGER.error("Failed to serialize road data", e);
            return null;
        }
    }

    private static RoadData deserializeRoadData(byte[] bytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             DataInputStream dataInput = new DataInputStream(input)) {
            CompoundTag root = NbtIo.read(dataInput);
            if (root == null || !root.contains("road")) {
                return null;
            }
            Tag tag = root.get("road");
            DataResult<RoadData> parsed = RoadData.CODEC.parse(new Dynamic<>(OPS, tag));
            return parsed.result().orElse(null);
        } catch (IOException e) {
            LOGGER.error("Failed to deserialize road data", e);
            return null;
        }
    }
}
