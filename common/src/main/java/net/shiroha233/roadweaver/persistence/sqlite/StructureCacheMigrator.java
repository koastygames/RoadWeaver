package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * 结构点缓存迁移器（SRP）。
 *
 * 职责：
 * - 将旧的 WorldDataProvider.StructureLocationData 中的结构点（位置/类型）一次性迁移到 SQLite。
 * - 迁移完成后写入 SQLite meta 标记，避免重复迁移。
 */
public final class StructureCacheMigrator {
    private StructureCacheMigrator() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    public static void migrateLegacyIfNeeded(ServerLevel level) {
        if (level == null) return;
        if (StructureSqliteStorage.isLegacyMigrated(level)) return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        Records.StructureLocationData loc = provider.getStructureLocations(level);

        List<BlockPos> locations = (loc != null && loc.structureLocations() != null) ? loc.structureLocations() : List.of();
        List<Records.StructureInfo> infos = (loc != null && loc.structureInfos() != null) ? loc.structureInfos() : List.of();

        if ((locations == null || locations.isEmpty()) && (infos == null || infos.isEmpty())) {
            // 无旧数据也标记完成，避免每次都读 SavedData/Attachment
            StructureSqliteStorage.markLegacyMigrated(level);
            return;
        }

        // 优先使用结构类型信息（StructureInfo）。同一坐标只保留一个 id（unknown 会被更具体的 id 覆盖）。
        HashMap<Long, String> idByPos = new HashMap<>();
        if (infos != null) {
            for (Records.StructureInfo info : infos) {
                if (info == null || info.pos() == null) continue;
                BlockPos p = info.pos();
                int x = p.getX();
                int z = p.getZ();
                String id = info.structureId() == null ? "unknown" : info.structureId();
                long key = (((long) x) << 32) ^ (z & 0xffffffffL);
                String prev = idByPos.get(key);
                if (prev == null || "unknown".equals(prev)) {
                    idByPos.put(key, id);
                }
            }
        }

        if (locations != null) {
            for (BlockPos p : locations) {
                if (p == null) continue;
                int x = p.getX();
                int z = p.getZ();
                long key = (((long) x) << 32) ^ (z & 0xffffffffL);
                idByPos.putIfAbsent(key, "unknown");
            }
        }

        if (idByPos.isEmpty()) {
            StructureSqliteStorage.markLegacyMigrated(level);
            return;
        }

        ArrayList<Records.StructureInfo> batch = new ArrayList<>(idByPos.size());
        HashSet<Long> seen = new HashSet<>();
        for (var e : idByPos.entrySet()) {
            long key = e.getKey();
            if (!seen.add(key)) continue;
            int x = (int) (key >> 32);
            int z = (int) key;
            batch.add(new Records.StructureInfo(new BlockPos(x, 0, z), e.getValue()));
        }

        StructureSqliteStorage.addStructures(level, batch, StructureSqliteStorage.SOURCE_LEGACY);
        StructureSqliteStorage.markLegacyMigrated(level);

        LOGGER.info("StructureCacheMigrator: migrated {} legacy structure points into SQLite for {}", batch.size(), level.dimension().location());
    }
}
