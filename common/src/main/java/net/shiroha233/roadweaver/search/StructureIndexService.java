package net.shiroha233.roadweaver.search;

import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.structure.StructureSelectionConfig;
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
     * - 使用配置中的 predictRadiusChunks / biomePrefilter
     * - 使用 StructureSelectionConfig 中的结构选择
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
        // 使用新的 StructureSelectionConfig 获取白名单
        List<Records.StructureInfo> predicted = StructurePredictor.predictOverworldStructuresAroundSpawn(
                level,
                cfg.predictRadiusChunks(),
                cfg.biomePrefilter()
        );
        return StructureVerificationService.verifyPredictedStructures(level, predicted);
    }

    /**
     * 在给定矩形（块坐标）范围内做结构预测 + 验证。
     *
     * - 调用 predictOverworldStructuresInRect
     * - 然后用 StructureVerificationService 过滤伪结构点
     * - 使用 StructureSelectionConfig 中的结构选择
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
        
        // 使用新的 StructureSelectionConfig 获取白名单
        List<String> whitelist = StructureSelectionConfig.get().toWhitelist();
        List<Records.StructureInfo> predicted = StructurePredictor.predictOverworldStructuresInRect(
                level,
                cminx, cminz, cmaxx, cmaxz,
                cfg.biomePrefilter(),
                whitelist,
                List.of()  // 不再使用黑名单，结构选择由 GUI 控制
        );
        return StructureVerificationService.verifyPredictedStructures(level, predicted);
    }
}
