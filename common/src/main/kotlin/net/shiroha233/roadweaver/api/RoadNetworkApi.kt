package net.shiroha233.roadweaver.api

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.shiroha233.roadweaver.generation.RoadGenerationService
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.helpers.StructureConnector
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.planning.PlanningUtils
import net.shiroha233.roadweaver.planning.RoadPlanningService

object RoadNetworkApi {
    @JvmStatic
    fun registerStructureEndpoint(level: ServerLevel?, pos: BlockPos?) {
        registerStructureEndpoint(level, pos, null, false)
    }

    @JvmStatic
    fun registerStructureEndpoint(level: ServerLevel?, pos: BlockPos?, autoConnect: Boolean) {
        registerStructureEndpoint(level, pos, null, autoConnect)
    }

    @JvmStatic
    fun registerStructureEndpoint(
        level: ServerLevel?,
        pos: BlockPos?,
        structureId: String?,
        autoConnect: Boolean
    ) {
        if (level == null || pos == null) return

        val provider = WorldDataProvider.getInstance()
        val existing = provider.getStructureLocations(level)
        val locations: MutableList<BlockPos> = if (existing != null) ArrayList(existing.structureLocations) else ArrayList()
        val infos = if (existing != null) ArrayList(existing.structureInfos) else ArrayList()

        if (!structureId.isNullOrEmpty()) {
            infos.add(Records.StructureInfo(pos, structureId))
            if (!locations.contains(pos)) {
                locations.add(pos)
            }
        } else {
            if (!locations.contains(pos)) {
                locations.add(pos)
            }
        }

        provider.setStructureLocations(level, Records.StructureLocationData(locations, infos))

        if (autoConnect) {
            StructureConnector.cacheNewConnection(level, true)
        }
    }

    @JvmStatic
    fun ensureConnection(level: ServerLevel?, from: BlockPos?, to: BlockPos?) {
        ensureConnection(level, from, to, false)
    }

    @JvmStatic
    fun ensureConnection(level: ServerLevel?, from: BlockPos?, to: BlockPos?, generateImmediately: Boolean) {
        if (level == null || from == null || to == null) return
        if (from == to) return

        val provider = WorldDataProvider.getInstance()
        val existing = provider.getStructureLocations(level)
        val locations: MutableList<BlockPos> = if (existing != null) ArrayList(existing.structureLocations) else ArrayList()
        val infos = if (existing != null) ArrayList(existing.structureInfos) else ArrayList()

        var changed = false
        if (!locations.contains(from)) {
            locations.add(from)
            changed = true
        }
        if (!locations.contains(to)) {
            locations.add(to)
            changed = true
        }
        if (changed) {
            provider.setStructureLocations(level, Records.StructureLocationData(locations, infos))
        }

        val existingConns = provider.getStructureConnections(level)
        val list = if (existingConns != null) ArrayList(existingConns) else ArrayList()
        var exists = false
        for (c in list) {
            if (PlanningUtils.sameEdge(c, from, to)) {
                exists = true
                break
            }
        }

        if (!exists) {
            list.add(Records.StructureConnection(from, to, Records.ConnectionStatus.PLANNED))
            provider.setStructureConnections(level, list)
        }

        if (!generateImmediately) return

        val conn = Records.StructureConnection(from, to, Records.ConnectionStatus.GENERATING)

        // 更新状态为 GENERATING
        run {
            val currentList = provider.getStructureConnections(level)
            val all = if (currentList != null) ArrayList(currentList) else ArrayList()
            var found = false
            for (i in all.indices) {
                if (PlanningUtils.sameEdge(all[i], from, to)) {
                    all[i] = conn
                    found = true
                    break
                }
            }
            if (!found) all.add(conn)
            provider.setStructureConnections(level, all)
        }

        // 执行生成
        val success = RoadGenerationService.generateTask(level, conn)

        // 更新最终状态
        val finalStatus = if (success) Records.ConnectionStatus.COMPLETED else Records.ConnectionStatus.FAILED
        val finalConn = Records.StructureConnection(from, to, finalStatus)

        run {
            val currentList = provider.getStructureConnections(level)
            val all = if (currentList != null) ArrayList(currentList) else ArrayList()
            var found = false
            for (i in all.indices) {
                if (PlanningUtils.sameEdge(all[i], from, to)) {
                    all[i] = finalConn
                    found = true
                    break
                }
            }
            if (!found) all.add(finalConn)
            provider.setStructureConnections(level, all)
        }
    }

    @JvmStatic
    fun planRegion(level: ServerLevel?, minBlockX: Int, minBlockZ: Int, maxBlockX: Int, maxBlockZ: Int) {
        if (level == null) return
        RoadPlanningService.planRectAsync(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ)
    }
}
