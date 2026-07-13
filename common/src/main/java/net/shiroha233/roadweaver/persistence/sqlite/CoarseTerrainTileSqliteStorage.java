package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainTile;
import net.shiroha233.roadweaver.pathfinding.terrain.region.CoarseTerrainTileKey;
import net.shiroha233.roadweaver.persistence.files.CoarseTerrainTileFileStorage;

/**
 * 粗采样地形瓦片存储门面，底层已切换到文件型存储。
 */
public final class CoarseTerrainTileSqliteStorage {
    private CoarseTerrainTileSqliteStorage() {}

    public static CoarseTerrainTile loadTile(ServerLevel level, CoarseTerrainTileKey key) {
        return CoarseTerrainTileFileStorage.loadTile(level, key);
    }

    public static void saveTile(ServerLevel level, CoarseTerrainTile tile) {
        CoarseTerrainTileFileStorage.saveTile(level, tile);
    }

    public static void deleteBySchemaVersion(ServerLevel level, int currentSchemaVersion) {
        CoarseTerrainTileFileStorage.deleteBySchemaVersion(level, currentSchemaVersion);
    }

    public static void pruneOldTiles(ServerLevel level, long olderThanEpochSeconds) {
        CoarseTerrainTileFileStorage.pruneOldTiles(level, olderThanEpochSeconds);
    }
}