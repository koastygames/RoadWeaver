package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 结构点存储门面，底层已切换到文件型存储。
 */
public final class StructureSqliteStorage {
    private StructureSqliteStorage() {}

    public static final int SOURCE_PREDICTED = StructureFileStorage.SOURCE_PREDICTED;
    public static final int SOURCE_MANUAL = StructureFileStorage.SOURCE_MANUAL;
    public static final int SCAN_TILE_SIZE_CHUNKS = 128;

    public static StructureLocationData getStructureLocations(ServerLevel level) {
        return StructureFileStorage.getStructureLocations(level);
    }

    public static void setStructureLocations(ServerLevel level, StructureLocationData data) {
        StructureFileStorage.setStructureLocations(level, data);
    }

    public static List<StructureConnection> getStructureConnections(ServerLevel level) {
        return StructureFileStorage.getStructureConnections(level);
    }

    public static void setStructureConnections(ServerLevel level, List<StructureConnection> connections) {
        StructureFileStorage.setStructureConnections(level, connections);
    }

    public static Set<Long> getPlannedTileKeys(ServerLevel level) {
        return StructureFileStorage.getPlannedTileKeys(level);
    }

    public static void setPlannedTileKeys(ServerLevel level, Set<Long> keys) {
        StructureFileStorage.setPlannedTileKeys(level, keys);
    }

    public static Map<Long, Long> getPlannedTileCenters(ServerLevel level) {
        return StructureFileStorage.getPlannedTileCenters(level);
    }

    public static void setPlannedTileCenters(ServerLevel level, Map<Long, Long> centers) {
        StructureFileStorage.setPlannedTileCenters(level, centers);
    }

    public static void ensurePolicy(ServerLevel level, String policyHash) {
        StructureFileStorage.ensurePolicy(level, policyHash);
    }

    public static boolean claimScanTile(ServerLevel level, int tileX, int tileZ) {
        return StructureFileStorage.claimScanTile(level, tileX, tileZ);
    }

    public static void markScanTileDone(ServerLevel level, int tileX, int tileZ) {
        StructureFileStorage.markScanTileDone(level, tileX, tileZ);
    }

    public static void releaseScanTile(ServerLevel level, int tileX, int tileZ) {
        StructureFileStorage.releaseScanTile(level, tileX, tileZ);
    }

    public static void addStructures(ServerLevel level, List<StructureInfo> infos, int source) {
        StructureFileStorage.addStructures(level, infos, source);
    }

    public static List<StructureInfo> queryRect(ServerLevel level,
                                                int minBlockX, int minBlockZ,
                                                int maxBlockX, int maxBlockZ,
                                                int... sources) {
        return StructureFileStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, sources);
    }

    public static boolean hasAnyStructure(ServerLevel level) {
        return StructureFileStorage.hasAnyStructure(level);
    }
}