package net.shiroha233.roadweaver.helpers

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import java.util.ArrayDeque
import java.util.Queue

object StructureConnector {
    private val CACHED: Queue<Records.StructureConnection> = ArrayDeque()

    @JvmStatic
    fun cachedStructureConnections(): Queue<Records.StructureConnection> {
        return CACHED
    }

    @JvmStatic
    fun cacheNewConnection(level: ServerLevel, locateAtPlayerIgnored: Boolean) {
        val provider = WorldDataProvider.getInstance()
        val data = provider.getStructureLocations(level)
        val list = data.structureLocations
        if (list.size < 2) return
        createNewStructureConnection(level)
    }

    private fun createNewStructureConnection(level: ServerLevel) {
        val provider = WorldDataProvider.getInstance()
        val data = provider.getStructureLocations(level)
        val all = data.structureLocations
        if (all.size < 2) return

        val latest = all[all.size - 1]
        val closest = findClosest(latest, all) ?: return

        val connections = ArrayList(provider.getStructureConnections(level))
        if (!exists(connections, latest, closest)) {
            val c = Records.StructureConnection(latest, closest, Records.ConnectionStatus.PLANNED)
            connections.add(c)
            provider.setStructureConnections(level, connections)
            CACHED.add(c)
        }
    }

    private fun exists(existing: List<Records.StructureConnection>, a: BlockPos, b: BlockPos): Boolean {
        for (c in existing) {
            if ((c.from.equals(a) && c.to.equals(b)) || (c.from.equals(b) && c.to.equals(a))) return true
        }
        return false
    }

    private fun findClosest(cur: BlockPos, all: List<BlockPos>): BlockPos? {
        var best: BlockPos? = null
        var min = Double.MAX_VALUE
        for (p in all) {
            if (p.equals(cur)) continue
            val d = cur.distSqr(p)
            if (d < min) {
                min = d
                best = p
            }
        }
        return best
    }
}
