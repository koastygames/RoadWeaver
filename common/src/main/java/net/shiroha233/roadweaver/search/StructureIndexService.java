package net.shiroha233.roadweaver.search;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.helpers.LevelCompat;
import net.shiroha233.roadweaver.persistence.sqlite.StructureSqliteStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 负责结构预测结果的分块扫描、验证与缓存落盘。
 */
public final class StructureIndexService {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final boolean CACHE_DEBUG = Boolean.getBoolean("roadweaver.structureCacheDebug");

    private StructureIndexService() {
    }

    /**
     * 围绕世界出生点执行结构预测和验证。
     */
    public static List<StructureInfo> predictAndVerifyAroundSpawn(ServerLevel level) {
        if (level == null) {
            return List.of();
        }

        ModConfig config = ConfigService.get();
        if (config == null || !config.structurePredictionEnabled()) {
            return List.of();
        }
        if (!config.isStructurePredictionEnabledForDimension(level.dimension().identifier().toString())) {
            return List.of();
        }

        BlockPos spawn = LevelCompat.getWorldSpawnPos(level);
        int radiusChunks = Math.max(1, config.predictRadiusChunks());
        int minChunkX = (spawn.getX() >> 4) - radiusChunks;
        int maxChunkX = (spawn.getX() >> 4) + radiusChunks;
        int minChunkZ = (spawn.getZ() >> 4) - radiusChunks;
        int maxChunkZ = (spawn.getZ() >> 4) + radiusChunks;
        return predictAndVerifyInRect(level, minChunkX * 16, minChunkZ * 16, maxChunkX * 16, maxChunkZ * 16);
    }

    /**
     * 在给定矩形区域内执行结构预测和验证。
     */
    public static List<StructureInfo> predictAndVerifyInRect(ServerLevel level,
            int minBlockX,
            int minBlockZ,
            int maxBlockX,
            int maxBlockZ) {
        if (level == null) {
            return List.of();
        }

        ModConfig config = ConfigService.get();
        if (config == null || !config.structurePredictionEnabled()) {
            return List.of();
        }
        if (!config.isStructurePredictionEnabledForDimension(level.dimension().identifier().toString())) {
            return List.of();
        }

        StructureSqliteStorage.ensurePolicy(level, policyHash(config));

        long startNanos = CACHE_DEBUG ? System.nanoTime() : 0L;
        int cachedBefore = CACHE_DEBUG
                ? StructureSqliteStorage.queryRect(
                        level,
                        minBlockX,
                        minBlockZ,
                        maxBlockX,
                        maxBlockZ,
                        StructureSqliteStorage.SOURCE_PREDICTED).size()
                : 0;

        int minChunkX = Math.floorDiv(minBlockX, 16);
        int minChunkZ = Math.floorDiv(minBlockZ, 16);
        int maxChunkX = Math.floorDiv(maxBlockX, 16);
        int maxChunkZ = Math.floorDiv(maxBlockZ, 16);

        int tileSize = Math.max(1, StructureSqliteStorage.SCAN_TILE_SIZE_CHUNKS);
        int minTileX = Math.floorDiv(minChunkX, tileSize);
        int minTileZ = Math.floorDiv(minChunkZ, tileSize);
        int maxTileX = Math.floorDiv(maxChunkX, tileSize);
        int maxTileZ = Math.floorDiv(maxChunkZ, tileSize);

        int tilesTotal = (maxTileX - minTileX + 1) * (maxTileZ - minTileZ + 1);
        int tilesClaimed = 0;
        int predictedTotal = 0;
        int verifiedTotal = 0;

        for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
            for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                if (!StructureSqliteStorage.claimScanTile(level, tileX, tileZ)) {
                    continue;
                }
                tilesClaimed++;

                try {
                    int tileMinChunkX = tileX * tileSize;
                    int tileMinChunkZ = tileZ * tileSize;
                    int tileMaxChunkX = tileMinChunkX + tileSize - 1;
                    int tileMaxChunkZ = tileMinChunkZ + tileSize - 1;

                    List<StructureInfo> predicted = StructurePredictor.predictStructuresInRect(
                            level,
                            tileMinChunkX,
                            tileMinChunkZ,
                            tileMaxChunkX,
                            tileMaxChunkZ,
                            config.biomePrefilter(),
                            config.structureWhitelist(),
                            config.structureBlacklist());
                    List<StructureInfo> verified = StructureVerificationService.verifyPredictedStructures(level, predicted);

                    predictedTotal += predicted != null ? predicted.size() : 0;
                    verifiedTotal += verified != null ? verified.size() : 0;

                    if (verified != null && !verified.isEmpty()) {
                        StructureSqliteStorage.addStructures(
                                level,
                                verified,
                                StructureSqliteStorage.SOURCE_PREDICTED);
                    }
                    StructureSqliteStorage.markScanTileDone(level, tileX, tileZ);
                } catch (Throwable throwable) {
                    StructureSqliteStorage.releaseScanTile(level, tileX, tileZ);
                    LOGGER.warn("StructureIndexService: scan tile failed tile=[{},{}]", tileX, tileZ, throwable);
                }
            }
        }

        List<StructureInfo> result = StructureSqliteStorage.queryRect(
                level,
                minBlockX,
                minBlockZ,
                maxBlockX,
                maxBlockZ,
                StructureSqliteStorage.SOURCE_PREDICTED);
        if (CACHE_DEBUG) {
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            LOGGER.info(
                    "StructureIndexService.predictAndVerifyInRect rect=[{},{}..{},{}] tilesTotal={} tilesClaimed={} predicted={} verified={} cachedBefore={} cachedAfter={} timeMs={}",
                    minBlockX,
                    minBlockZ,
                    maxBlockX,
                    maxBlockZ,
                    tilesTotal,
                    tilesClaimed,
                    predictedTotal,
                    verifiedTotal,
                    cachedBefore,
                    result.size(),
                    elapsedMillis);
        }
        return result;
    }

    private static String policyHash(ModConfig config) {
        if (config == null) {
            return "";
        }

        List<String> whitelist = config.structureWhitelist() != null
                ? new ArrayList<>(config.structureWhitelist())
                : new ArrayList<>();
        List<String> blacklist = config.structureBlacklist() != null
                ? new ArrayList<>(config.structureBlacklist())
                : new ArrayList<>();
        Collections.sort(whitelist);
        Collections.sort(blacklist);

        String raw = "biomePrefilter=" + config.biomePrefilter()
                + "|whitelist=" + String.join(",", whitelist)
                + "|blacklist=" + String.join(",", blacklist);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
