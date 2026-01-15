package net.shiroha233.roadweaver.persistence.sqlite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.shiroha233.roadweaver.helpers.Records;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 旧分片 NBT 数据迁移服务
 * 
 * 在首次访问 H2 数据库时，检测并导入旧的 r.rx.rz.nbt 分片文件。
 * 迁移完成后会创建一个标记文件，避免重复迁移。
 * 
 * 旧数据路径: data/roadweaver/roads/<dimKey>/r.<rx>.<rz>.nbt
 * 新数据路径: data/roadweaver/<dimKey>/roads.mv.db (H2)
 */
public final class LegacyShardMigration {
    private LegacyShardMigration() {}
    
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final DynamicOps<Tag> OPS = NbtOps.INSTANCE;
    
    // 迁移标记文件名
    private static final String MIGRATION_MARKER = ".migrated_from_nbt";
    
    // 已迁移的维度（避免同一会话重复检查）
    private static final Set<String> MIGRATED_DIMS = ConcurrentHashMap.newKeySet();
    
    /**
     * 获取旧分片数据的基础路径
     */
    private static Path legacyBasePath(ServerLevel level) {
        ResourceLocation rl = level.dimension().location();
        String dimKey = rl.getNamespace() + "/" + rl.getPath();
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve("data/roadweaver/roads").resolve(dimKey);
    }
    
    /**
     * 获取迁移标记文件路径
     */
    private static Path migrationMarkerPath(ServerLevel level) {
        ResourceLocation rl = level.dimension().location();
        String dimKey = rl.getNamespace() + "_" + rl.getPath();
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve("data/roadweaver").resolve(dimKey).resolve(MIGRATION_MARKER);
    }
    
    /**
     * 获取维度唯一键（用于会话缓存）
     */
    private static String dimCacheKey(ServerLevel level) {
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        String worldId = worldRoot == null ? "unknown" : worldRoot.toAbsolutePath().normalize().toString();
        ResourceLocation rl = level.dimension().location();
        return worldId + "|" + rl.getNamespace() + "_" + rl.getPath();
    }
    
    /**
     * 检查并执行迁移（如果需要）
     * 
     * 在 RoadDatabaseManager.getConnection() 成功创建连接后调用此方法。
     * 
     * @param level 服务器世界
     * @return 迁移的道路数量（0 表示无需迁移或已迁移）
     */
    public static int migrateIfNeeded(ServerLevel level) {
        String cacheKey = dimCacheKey(level);
        
        // 会话内已处理过
        if (MIGRATED_DIMS.contains(cacheKey)) {
            return 0;
        }
        
        // 检查迁移标记
        Path markerPath = migrationMarkerPath(level);
        if (Files.exists(markerPath)) {
            MIGRATED_DIMS.add(cacheKey);
            return 0;
        }
        
        // 检查旧数据目录是否存在
        Path legacyDir = legacyBasePath(level);
        if (!Files.exists(legacyDir) || !Files.isDirectory(legacyDir)) {
            // 无旧数据目录，不迁移，也不写标记（仅在旧存档才触发迁移）
            MIGRATED_DIMS.add(cacheKey);
            return 0;
        }

        // 旧目录存在，但没有任何分片文件则视为“无旧数据”
        if (!hasLegacyShardFiles(legacyDir)) {
            MIGRATED_DIMS.add(cacheKey);
            return 0;
        }
        
        // 执行迁移
        int migrated = performMigration(level, legacyDir);
        
        // 创建迁移标记
        createMigrationMarker(markerPath);
        MIGRATED_DIMS.add(cacheKey);
        
        return migrated;
    }
    
    /**
     * 执行实际的数据迁移
     */
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
                    
                    // 计算指纹用于去重
                    long fp = fingerprint(rd);
                    if (seenFingerprints.add(fp)) {
                        // 直接写入 SQLite（RoadSqliteStorage.addRoad 内部也会去重）
                        RoadSqliteStorage.addRoad(level, rd);
                        totalMigrated++;
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("LegacyShardMigration: 读取旧数据目录失败", e);
        }
        
        if (totalMigrated > 0) {
            LOGGER.info("LegacyShardMigration: 迁移完成 - 维度: {}, 文件数: {}, 道路数: {}", 
                level.dimension().location(), fileCount, totalMigrated);
        }

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
    
    /**
     * 从分片文件加载道路数据
     */
    private static List<Records.RoadData> loadShardFile(Path shardFile) {
        List<Records.RoadData> roads = new ArrayList<>();
        
        try {
            CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(shardFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
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
    
    /**
     * 计算道路数据的指纹（与 RoadSqliteStorage 一致）
     */
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
    
    /**
     * 创建迁移标记文件
     */
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
    
    /**
     * 重置迁移状态（服务器停止时调用）
     */
    public static void reset() {
        MIGRATED_DIMS.clear();
    }
}
