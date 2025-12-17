package net.shiroha233.roadweaver.client.map.data

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage
import net.shiroha233.roadweaver.planning.RoadPlanningService
import net.shiroha233.roadweaver.search.StructureIndexService
import java.util.ArrayList
import java.util.HashSet

object MapDataCollector {
    // 视图跨度超过该阈值时，不再加载详细道路几何，只依赖连接直线
    private const val MAX_DETAILED_ROAD_SPAN_BLOCKS = 7680

    @JvmStatic
    fun build(level: ServerLevel): MapSnapshot {
        val provider = WorldDataProvider.getInstance()
        val loc = provider.getStructureLocations(level)
        val connections = provider.getStructureConnections(level)

        val structures: MutableList<BlockPos> = if (loc != null) ArrayList(loc.structureLocations) else ArrayList()
        val conns: MutableList<Records.StructureConnection> = if (connections != null) ArrayList(connections) else ArrayList()
        val infos: MutableList<Records.StructureInfo> = if (loc != null) ArrayList(loc.structureInfos) else ArrayList()

        val roads: MutableList<List<BlockPos>> = ArrayList()
        val cfg = ConfigService.get()
        val spawn = level.sharedSpawnPos
        val radiusChunks = kotlin.math.max(1, cfg.initialPlanRadiusChunks())
        val minX = ((spawn.x shr 4) - radiusChunks) * 16
        val maxX = ((spawn.x shr 4) + radiusChunks) * 16
        val minZ = ((spawn.z shr 4) - radiusChunks) * 16
        val maxZ = ((spawn.z shr 4) + radiusChunks) * 16

        val roadDataList = RoadShardStorage.queryRect(level, minX, minZ, maxX, maxZ)
        for (rd in roadDataList) {
            val segs = rd.roadSegmentList
            if (segs.isNullOrEmpty()) continue
            val poly = ArrayList<BlockPos>(segs.size)
            for (sp in segs) poly.add(sp.middlePos)
            if (poly.size >= 2) roads.add(poly)
        }

        if (Level.OVERWORLD == level.dimension()) {
            val verified = StructureIndexService.predictAndVerifyAroundSpawn(level)
            if (verified.isNotEmpty()) {
                val existing = HashSet(structures)
                for (info in verified) {
                    val p = info.pos
                    if (!existing.contains(p)) {
                        structures.add(p)
                        infos.add(info)
                        existing.add(p)
                    }
                }
            }
        }

        return MapSnapshot(structures, conns, infos, roads)
    }

    @JvmStatic
    fun build(level: ServerLevel, minBlockX: Int, minBlockZ: Int, maxBlockX: Int, maxBlockZ: Int): MapSnapshot {
        val provider = WorldDataProvider.getInstance()
        val loc = provider.getStructureLocations(level)
        val connections = provider.getStructureConnections(level)

        val structures = ArrayList<BlockPos>()
        if (loc != null && loc.structureLocations != null) {
            for (p in loc.structureLocations) {
                val x = p.x
                val z = p.z
                if (x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ) structures.add(p)
            }
        }

        val conns = ArrayList<Records.StructureConnection>()
        if (connections != null) {
            for (c in connections) {
                val a = c.from
                val b = c.to
                val ina = a.x >= minBlockX && a.x <= maxBlockX && a.z >= minBlockZ && a.z <= maxBlockZ
                val inb = b.x >= minBlockX && b.x <= maxBlockX && b.z >= minBlockZ && b.z <= maxBlockZ
                if (ina || inb) conns.add(c)
            }
        }

        val infos = ArrayList<Records.StructureInfo>()
        if (loc != null && loc.structureInfos != null) {
            for (info in loc.structureInfos) {
                val p = info.pos
                val x = p.x
                val z = p.z
                if (x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ) infos.add(info)
            }
        }

        if (Level.OVERWORLD == level.dimension()) {
            val verified = StructureIndexService.predictAndVerifyInRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ)
            if (verified.isNotEmpty()) {
                val existing = HashSet(structures)
                for (info in verified) {
                    val p = info.pos
                    if (!existing.contains(p)) {
                        val x = p.x
                        val z = p.z
                        if (x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ) {
                            structures.add(p)
                            infos.add(info)
                            existing.add(p)
                        }
                    }
                }
            }
        }

