package net.shiroha233.roadweaver.planning

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.shiroha233.roadweaver.client.map.data.MapDataCollector
import net.shiroha233.roadweaver.client.map.data.MapSnapshot
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.config.ModConfig
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.runtime.ThreadPoolManager
import net.shiroha233.roadweaver.util.ComputeService
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.concurrent.CompletableFuture

object RoadPlanningService {
    private const val MAX_PLANNED_KEYS = 200_000

    private fun prunePlannedIfTooLarge(level: Level) {
        val provider = WorldDataProvider.getInstance()

        val keys = HashSet(provider.getPlannedTileKeys(level as ServerLevel) ?: emptySet())
        if (keys.size > MAX_PLANNED_KEYS) {
            var remove = keys.size - MAX_PLANNED_KEYS
            val it = keys.iterator()
            while (remove > 0 && it.hasNext()) {
                it.next()
                it.remove()
                remove--
            }
            provider.setPlannedTileKeys(level, keys)
        }

        val centers = HashMap(provider.getPlannedTileCenters(level) ?: emptyMap())
        if (centers.size > MAX_PLANNED_KEYS) {
            var remove2 = centers.size - MAX_PLANNED_KEYS
            val it2 = centers.keys.iterator()
            while (remove2 > 0 && it2.hasNext()) {
                it2.next()
                it2.remove()
                remove2--
            }
            provider.setPlannedTileCenters(level, centers)
        }
    }

    @JvmStatic
    fun initialPlan(level: ServerLevel) {
        if (Level.OVERWORLD !== level.dimension()) return
        val cfg = ConfigService.get()
        val radiusChunks = maxOf(1, cfg.initialPlanRadiusChunks())

        val spawn = level.sharedSpawnPos
        val cx = spawn.x shr 4
        val cz = spawn.z shr 4

        val minX = (cx - radiusChunks) * 16
        val maxX = (cx + radiusChunks) * 16
        val minZ = (cz - radiusChunks) * 16
        val maxZ = (cz + radiusChunks) * 16

        planRect(level, minX, minZ, maxX, maxZ)
    }

    @JvmStatic
    fun planAroundPlayer(player: ServerPlayer?) {
        if (player === null) return
        val level = player.serverLevel()
        if (Level.OVERWORLD !== level.dimension()) return

        val cfg = ConfigService.get()
        if (!cfg.dynamicPlanEnabled()) return

        val radiusChunks = maxOf(1, cfg.dynamicPlanRadiusChunks())
        val stride = maxOf(1, cfg.dynamicPlanStrideChunks())
        val tile = maxOf(8, minOf(256, stride))

        val pcx = player.chunkPosition().x
        val pcz = player.chunkPosition().z
        val kx = Math.floorDiv(pcx, tile)
        val kz = Math.floorDiv(pcz, tile)
        val key = (kx.toLong() shl 32) xor (kz.toLong() and 0xffffffffL)

        val provider0 = WorldDataProvider.getInstance()
        val set = HashSet(provider0.getPlannedTileKeys(level))
        val isNewTile = set.add(key)

        val centers = HashMap(provider0.getPlannedTileCenters(level))
        centers.putIfAbsent(key, (pcx.toLong() shl 32) xor (pcz.toLong() and 0xffffffffL))

        provider0.setPlannedTileKeys(level, set)
        provider0.setPlannedTileCenters(level, centers)

        if (!isNewTile) return

        val minX = (pcx - radiusChunks) * 16
        val maxX = (pcx + radiusChunks) * 16
        val minZ = (pcz - radiusChunks) * 16
        val maxZ = (pcz + radiusChunks) * 16

        prunePlannedIfTooLarge(level)
        planRectAsync(level, minX, minZ, maxX, maxZ)
    }

