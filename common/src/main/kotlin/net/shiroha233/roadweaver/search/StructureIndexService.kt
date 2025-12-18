package net.shiroha233.roadweaver.search

import net.minecraft.server.level.ServerLevel
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.config.structure.StructureSelectionConfig
import net.shiroha233.roadweaver.helpers.Records

/**
 * 结构索引/预测服务。
 */
object StructureIndexService {
    /**
     * 预测并验证「围绕出生点」的一圈结构。
     */
    @JvmStatic
    fun predictAndVerifyAroundSpawn(level: ServerLevel?): List<Records.StructureInfo> {
        if (level === null) {
            return listOf()
        }
        val cfg: ModConfig = ConfigService.get()
        if (!cfg.villagePredictionEnabled()) {
            return listOf()
        }
        val whitelist = StructureSelectionConfig.get().toWhitelist()
        val predicted = StructurePredictor.predictOverworldStructuresAroundSpawn(
            level,
            cfg.predictRadiusChunks(),
            cfg.biomePrefilter(),
            whitelist,
            listOf()
        )
        return StructureVerificationService.verifyPredictedStructures(level, predicted)
    }

    /**
     * 在给定矩形（块坐标）范围内做结构预测 + 验证。
     */
    @JvmStatic
    fun predictAndVerifyInRect(level: ServerLevel?, minBlockX: Int, minBlockZ: Int, maxBlockX: Int, maxBlockZ: Int): List<Records.StructureInfo> {
        if (level === null) {
            return listOf()
        }
        val cfg: ModConfig = ConfigService.get()
        if (!cfg.villagePredictionEnabled()) {
            return listOf()
        }
        val cminx = Math.floorDiv(minBlockX, 16)
        val cminz = Math.floorDiv(minBlockZ, 16)
        val cmaxx = Math.floorDiv(maxBlockX, 16)
        val cmaxz = Math.floorDiv(maxBlockZ, 16)

        val whitelist = StructureSelectionConfig.get().toWhitelist()
        val predicted = StructurePredictor.predictOverworldStructuresInRect(
            level,
            cminx,
            cminz,
            cmaxx,
            cmaxz,
            cfg.biomePrefilter(),
            whitelist,
            listOf()
        )
        return StructureVerificationService.verifyPredictedStructures(level, predicted)
    }
}
