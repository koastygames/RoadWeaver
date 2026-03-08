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

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 基于 H2 的道路数据存储，支持空间索引查询和 LRU 反序列化缓存
 */
public final class RoadSqliteStorage {
    private RoadSqliteStorage() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final DynamicOps<Tag> OPS = NbtOps.INSTANCE;
    private static final int ROAD_CACHE_MAX = 4096;

    private static final class CacheKey {
        private final Identifier dimensionId;
        private final long fingerprint;

        private CacheKey(Identifier dimensionId, long fingerprint) {
            this.dimensionId = dimensionId;
            this.fingerprint = fingerprint;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey other)) return false;
            return fingerprint == other.fingerprint && dimensionId.equals(other.dimensionId);
        }

        @Override
        public int hashCode() {
            int h = dimensionId.hashCode();
            h = 31 * h + (int) (fingerprint ^ (fingerprint >>> 32));
            return h;
        }
    }

    private static final Map<CacheKey, RoadData> ROAD_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, RoadData> eldest) {
                    return size() > ROAD_CACHE_MAX;
                }
            });

    private static final String SQL_INSERT =
            "MERGE INTO roads (fingerprint, width, road_type, min_x, min_z, max_x, max_z, data) "
            + "KEY (fingerprint) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_QUERY_RECT =
            "SELECT fingerprint, data FROM roads "
            + "WHERE max_x >= ? AND min_x <= ? AND max_z >= ? AND min_z <= ?";

    private static final String SQL_EXISTS = "SELECT 1 FROM roads WHERE fingerprint = ? LIMIT 1";
    private static final String SQL_DELETE = "DELETE FROM roads WHERE fingerprint = ?";

    public static void addRoad(ServerLevel level, RoadData rd) {
        if (rd == null || rd.roadSegmentList() == null || rd.roadSegmentList().isEmpty()) return;

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (RoadSegmentPlacement seg : rd.roadSegmentList()) {
            BlockPos p = seg.middlePos();
            int x = p.getX(), z = p.getZ();
            if (x < minX) minX = x;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (z > maxZ) maxZ = z;
        }

        long fingerprint = fingerprint(rd);

        try {
            Connection conn = RoadDatabaseManager.getConnection(level);

            try (PreparedStatement checkStmt = conn.prepareStatement(SQL_EXISTS)) {
                checkStmt.setLong(1, fingerprint);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) return;
                }
            }

            byte[] data = serializeRoadData(rd);
            if (data == null) {
                LOGGER.warn("序列化道路数据失败");
                return;
            }

            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT)) {
                stmt.setLong(1, fingerprint);
                stmt.setInt(2, rd.width());
                stmt.setInt(3, rd.roadType());
                stmt.setInt(4, minX);
                stmt.setInt(5, minZ);
                stmt.setInt(6, maxX);
                stmt.setInt(7, maxZ);
                stmt.setBytes(8, data);
                stmt.executeUpdate();
            }

            int minCX = minX >> 4, minCZ = minZ >> 4;
            int maxCX = maxX >> 4, maxCZ = maxZ >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    RoadSpatialIndex.invalidateChunk(level, cx, cz);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("添加道路数据失败", e);
        }
    }

    public static List<RoadData> queryRect(ServerLevel level,
                                           int minBlockX, int minBlockZ,
                                           int maxBlockX, int maxBlockZ) {
        List<RoadData> result = new ArrayList<>();
        try {
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

                        InputStream in = rs.getBinaryStream("data");
                        if (in == null) continue;
                        try {
                            RoadData rd = deserializeRoadData(in);
                            if (rd != null) {
                                ROAD_CACHE.put(key, rd);
                                result.add(rd);
                            }
                        } finally {
                            try { in.close(); } catch (IOException ignored) {}
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("查询道路数据失败", e);
        }
        return result;
    }

    private static long fingerprint(RoadData rd) {
        if (rd == null || rd.roadSegmentList() == null || rd.roadSegmentList().isEmpty()) return 0L;
        BlockPos a = rd.roadSegmentList().get(0).middlePos();
        BlockPos b = rd.roadSegmentList().get(rd.roadSegmentList().size() - 1).middlePos();
        long ka = (((long) a.getX()) << 32) ^ (a.getZ() & 0xffffffffL);
        long kb = (((long) b.getX()) << 32) ^ (b.getZ() & 0xffffffffL);
        long lo = Math.min(ka, kb), hi = Math.max(ka, kb);
        long f = (hi << 1) ^ lo;
        f ^= ((long) rd.width() & 0xffffffffL);
        f ^= ((long) rd.roadType() & 0xffffffffL) << 33;
        return f;
    }

    private static byte[] serializeRoadData(RoadData rd) {
        try {
            DataResult<Tag> result = RoadData.CODEC.encodeStart(OPS, rd);
            Tag tag = result.result().orElse(null);
            if (tag == null) return null;

            CompoundTag compound = new CompoundTag();
            compound.put("road", tag);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.write(compound, new DataOutputStream(baos));
            return baos.toByteArray();
        } catch (Exception e) {
            LOGGER.error("序列化失败", e);
            return null;
        }
    }

    private static RoadData deserializeRoadData(InputStream in) {
        try {
            CompoundTag compound = NbtIo.read(new DataInputStream(in));
            if (compound == null || !compound.contains("road")) return null;

            Tag tag = compound.get("road");
            DataResult<RoadData> result = RoadData.CODEC.parse(new Dynamic<>(OPS, tag));
            return result.result().orElse(null);
        } catch (Exception e) {
            LOGGER.error("反序列化失败", e);
            return null;
        }
    }

    public static long computeFingerprint(RoadData rd) {
        return fingerprint(rd);
    }

    public static void deleteRoad(ServerLevel level, long fp) {
        try {
            Connection conn = RoadDatabaseManager.getConnection(level);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE)) {
                stmt.setLong(1, fp);
                stmt.executeUpdate();
            }
            ROAD_CACHE.remove(new CacheKey(level.dimension().identifier(), fp));
        } catch (SQLException e) {
            LOGGER.error("删除道路数据失败", e);
        }
    }

    public static void replaceRoad(ServerLevel level, long oldFp, RoadData newRd) {
        if (newRd == null || newRd.roadSegmentList() == null || newRd.roadSegmentList().isEmpty()) return;
        deleteRoad(level, oldFp);
        addRoadForce(level, newRd);
    }

    private static void addRoadForce(ServerLevel level, RoadData rd) {
        if (rd == null || rd.roadSegmentList() == null || rd.roadSegmentList().isEmpty()) return;

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (RoadSegmentPlacement seg : rd.roadSegmentList()) {
            BlockPos p = seg.middlePos();
            int x = p.getX(), z = p.getZ();
            if (x < minX) minX = x;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (z > maxZ) maxZ = z;
        }

        long fp = fingerprint(rd);
        byte[] data = serializeRoadData(rd);
        if (data == null) return;

        try {
            Connection conn = RoadDatabaseManager.getConnection(level);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT)) {
                stmt.setLong(1, fp);
                stmt.setInt(2, rd.width());
                stmt.setInt(3, rd.roadType());
                stmt.setInt(4, minX);
                stmt.setInt(5, minZ);
                stmt.setInt(6, maxX);
                stmt.setInt(7, maxZ);
                stmt.setBytes(8, data);
                stmt.executeUpdate();
            }
            ROAD_CACHE.put(new CacheKey(level.dimension().identifier(), fp), rd);

            int minCX = minX >> 4, minCZ = minZ >> 4;
            int maxCX = maxX >> 4, maxCZ = maxZ >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    RoadSpatialIndex.invalidateChunk(level, cx, cz);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("强制写入道路数据失败", e);
        }
    }

    public static void flushAll(ServerLevel level) {
        RoadDatabaseManager.checkpoint(level);
    }

    public static void clearAll(ServerLevel level) {
        try {
            Connection conn = RoadDatabaseManager.getConnection(level);
            try (var stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM roads");
            }
        } catch (SQLException e) {
            LOGGER.error("清除道路数据失败", e);
        }
    }

    public static void shutdown() {
        RoadDatabaseManager.checkpointAll();
        RoadDatabaseManager.closeAll();
    }
}