    private fun planRect(level: ServerLevel, minBlockX: Int, minBlockZ: Int, maxBlockX: Int, maxBlockZ: Int) {
        val snap = MapDataCollector.build(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ)

        val points = ArrayList<BlockPos>()
        val seenPos = HashSet<Long>()
        for (p in snap.structures()) {
            val q = BlockPos(p.x, 0, p.z)
            val key = PlanningUtils.pos2dKey(q)
            if (seenPos.add(key)) points.add(q)
        }
        if (points.size < 2) return

        val primaryEdges: List<Records.StructureConnection>
        val cfg0 = ConfigService.get()
        primaryEdges = when (cfg0.planningAlgorithm()) {
            ModConfig.PlanningAlgorithm.DELAUNAY -> DelaunayPlanner.planDelaunay(points, 2048)
            ModConfig.PlanningAlgorithm.RNG -> RNGPlanner.planRNG(points, 2048)
            ModConfig.PlanningAlgorithm.MST -> MSTPlanner.planMST(points, 2048)
            else -> KNNPlanner.planKNN(points, 2, 2048, 1.8, 40.0, 2)
        }

        if (primaryEdges.isEmpty()) return

        val provider = WorldDataProvider.getInstance()
        val existing: List<Records.StructureConnection>? = provider.getStructureConnections(level)

        val inRect = HashSet(points)
        val existingInRect = ArrayList<Records.StructureConnection>()
        if (existing !== null) {
            for (c in existing) {
                if (inRect.contains(c.from) && inRect.contains(c.to)) existingInRect.add(c)
            }
        }

        val base = ArrayList(existingInRect)
        base.addAll(primaryEdges)

        val bridges = KNNPlanner.connectComponents(points, base, 1536, 35.0, 3)

        val incoming = ArrayList(primaryEdges)
        incoming.addAll(bridges)

        val merged = mergeConnections(existing, incoming)
        if (merged.size != (existing?.size ?: 0)) {
            provider.setStructureConnections(level, merged)
        }
    }

    @JvmStatic
    fun initialPlanAsync(level: ServerLevel): CompletableFuture<Void> {
        if (Level.OVERWORLD !== level.dimension()) return CompletableFuture.completedFuture(null)

        val cfg = ConfigService.get()
        val radiusChunks = maxOf(1, cfg.initialPlanRadiusChunks())

        val spawn = level.sharedSpawnPos
        val cx = spawn.x shr 4
        val cz = spawn.z shr 4

        val minX = (cx - radiusChunks) * 16
        val maxX = (cx + radiusChunks) * 16
        val minZ = (cz - radiusChunks) * 16
        val maxZ = (cz + radiusChunks) * 16

        return planRectAsync(level, minX, minZ, maxX, maxZ)
    }

