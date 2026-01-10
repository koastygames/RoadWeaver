package net.shiroha233.roadweaver.persistence.sqlite;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.RoadSpatialIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
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
 * 基于 H2 的道路数据存储
 * 
 * 优势：
 * - 纯 Java 实现，无 native 依赖，避免平台审核问题
 * - 无需 LRU 缓存，数据直接存取数据库
 * - 支持并发读写，不阻塞区块生成
 * - 空间索引加速矩形范围查询
 * - 自动持久化，无需手动 flush
 */
public final class RoadSqliteStorage {
    private RoadSqliteStorage() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final DynamicOps<Tag> OPS = NbtOps.INSTANCE;
    private static final int ROAD_CACHE_MAX = 4096;

    private static final class CacheKey {
        private final ResourceLocation dimensionId;
        private final long fingerprint;

        private CacheKey(ResourceLocation dimensionId, long fingerprint) {
            this.dimensionId = dimensionId;
            this.fingerprint = fingerprint;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof CacheKey other))
                return false;
            return fingerprint == other.fingerprint && dimensionId.equals(other.dimensionId);
        }

        @Override
        public int hashCode() {
            int h = dimensionId.hashCode();
            h = 31 * h + (int) (fingerprint ^ (fingerprint >>> 32));
            return h;
        }
    }

    private static final Map<CacheKey, Records.RoadData> ROAD_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, Records.RoadData> eldest) {
                    return size() > ROAD_CACHE_MAX;
                }
            });

    // SQL 语句（H2 兼容）
    private static final String SQL_INSERT = "MERGE INTO roads (fingerprint, width, road_type, min_x, min_z, max_x, max_z, data) "
            + "KEY (fingerprint) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_QUERY_RECT = "SELECT fingerprint, data FROM roads " +
            "WHERE max_x >= ? AND min_x <= ? AND max_z >= ? AND min_z <= ?";

    private static final String SQL_EXISTS = "SELECT 1 FROM roads WHERE fingerprint = ? LIMIT 1";

    /**
     * 添加道路数据
     * 
     * @param level 服务器世界
     * @param rd    道路数据
     */
    public static void addRoad(ServerLevel level, Records.RoadData rd) {
        if (rd == null || rd.roadSegmentList() == null || rd.roadSegmentList().isEmpty()) {
            return;
        }

        // 计算 bounding box
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Records.RoadSegmentPlacement seg : rd.roadSegmentList()) {
            BlockPos p = seg.middlePos();
            int x = p.getX(), z = p.getZ();
            if (x < minX)
                minX = x;
            if (z < minZ)
                minZ = z;
            if (x > maxX)
                maxX = x;
            if (z > maxZ)
                maxZ = z;
        }

        long fingerprint = fingerprint(rd);

        try {
            Connection conn = RoadDatabaseManager.getConnection(level);

            // 先检查是否已存在（快速路径）- 使用 MERGE 语句时可以跳过这步
            // 但保留检查可以避免不必要的序列化开销
            try (PreparedStatement checkStmt = conn.prepareStatement(SQL_EXISTS)) {
                checkStmt.setLong(1, fingerprint);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        return; // 已存在，跳过
                    }
                }
            }

            // 序列化道路数据为 NBT 字节数组
            byte[] data = serializeRoadData(rd);
            if (data == null) {
                LOGGER.warn("RoadSqliteStorage: 序列化道路数据失败");
                return;
            }

            // 插入数据库（H2 MERGE 语句自动处理重复）
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

            // 关键：在 C2ME 并发环境下，新插入的道路必须立即让受影响的网格索引失效，
            // 否则当前正在进行的辅助属性线程可能使用旧的（空）索引构建结果。
            int minCX = minX >> 4;
            int minCZ = minZ >> 4;
            int maxCX = maxX >> 4;
            int maxCZ = maxZ >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    RoadSpatialIndex.invalidateChunk(level, cx, cz);
                }
            }

        } catch (SQLException e) {
            LOGGER.error("RoadSqliteStorage: 添加道路数据失败", e);
        }
    }

    /**
     * 查询矩形范围内的道路数据
     * 
     * @param level     服务器世界
     * @param minBlockX 最小 X 坐标
     * @param minBlockZ 最小 Z 坐标
     * @param maxBlockX 最大 X 坐标
     * @param maxBlockZ 最大 Z 坐标
     * @return 范围内的道路数据列表
     */
    public static List<Records.RoadData> queryRect(ServerLevel level,
            int minBlockX, int minBlockZ,
            int maxBlockX, int maxBlockZ) {
        List<Records.RoadData> result = new ArrayList<>();

        try {
            Connection conn = RoadDatabaseManager.getConnection(level);

            try (PreparedStatement stmt = conn.prepareStatement(SQL_QUERY_RECT)) {
                // 空间查询条件：数据库中的 bbox 与查询 bbox 相交
                stmt.setInt(1, minBlockX); // max_x >= minBlockX
                stmt.setInt(2, maxBlockX); // min_x <= maxBlockX
                stmt.setInt(3, minBlockZ); // max_z >= minBlockZ
                stmt.setInt(4, maxBlockZ); // min_z <= maxBlockZ

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long fp = rs.getLong("fingerprint");
                        CacheKey key = new CacheKey(level.dimension().location(), fp);
                        Records.RoadData cached = ROAD_CACHE.get(key);
                        if (cached != null) {
                            result.add(cached);
                            continue;
                        }

                        InputStream in = rs.getBinaryStream("data");
                        if (in == null)
                            continue;
                        try {
                            Records.RoadData rd = deserializeRoadData(in);
                            if (rd != null) {
                                ROAD_CACHE.put(key, rd);
                                result.add(rd);
                            }
                        } finally {
                            try {
                                in.close();
                            } catch (java.io.IOException ignored) {
                            }
                        }
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.error("RoadSqliteStorage: 查询道路数据失败", e);
        }

        return result;
    }

    /**
     * 检查道路是否与矩形相交
     */
    @SuppressWarnings("unused")
    private static boolean intersects(Records.RoadData rd, int minX, int minZ, int maxX, int maxZ) {
        if (rd == null || rd.roadSegmentList() == null || rd.roadSegmentList().isEmpty()) {
            return false;
        }

        int rminX = Integer.MAX_VALUE, rminZ = Integer.MAX_VALUE;
        int rmaxX = Integer.MIN_VALUE, rmaxZ = Integer.MIN_VALUE;

        for (Records.RoadSegmentPlacement seg : rd.roadSegmentList()) {
            BlockPos p = seg.middlePos();
            int x = p.getX(), z = p.getZ();
            if (x < rminX)
                rminX = x;
            if (z < rminZ)
                rminZ = z;
            if (x > rmaxX)
                rmaxX = x;
            if (z > rmaxZ)
                rmaxZ = z;
        }

        return !(rmaxX < minX || rminX > maxX || rmaxZ < minZ || rminZ > maxZ);
    }

    /**
     * 计算道路数据的指纹（用于去重）
     */
    private static long fingerprint(Records.RoadData rd) {
        if (rd == null || rd.roadSegmentList() == null || rd.roadSegmentList().isEmpty()) {
            return 0L;
        }
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

    /**
     * 序列化道路数据为字节数组
     */
    private static byte[] serializeRoadData(Records.RoadData rd) {
        try {
            // 使用 Codec 编码为 NBT
            DataResult<Tag> result = Records.RoadData.CODEC.encodeStart(OPS, rd);
            Tag tag = result.result().orElse(null);
            if (tag == null) {
                return null;
            }

            // 包装为 CompoundTag
            CompoundTag compound = new CompoundTag();
            compound.put("road", tag);

            // 写入字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            NbtIo.write(compound, dos);
            return baos.toByteArray();

        } catch (Exception e) {
            LOGGER.error("RoadSqliteStorage: 序列化失败", e);
            return null;
        }
    }

    /**
     * 反序列化字节数组为道路数据
     */
    @SuppressWarnings("unused")
    private static Records.RoadData deserializeRoadData(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            DataInputStream dis = new DataInputStream(bais);
            return deserializeRoadData(dis);

        } catch (Exception e) {
            LOGGER.error("RoadSqliteStorage: 反序列化失败", e);
            return null;
        }
    }

    private static Records.RoadData deserializeRoadData(InputStream in) {
        try {
            DataInputStream dis = new DataInputStream(in);
            return deserializeRoadData(dis);
        } catch (Exception e) {
            LOGGER.error("RoadSqliteStorage: 反序列化失败", e);
            return null;
        }
    }

    private static Records.RoadData deserializeRoadData(DataInputStream dis) {
        try {
            CompoundTag compound = NbtIo.read(dis);

            if (compound == null || !compound.contains("road")) {
                return null;
            }

            Tag tag = compound.get("road");
            DataResult<Records.RoadData> result = Records.RoadData.CODEC.parse(new Dynamic<>(OPS, tag));
            return result.result().orElse(null);

        } catch (Exception e) {
            LOGGER.error("RoadSqliteStorage: 反序列化失败", e);
            return null;
        }
    }

    /**
     * 刷新（SQLite 自动持久化，此方法仅执行检查点）
     */
    public static void flushAll(ServerLevel level) {
        RoadDatabaseManager.checkpoint(level);
    }

    /**
     * 清除维度的所有道路数据（谨慎使用）
     */
    public static void clearAll(ServerLevel level) {
        try {
            Connection conn = RoadDatabaseManager.getConnection(level);
            try (var stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM roads");
            }
        } catch (SQLException e) {
            LOGGER.error("RoadSqliteStorage: 清除道路数据失败", e);
        }
    }

    /**
     * 关闭数据库连接（服务器停止时调用）
     */
    public static void shutdown() {
        RoadDatabaseManager.checkpointAll();
        RoadDatabaseManager.closeAll();
    }
}
