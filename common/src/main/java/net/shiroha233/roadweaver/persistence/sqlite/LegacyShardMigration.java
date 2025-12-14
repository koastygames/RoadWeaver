package net.shiroha233.roadweaver.persistence.sqlite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.shiroha233.roadweaver.helpers.Records;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * 旧分片 NBT 数据迁移服务
 *
 * <p>用于兼容旧版本 RoadWeaver 的道路数据存储格式：
 * 旧版本会将道路数据写在 r.&lt;rx&gt;.&lt;rz&gt;.nbt 分片文件中。
 * 新版本使用 SQLite（roads.db）。
 *
 * <p>本类负责：
 * 1. 检测旧分片文件是否存在
 * 2. 读取并解析旧数据
 * 3. 导入 SQLite（去重）
 * 4. 写入迁移标记文件，避免重复迁移
 *
 * <p>SRP：只负责“旧数据迁移”，不参与 SQLite 读写业务逻辑。
 */
public final class LegacyShardMigration {
    private LegacyShardMigration() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final DynamicOps<Tag> OPS = NbtOps.INSTANCE;

    private static final String MIGRATION_MARKER = ".migrated_from_nbt";

    // 已处理的维度（避免同一会话重复扫描磁盘）
    private static final Set<String> MIGRATED_DIMS = ConcurrentHashMap.newKeySet();

    private static Path legacyBasePath(ServerLevel level) {
        ResourceLocation rl = level.dimension().location();
        String dimKey = rl.getNamespace() + "/" + rl.getPath();
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve("data/roadweaver/roads").resolve(dimKey);
    }

    private static Path migrationMarkerPath(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve("data/roadweaver").resolve(dimKeyForDb(level)).resolve(MIGRATION_MARKER);
    }

    private static String dimKeyForDb(ServerLevel level) {
        ResourceLocation rl = level.dimension().location();
        return rl.getNamespace() + "_" + rl.getPath();
    }

    private static String dimCacheKey(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        String worldId = worldRoot == null ? "unknown" : worldRoot.toAbsolutePath().normalize().toString();
        return worldId + "|" + dimKeyForDb(level);
    }

    /**
     * 检测并迁移旧数据（仅旧存档触发）
     *
     * <p>注意：本方法会调用 {@link RoadSqliteStorage#addRoad(ServerLevel, Records.RoadData)}。
     * 因此必须在 {@link RoadDatabaseManager#getConnection(ServerLevel)} 将连接放入连接池之后调用，
     * 避免迁移过程中递归创建连接。
     *
     * @return 迁移条数（0 表示无需迁移或已迁移）
     */
    public static int migrateIfNeeded(ServerLevel level) {
        String cacheKey = dimCacheKey(level);
        if (MIGRATED_DIMS.contains(cacheKey)) {
            return 0;
        }

        Path markerPath = migrationMarkerPath(level);
        if (Files.exists(markerPath)) {
            MIGRATED_DIMS.add(cacheKey);
            return 0;
        }

        Path legacyDir = legacyBasePath(level);
        if (!Files.exists(legacyDir) || !Files.isDirectory(legacyDir)) {
            // 无旧数据目录：不迁移、不写标记（仅旧存档才触发迁移）
            MIGRATED_DIMS.add(cacheKey);
            return 0;
        }

        if (!hasLegacyShardFiles(legacyDir)) {
            MIGRATED_DIMS.add(cacheKey);
            return 0;
        }

        int migrated = performMigration(level, legacyDir);

        createMigrationMarker(markerPath);
        MIGRATED_DIMS.add(cacheKey);

        return migrated;
    }

    private static int performMigration(ServerLevel level, Path legacyDir) {
        LOGGER.info("LegacyShardMigration: 开始迁移旧道路数据 - 维度: {}", level.dimension().location());
        notifyPlayers(level, Component.translatable("message.roadweaver.migration.start"));

        int totalMigrated = 0;
        int fileCount = 0;
        Set<Long> seenFingerprints = new HashSet<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(legacyDir, "r.*.*.nbt")) {
            for (Path shardFile : stream) {
                fileCount++;
                List<Records.RoadData> roads = loadShardFile(shardFile);

                for (Records.RoadData rd : roads) {
                    if (rd == null || rd.roadSegmentList() == null || rd.roadSegmentList().isEmpty()) {
                        continue;
                    }

                    long fp = fingerprint(rd);
                    if (seenFingerprints.add(fp)) {
                        RoadSqliteStorage.addRoad(level, rd);
                        totalMigrated++;
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("LegacyShardMigration: 读取旧数据目录失败", e);
        }

        LOGGER.info("LegacyShardMigration: 迁移完成 - 维度: {}, 文件数: {}, 道路数: {}",
            level.dimension().location(), fileCount, totalMigrated);
        notifyPlayers(level, Component.translatable("message.roadweaver.migration.done", totalMigrated));

        return totalMigrated;
    }

    private static boolean hasLegacyShardFiles(Path legacyDir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(legacyDir, "r.*.*.nbt")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    private static void notifyPlayers(ServerLevel level, Component message) {
        if (level == null || level.getServer() == null) return;
        level.getServer().execute(() -> {
            try {
                level.getServer().getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(message));
            } catch (Exception ignored) {
            }
        });
    }

    private static List<Records.RoadData> loadShardFile(Path shardFile) {
        List<Records.RoadData> roads = new ArrayList<>();

        try (InputStream is = Files.newInputStream(shardFile);
             GZIPInputStream gzip = new GZIPInputStream(is);
             DataInputStream dis = new DataInputStream(gzip)) {

            CompoundTag tag = NbtIo.read(dis);
            if (tag != null && tag.contains("roads")) {
                Tag list = tag.get("roads");
                DataResult<List<Records.RoadData>> res = Codec.list(Records.RoadData.CODEC)
                    .parse(new Dynamic<>(OPS, list));
                res.result().ifPresent(roads::addAll);
            }

        } catch (IOException e) {
            LOGGER.warn("LegacyShardMigration: 读取分片文件失败: {}", shardFile, e);
        }

        return roads;
    }

    // 与 RoadSqliteStorage 一致：用于迁移阶段快速去重
    private static long fingerprint(Records.RoadData rd) {
        if (rd == null || rd.roadSegmentList() == null || rd.roadSegmentList().isEmpty()) {
            return 0L;
        }
        var a = rd.roadSegmentList().get(0).middlePos();
        var b = rd.roadSegmentList().get(rd.roadSegmentList().size() - 1).middlePos();
        long ka = (((long) a.getX()) << 32) ^ (a.getZ() & 0xffffffffL);
        long kb = (((long) b.getX()) << 32) ^ (b.getZ() & 0xffffffffL);
        long lo = Math.min(ka, kb), hi = Math.max(ka, kb);
        long f = (hi << 1) ^ lo;
        f ^= ((long) rd.width() & 0xffffffffL);
        f ^= ((long) rd.roadType() & 0xffffffffL) << 33;
        return f;
    }

    private static void createMigrationMarker(Path markerPath) {
        try {
            Files.createDirectories(markerPath.getParent());
            Files.writeString(markerPath,
                "# RoadWeaver 数据迁移标记\n" +
                    "# 此文件表示旧分片 NBT 数据已迁移到 SQLite\n" +
                    "# 删除此文件将导致下次启动时重新迁移（不会产生重复数据）\n" +
                    "migrated_at=" + System.currentTimeMillis() + "\n");
        } catch (IOException e) {
            LOGGER.warn("LegacyShardMigration: 创建迁移标记失败", e);
        }
    }

    public static void reset() {
        MIGRATED_DIMS.clear();
    }
}