    @JvmStatic
    fun planRectAsync(level: ServerLevel, minBlockX: Int, minBlockZ: Int, maxBlockX: Int, maxBlockZ: Int): CompletableFuture<Void> {
        val epoch = ThreadPoolManager.currentEpoch()

        val existingSnapshot: List<Records.StructureConnection> = run {
            val prov = WorldDataProvider.getInstance()
            val ex = prov.getStructureConnections(level)
            if (ex !== null) ArrayList(ex) else ArrayList()
        }

        return ComputeService.supplyAsync {
            if (Thread.currentThread().isInterrupted) return@supplyAsync ArrayList<Records.StructureConnection>()
            if (!ThreadPoolManager.isEpoch(epoch)) return@supplyAsync ArrayList<Records.StructureConnection>()

            val snap = MapDataCollector.build(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ)

            val points = ArrayList<BlockPos>()
            val seenPos = HashSet<Long>()
            for (p in snap.structures()) {
                val q = BlockPos(p.x, 0, p.z)
                val key = PlanningUtils.pos2dKey(q)
                if (seenPos.add(key)) points.add(q)
            }

            for (c in existingSnapshot) {
                val f = BlockPos(c.from.x, 0, c.from.z)
                val t = BlockPos(c.to.x, 0, c.to.z)
                val kf = PlanningUtils.pos2dKey(f)
                val kt = PlanningUtils.pos2dKey(t)
                if (seenPos.add(kf)) points.add(f)
                if (seenPos.add(kt)) points.add(t)
            }

            if (points.size < 2) return@supplyAsync ArrayList<Records.StructureConnection>()

            val inRect = HashSet(points)
            val existingInRect = ArrayList<Records.StructureConnection>()
            val existingEdgeKeys = HashSet<Long>()

            for (c in existingSnapshot) {
                if (
                    inRect.contains(BlockPos(c.from.x, 0, c.from.z)) &&
                    inRect.contains(BlockPos(c.to.x, 0, c.to.z))
                ) {
                    existingInRect.add(c)
                    existingEdgeKeys.add(PlanningUtils.edgeKey(c.from, c.to))
                }
            }

            val primaryEdges: List<Records.StructureConnection>
            val cfg0 = ConfigService.get()
            primaryEdges = when (cfg0.planningAlgorithm()) {
                ModConfig.PlanningAlgorithm.DELAUNAY -> DelaunayPlanner.planDelaunay(points, 2048)
                ModConfig.PlanningAlgorithm.RNG -> RNGPlanner.planRNG(points, 2048)
                ModConfig.PlanningAlgorithm.MST -> MSTPlanner.planMST(points, 2048)
                else -> KNNPlanner.planKNN(points, 2, 2048, 1.8, 40.0, 2)
            }

            if (primaryEdges.isEmpty() && existingInRect.isEmpty()) return@supplyAsync ArrayList<Records.StructureConnection>()

            val filteredPrimary = ArrayList<Records.StructureConnection>()
            for (c in primaryEdges) {
                val ek = PlanningUtils.edgeKey(c.from, c.to)
                if (!existingEdgeKeys.contains(ek)) filteredPrimary.add(c)
            }

            val base = ArrayList(existingInRect)
            base.addAll(filteredPrimary)

            val bridges = KNNPlanner.connectComponents(points, base, 1536, 35.0, 3)

            val incoming = ArrayList(filteredPrimary)
            incoming.addAll(bridges)
            incoming
        }.thenAccept { incoming ->
            if (incoming === null || incoming.isEmpty()) return@thenAccept
            if (!ThreadPoolManager.isEpoch(epoch)) return@thenAccept

            val server = level.server ?: return@thenAccept
            server.execute {
                if (!ThreadPoolManager.isEpoch(epoch)) return@execute

                val provider = WorldDataProvider.getInstance()
                val existing = provider.getStructureConnections(level)
                val merged = mergeConnections(existing, incoming)

                if (merged.size != (existing?.size ?: 0)) {
                    provider.setStructureConnections(level, merged)
                }
            }
        }
    }

    private fun mergeConnections(
        existing: List<Records.StructureConnection>?,
        incoming: List<Records.StructureConnection>
    ): List<Records.StructureConnection> {
        val seen = HashSet<Long>()
        val out = ArrayList<Records.StructureConnection>()

        if (existing !== null) {
            for (c in existing) {
                val k = PlanningUtils.edgeKey(c.from, c.to)
                if (seen.add(k)) out.add(c)
            }
        }

        for (c in incoming) {
            val k = PlanningUtils.edgeKey(c.from, c.to)
            if (seen.add(k)) out.add(Records.StructureConnection(c.from, c.to, Records.ConnectionStatus.PLANNED))
        }

        return out
    }

    @JvmStatic
    fun getPlannedTiles(level: ServerLevel): Set<Long> {
        val s: Set<Long>? = WorldDataProvider.getInstance().getPlannedTileKeys(level)
        return if (s === null) java.util.Set.of() else java.util.Set.copyOf(s)
    }

    @JvmStatic
    fun getPlannedTileCenters(level: ServerLevel): Map<Long, Long> {
        val m: Map<Long, Long>? = WorldDataProvider.getInstance().getPlannedTileCenters(level)
        if (m === null || m.isEmpty()) return java.util.Map.of()
        return java.util.Map.copyOf(m)
    }

    @JvmStatic
    fun getStrideTileSizeChunks(): Int {
        val cfg = ConfigService.get()
        val stride = maxOf(1, cfg.dynamicPlanStrideChunks())
        return maxOf(8, minOf(256, stride))
    }

    @JvmStatic
    fun getDynamicPlanRadiusChunks(): Int {
        val cfg = ConfigService.get()
        return maxOf(1, cfg.dynamicPlanRadiusChunks())
    }

    @JvmStatic
    fun resetAll() {
        // 数据存储在 WorldDataProvider 中，无需清理本地缓存
    }
}
