package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.persistence.files.CoarseTerrainTileFileStorage;
import net.shiroha233.roadweaver.persistence.files.FileStorageIO;
import net.shiroha233.roadweaver.persistence.files.FileStoragePathResolver;
import net.shiroha233.roadweaver.persistence.files.RoadFileStorage;
import net.shiroha233.roadweaver.persistence.files.SignTextFileStorage;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;
import net.shiroha233.roadweaver.persistence.LegacyRoadDataRepairService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一次性 H2 到文件存储迁移协调器。
 */
public final class H2MigrationCoordinator {
    private H2MigrationCoordinator() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final String MARKER_FILE = "h2-to-file-v1.done";

    public static void migrateServer(MinecraftServer server) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            migrateLevel(level);
        }
        LegacyRoadDataRepairService.repairServerMetadata(server);
    }

    public static boolean migrateLevel(ServerLevel level) {
        if (level == null || isCompleted(level)) return false;

        if (!RoadDatabaseManager.hasAnyLegacyDatabase(level)) {
            if (markCompleted(level)) {
                LOGGER.info("未发现旧 H2 数据库，跳过迁移 dimension={}", level.dimension().location());
                return false;
            }
            return false;
        }

        try {
            RoadDatabaseManager.requireDriverAvailable();
        } catch (Exception e) {
            LOGGER.warn("旧 H2 数据库存在但 H2 Driver 不可用，暂不写入迁移完成标记 dimension={}", level.dimension().location(), e);
            return false;
        }

        int structureCount;
        int roadCount;
        int signTextCount;
        int terrainTileCount;
        try {
            structureCount = RoadDatabaseManager.hasLegacyDatabase(level, RoadDatabaseManager.DB_MAP)
                    ? StructureFileStorage.importLegacyState(level)
                    : 0;
            if (RoadDatabaseManager.hasLegacyDatabase(level, RoadDatabaseManager.DB_ROAD)) {
                roadCount = RoadFileStorage.importLegacyRoads(level);
                signTextCount = SignTextFileStorage.importLegacySignTexts(level);
            } else {
                roadCount = 0;
                signTextCount = 0;
            }
            terrainTileCount = RoadDatabaseManager.hasLegacyDatabase(level, RoadDatabaseManager.DB_TERRAIN)
                    ? CoarseTerrainTileFileStorage.importLegacyTiles(level)
                    : 0;
        } catch (LegacyH2Importer.LegacyImportException e) {
            LOGGER.warn("旧 H2 数据迁移失败，暂不写入完成标记 dimension={}", level.dimension().location(), e);
            return false;
        }

        if (!markCompleted(level)) {
            return false;
        }
        LOGGER.info("完成旧 H2 数据迁移 dimension={} structures={} roads={} signTexts={} terrainTiles={}",
                level.dimension().location(), structureCount, roadCount, signTextCount, terrainTileCount);
        return true;
    }

    public static boolean hasPendingLegacyData(ServerLevel level) {
        return level != null && !isCompleted(level) && RoadDatabaseManager.hasAnyLegacyDatabase(level);
    }

    private static boolean isCompleted(ServerLevel level) {
        return Files.exists(markerPath(level));
    }

    private static boolean markCompleted(ServerLevel level) {
        try {
            FileStorageIO.writeStringAtomic(markerPath(level), "version=1\n");
            return true;
        } catch (Exception e) {
            LOGGER.warn("写入 H2 迁移完成标记失败 dimension={}", level.dimension().location(), e);
            return false;
        }
    }

    private static Path markerPath(ServerLevel level) {
        return FileStoragePathResolver.root(level)
                .resolve("migration")
                .resolve(FileStoragePathResolver.dimensionKey(level.dimension().location()))
                .resolve(MARKER_FILE);
    }
}