        val roads: MutableList<List<BlockPos>> = ArrayList()
        val roadDataList = RoadShardStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ)
        for (rd in roadDataList) {
            val segs = rd.roadSegmentList
            if (segs.isNullOrEmpty()) continue
            val poly = ArrayList<BlockPos>(segs.size)
            for (sp in segs) {
                val p = sp.middlePos
                val x = p.x
                val z = p.z
                if (x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ) poly.add(p)
            }
            if (poly.size >= 2) roads.add(poly)
        }

        return MapSnapshot(structures, conns, infos, roads)
    }

    @JvmStatic
    fun build(
        level: ServerLevel,
        minBlockX: Int,
        minBlockZ: Int,
        maxBlockX: Int,
        maxBlockZ: Int,
        centerX: Int,
        centerZ: Int,
        radiusBlocks: Int
    ): MapSnapshot {
        val provider = WorldDataProvider.getInstance()
        val loc = provider.getStructureLocations(level)
        val connections = provider.getStructureConnections(level)

        val r = kotlin.math.max(0, radiusBlocks)
        val r2 = r.toLong() * r.toLong()
        val inAOI: (Int, Int) -> Boolean = { x, z ->
            if (r2 <= 0) true
            else {
                val dx = (x - centerX).toLong()
                val dz = (z - centerZ).toLong()
                dx * dx + dz * dz <= r2
            }
        }

        val spanX = kotlin.math.abs(maxBlockX - minBlockX)
        val spanZ = kotlin.math.abs(maxBlockZ - minBlockZ)

        // 先构建“已规划端点”集合（任何状态）
        val plannedEndpoints = HashSet<BlockPos>()
        if (connections != null) {
            for (c in connections) {
                plannedEndpoints.add(c.from)
                plannedEndpoints.add(c.to)
            }
        }

        // 计算“已触发规划覆盖”判断
        val cfgAll = ConfigService.get()
        val initialRadiusBlocks = kotlin.math.max(1, cfgAll.initialPlanRadiusChunks()) * 16
        val strideChunks = RoadPlanningService.getStrideTileSizeChunks()
        val dynRadiusChunks = RoadPlanningService.getDynamicPlanRadiusChunks()
        val dynRadiusBlocks = kotlin.math.max(1, dynRadiusChunks) * 16
        val initialR2 = initialRadiusBlocks.toLong() * initialRadiusBlocks.toLong()
        val spawn = level.sharedSpawnPos

        // 预计算已规划 tiles 对应的近似矩形（块坐标）
        val plannedRects = ArrayList<IntArray>()
        val centersMap = RoadPlanningService.getPlannedTileCenters(level)
        for (key in RoadPlanningService.getPlannedTiles(level)) {
            val kx = (key shr 32).toInt()
            val kz = (key and 0xffffffffL).toInt()

            val cval = centersMap[key]
            val centerChunkX: Int
            val centerChunkZ: Int
            if (cval != null) {
                centerChunkX = (cval shr 32).toInt()
                centerChunkZ = (cval and 0xffffffffL).toInt()
            } else {
                centerChunkX = kx * strideChunks + strideChunks / 2
                centerChunkZ = kz * strideChunks + strideChunks / 2
            }

            val cxBlocks = centerChunkX * 16
            val czBlocks = centerChunkZ * 16
            plannedRects.add(
                intArrayOf(
                    cxBlocks - dynRadiusBlocks,
                    czBlocks - dynRadiusBlocks,
                    cxBlocks + dynRadiusBlocks,
                    czBlocks + dynRadiusBlocks
                )
            )
        }

        val inPlannedCoverage: (Int, Int) -> Boolean = { x, z ->
            var ok = false
            if (!ok && Level.OVERWORLD == level.dimension()) {
                val dxs = (x - spawn.x).toLong()
                val dzs = (z - spawn.z).toLong()
                if (dxs * dxs + dzs * dzs <= initialR2) ok = true
            }
            if (!ok) {
                for (rct in plannedRects) {
                    if (x >= rct[0] && x <= rct[2] && z >= rct[1] && z <= rct[3]) {
                        ok = true
                        break
                    }
                }
            }
            ok
        }

        val structures = ArrayList<BlockPos>()
        if (loc != null && loc.structureLocations != null) {
            for (p in loc.structureLocations) {
                val x = p.x
                val z = p.z
                val inRect = x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ
                if (!inRect) continue

                // 规则：已规划端点始终显示；已触发规划覆盖内的结构显示；其余仅在 AOI 内显示
                if (plannedEndpoints.contains(p) || inPlannedCoverage(x, z) || inAOI(x, z)) {
                    structures.add(p)
                }
            }
        }

        // 确保连接端点一定出现在结构点列表中
        if (connections != null) {
            val existing = HashSet(structures)
            for (c in connections) {
                val eps = arrayOf(c.from, c.to)
                for (ep in eps) {
                    val x = ep.x
                    val z = ep.z
                    val inRect = x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ
                    if (inRect && !existing.contains(ep)) {
                        structures.add(ep)
                        existing.add(ep)
                    }
                }
            }
        }

        val conns = ArrayList<Records.StructureConnection>()
        if (connections != null) {
            for (c in connections) {
                val a = c.from
                val b = c.to
                val ina = a.x >= minBlockX && a.x <= maxBlockX && a.z >= minBlockZ && a.z <= maxBlockZ
                val inb = b.x >= minBlockX && b.x <= maxBlockX && b.z >= minBlockZ && b.z <= maxBlockZ
                // 规则：连接按矩形过滤，不受 AOI 限制
                if (ina || inb) conns.add(c)
            }
        }

        val infos = ArrayList<Records.StructureInfo>()
        if (loc != null && loc.structureInfos != null) {
            for (info in loc.structureInfos) {
                val p = info.pos
                val x = p.x
                val z = p.z
                val inRect = x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ
                if (!inRect) continue

                if (plannedEndpoints.contains(p) || inPlannedCoverage(x, z) || inAOI(x, z)) {
                    infos.add(info)
                }
            }
        }

        val roads: MutableList<List<BlockPos>> = ArrayList()

        // 视图跨度太大时跳过道路几何的加载，只用连接直线表示已连接道路
        val loadDetailedRoads = spanX <= MAX_DETAILED_ROAD_SPAN_BLOCKS && spanZ <= MAX_DETAILED_ROAD_SPAN_BLOCKS
        if (loadDetailedRoads) {
            val roadDataList = RoadShardStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ)
            for (rd in roadDataList) {
                val segs = rd.roadSegmentList
                if (segs.isNullOrEmpty()) continue

                val poly = ArrayList<BlockPos>(segs.size)
                for (sp in segs) {
                    val p = sp.middlePos
                    val x = p.x
                    val z = p.z
                    if (x >= minBlockX && x <= maxBlockX && z >= minBlockZ && z <= maxBlockZ) {
                        poly.add(p)
                    }
                }

                if (poly.size >= 2) roads.add(poly)
            }
        }

        return MapSnapshot(structures, conns, infos, roads)
    }
}
