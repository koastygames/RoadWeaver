package net.shiroha233.roadweaver.client.map.data

import net.minecraft.core.BlockPos
import net.shiroha233.roadweaver.helpers.Records

class MapSnapshot(
    structures: List<BlockPos>?,
    connections: List<Records.StructureConnection>?,
    structureInfos: List<Records.StructureInfo>?,
    roadPolylines: List<List<BlockPos>>?
) {
    private val structures: List<BlockPos> = java.util.List.copyOf(structures ?: emptyList())
    private val connections: List<Records.StructureConnection> = java.util.List.copyOf(connections ?: emptyList())
    private val structureNames: Map<BlockPos, String>
    private val roadPolylines: List<List<BlockPos>>

    private val minX: Int
    private val minZ: Int
    private val maxX: Int
    private val maxZ: Int

    init {
        val nm = HashMap<BlockPos, String>()
        if (structureInfos != null) {
            for (info in structureInfos) nm[info.pos] = info.structureId
        }
        structureNames = java.util.Map.copyOf(nm)

        val rp = ArrayList<List<BlockPos>>()
        if (roadPolylines != null) {
            for (pl in roadPolylines) {
                rp.add(java.util.List.copyOf(pl))
            }
        }
        this.roadPolylines = java.util.List.copyOf(rp)

        var minX0 = Int.MAX_VALUE
        var minZ0 = Int.MAX_VALUE
        var maxX0 = Int.MIN_VALUE
        var maxZ0 = Int.MIN_VALUE

        for (p in this.structures) {
            if (p.x < minX0) minX0 = p.x
            if (p.z < minZ0) minZ0 = p.z
            if (p.x > maxX0) maxX0 = p.x
            if (p.z > maxZ0) maxZ0 = p.z
        }
        for (c in this.connections) {
            val a = c.from
            val b = c.to
            if (a.x < minX0) minX0 = a.x
            if (a.z < minZ0) minZ0 = a.z
            if (a.x > maxX0) maxX0 = a.x
            if (a.z > maxZ0) maxZ0 = a.z
            if (b.x < minX0) minX0 = b.x
            if (b.z < minZ0) minZ0 = b.z
            if (b.x > maxX0) maxX0 = b.x
            if (b.z > maxZ0) maxZ0 = b.z
        }
        for (pl in this.roadPolylines) {
            for (p in pl) {
                if (p.x < minX0) minX0 = p.x
                if (p.z < minZ0) minZ0 = p.z
                if (p.x > maxX0) maxX0 = p.x
                if (p.z > maxZ0) maxZ0 = p.z
            }
        }
        if (minX0 == Int.MAX_VALUE) {
            minX0 = 0
            minZ0 = 0
            maxX0 = 1
            maxZ0 = 1
        }

        minX = minX0
        minZ = minZ0
        maxX = maxX0
        maxZ = maxZ0
    }

    fun structures(): List<BlockPos> = structures
    fun connections(): List<Records.StructureConnection> = connections
    fun structureName(pos: BlockPos): String? = structureNames[pos]
    fun roadPolylines(): List<List<BlockPos>> = roadPolylines

    fun minX(): Int = minX
    fun minZ(): Int = minZ
    fun maxX(): Int = maxX
    fun maxZ(): Int = maxZ

    fun structuresCount(): Int = structures.size

    fun plannedCount(): Int = connections.count { it.status == Records.ConnectionStatus.PLANNED }
    fun generatingCount(): Int = connections.count { it.status == Records.ConnectionStatus.GENERATING }
    fun completedCount(): Int = connections.count { it.status == Records.ConnectionStatus.COMPLETED }
    fun failedCount(): Int = connections.count { it.status == Records.ConnectionStatus.FAILED }

    companion object {
        @JvmStatic
        fun empty(): MapSnapshot {
            return MapSnapshot(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }
}
