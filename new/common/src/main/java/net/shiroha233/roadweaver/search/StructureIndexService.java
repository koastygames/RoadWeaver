package net.shiroha233.roadweaver.search;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.persistence.sqlite.StructureSqliteStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 结构索引服务
 * 
 * 职责：统一封装结构预测和验证调用，只返回预测并通过验证的结构信息列表
 */
public final class StructureIndexService {

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final boolean CACHE_DEBUG = Boolean.getBoolean("roadweaver.structureCacheDebug");

    private StructureIndexService() {
    }

    /**
     * 预测并验证围绕出生点的结构
     */
    public static List<StructureInfo> predictAndVerifyAroundSpawn(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.structurePredictionEnabled()) {
            return List.of();
        }
        if (!cfg.isStructurePredictionEnabledForDimension(level.dimension().location().toString())) {
            return List.of();
        }

        BlockPos spawn = level.getSharedSpawnPos();
        int radiusChunks = Math.max(1, cfg.predictRadiusChunks());
        int minBlockX = (spawn.getX() >> 4) - radiusChunks;
        int maxBlockX = (spawn.getX() >> 4) + radiusChunks;
        int minBlockZ = (spawn.getZ() >> 4) - radiusChunks;
        int maxBlockZ = (spawn.getZ() >> 4) + radiusChunks;
        return predictAndVerifyInRect(level, minBlockX * 16, minBlockZ * 16, maxBlockX * 16, maxBlockZ * 16);
    }

    /**
     * 在给定矩形范围内做结构预测和验证
     */
    public static List<StructureInfo> predictAndVerifyInRect(ServerLevel level,
                                                                     int minBlockX, int minBlockZ,
                                                                     int maxBlockX, int maxBlockZ) {
        if (level == null) {
            return List.of();
        }

        ModConfig cfg = ConfigService.get();
        if (cfg == null || !cfg.structurePredictionEnabled()) {
            return List.of();
        }
        if (!cfg.isStructurePredictionEnabledForDimension(level.dimension().location().toString())) {
            return List.of();
        }

        StructureSqliteStorage.ensurePolicy(level, policyHash(cfg));

        final long tAll0 = CACHE_DEBUG ? System.nanoTime() : 0L;
        final int beforeCount = CACHE_DEBUG
                ? StructureSqliteStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, StructureSqliteStorage.SOURCE_PREDICTED).size()
                : 0;

        int cminx = Math.floorDiv(minBlockX, 16);
        int cminz = Math.floorDiv(minBlockZ, 16);
        int cmaxx = Math.floorDiv(maxBlockX, 16);
        int cmaxz = Math.floorDiv(maxBlockZ, 16);

        int tileSize = Math.max(1, StructureSqliteStorage.SCAN_TILE_SIZE_CHUNKS);
        int tminx = Math.floorDiv(cminx, tileSize);
        int tminz = Math.floorDiv(cminz, tileSize);
        int tmaxx = Math.floorDiv(cmaxx, tileSize);
        int tmaxz = Math.floorDiv(cmaxz, tileSize);

        int tilesTotal = (tmaxx - tminx + 1) * (tmaxz - tminz + 1);
        int tilesClaimed = 0;
        int predictedTotal = 0;
        int verifiedTotal = 0;

        for (int tx = tminx; tx <= tmaxx; tx++) {
            for (int tz = tminz; tz <= tmaxz; tz++) {
                if (!StructureSqliteStorage.claimScanTile(level, tx, tz)) {
                    continue;
                }
                tilesClaimed++;

                try {
                    int tileMinChunkX = tx * tileSize;
                    int tileMinChunkZ = tz * tileSize;
                    int tileMaxChunkX = tileMinChunkX + tileSize - 1;
                    int tileMaxChunkZ = tileMinChunkZ + tileSize - 1;

                    List<StructureInfo> predicted = StructurePredictor.predictStructuresInRect(
                            level,
                            tileMinChunkX, tileMinChunkZ, tileMaxChunkX, tileMaxChunkZ,
                            cfg.biomePrefilter(),
                            cfg.structureWhitelist(),
                            cfg.structureBlacklist()
                    );
                    List<StructureInfo> verified = StructureVerificationService.verifyPredictedStructures(level, predicted);
                    predictedTotal += predicted != null ? predicted.size() : 0;
                    verifiedTotal += verified != null ? verified.size() : 0;
                    if (verified != null && !verified.isEmpty()) {
                        StructureSqliteStorage.addStructures(level, verified, StructureSqliteStorage.SOURCE_PREDICTED);
                    }
                    StructureSqliteStorage.markScanTileDone(level, tx, tz);
                } catch (Throwable t) {
                    StructureSqliteStorage.releaseScanTile(level, tx, tz);
                    LOGGER.warn("StructureIndexService: scan tile failed tile=[{},{}]", tx, tz, t);
                }
            }
        }

        List<StructureInfo> out = StructureSqliteStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, StructureSqliteStorage.SOURCE_PREDICTED);
        if (CACHE_DEBUG) {
            int afterCount = out.size();
            long ms = (System.nanoTime() - tAll0) / 1_000_000L;
            LOGGER.info("StructureIndexService.predictAndVerifyInRect rect=[{},{}..{},{}] tilesTotal={} tilesClaimed={} predicted={} verified={} cachedBefore={} cachedAfter={} timeMs={}",
                    minBlockX, minBlockZ, maxBlockX, maxBlockZ,
                    tilesTotal, tilesClaimed,
                    predictedTotal, verifiedTotal,
                    beforeCount, afterCount,
                    ms);
        }
        return out;
    }

    private static String policyHash(ModConfig cfg) {
        if (cfg == null) return "";
        List<String> wl = cfg.structureWhitelist() != null ? new ArrayList<>(cfg.structureWhitelist()) : new ArrayList<>();
        List<String> bl = cfg.structureBlacklist() != null ? new ArrayList<>(cfg.structureBlacklist()) : new ArrayList<>();
        Collections.sort(wl);
        Collections.sort(bl);
        String raw = "biomePrefilter=" + cfg.biomePrefilter()
                + "|whitelist=" + String.join(",", wl)
                + "|blacklist=" + String.join(",", bl);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
