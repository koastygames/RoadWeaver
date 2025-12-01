package net.shiroha233.roadweaver.search;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.helpers.Records;

import java.util.List;

/**
 * 结构索引/预测服务。
 *
 * 职责：
 * - 统一封装对 StructurePredictor + StructureVerificationService 的调用
 * - 只返回预测并通过验证的结构信息列表，不负责写回世界存储
 *
 * 这样可以逐步把「结构搜寻」从 MapDataCollector 等调用方中抽离出来，
 * 以后如果要做缓存或增量更新，只需要改这里的实现即可。
 */
public final class StructureIndexService {

    private StructureIndexService() {
    }

    /**
     * 预测并验证「围绕出生点」的一圈结构。
     *
     * - 使用配置中的 predictRadiusChunks / biomePrefilter / 白黑名单
     * - 若未开启结构预测开关，则返回空列表
     */
    public static List<Records.StructureInfo> predictAndVerifyAroundSpawn(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        ModConfig cfg = ConfigService.get();
        if (!cfg.villagePredictionEnabled()) {
            return List.of();
        }
        List<Records.StructureInfo> predicted = StructurePredictor.predictOverworldStructuresAroundSpawn(
                level,
                cfg.predictRadiusChunks(),
                cfg.biomePrefilter(),
                cfg.structureWhitelist(),
                cfg.structureBlacklist()
        );
        return StructureVerificationService.verifyPredictedStructures(level, predicted);
    }

    /**
     * 在给定矩形（块坐标）范围内做结构预测 + 验证。
     *
     * - 调用 predictOverworldStructuresInRect
     * - 然后用 StructureVerificationService 过滤伪结构点
     * - 若未开启结构预测开关，则返回空列表
     */
    public static List<Records.StructureInfo> predictAndVerifyInRect(ServerLevel level,
                                                                     int minBlockX, int minBlockZ,
                                                                     int maxBlockX, int maxBlockZ) {
        if (level == null) {
            return List.of();
        }
        ModConfig cfg = ConfigService.get();
        if (!cfg.villagePredictionEnabled()) {
            return List.of();
        }
        int cminx = Math.floorDiv(minBlockX, 16);
        int cminz = Math.floorDiv(minBlockZ, 16);
        int cmaxx = Math.floorDiv(maxBlockX, 16);
        int cmaxz = Math.floorDiv(maxBlockZ, 16);
        List<Records.StructureInfo> predicted = StructurePredictor.predictOverworldStructuresInRect(
                level,
                cminx, cminz, cmaxx, cmaxz,
                cfg.biomePrefilter(),
                cfg.structureWhitelist(),
                cfg.structureBlacklist()
        );
        return StructureVerificationService.verifyPredictedStructures(level, predicted);
    }
}
