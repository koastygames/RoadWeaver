package net.shiroha233.roadweaver.persistence

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.WorldGenRegion
import net.minecraft.world.level.WorldGenLevel
import net.shiroha233.roadweaver.config.ConfigService
import net.shiroha233.roadweaver.helpers.Records
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 道路空间索引，使用网格划分实现高效的空间查询。
 */
object RoadSpatialIndex {
    private const val GRID_SIZE = 8
    private const val GRID_SHIFT = 3

    private const val MAX_CACHED_CHUNKS_PER_DIM = 512

    private val CHUNK_INDEX: MutableMap<String, MutableMap<Long, ChunkGridIndex>> = ConcurrentHashMap()

    private fun createLRUCache(): MutableMap<Long, ChunkGridIndex> {
        return Collections.synchronizedMap(
            object : LinkedHashMap<Long, ChunkGridIndex>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ChunkGridIndex>): Boolean {
                    return size > MAX_CACHED_CHUNKS_PER_DIM
                }
            }
        )
    }

    private const val Y_CHECK_ABOVE = 12
    private const val Y_CHECK_BELOW = 2

    @JvmStatic
    fun isNearRoad(level: WorldGenLevel, pos: BlockPos): Boolean {
        val serverLevel = extractServerLevel(level)
        return serverLevel != null && isNearRoadServer(serverLevel, pos)
    }

    @JvmStatic
    fun isNearRoadServer(level: ServerLevel, pos: BlockPos): Boolean {
        if (!ConfigService.get().preventTreesOnRoad()) return false

        val margin = (ConfigService.get().roadWidth() / 2) + 1
        return isNearRoadInternal(level, pos, margin, Y_CHECK_ABOVE, Y_CHECK_BELOW)
    }

    private fun isNearRoadInternal(level: ServerLevel, pos: BlockPos, margin: Int, yAbove: Int, yBelow: Int): Boolean {
        val cx = pos.x shr 4
        val cz = pos.z shr 4
        val dimKey = dimKey(level)

        val dimIndex = CHUNK_INDEX.computeIfAbsent(dimKey) { createLRUCache() }
        val chunkKey = chunkKey(cx, cz)

        var gridIndex = dimIndex[chunkKey]
        if (gridIndex == null) {
            gridIndex = buildChunkGridIndex(level, cx, cz)
            dimIndex[chunkKey] = gridIndex
        }

        if (gridIndex.isEmpty()) return false

        val px = pos.x
        val py = pos.y
        val pz = pos.z
        val gridX = px shr GRID_SHIFT
        val gridZ = pz shr GRID_SHIFT

        val gridRadius = (margin shr GRID_SHIFT) + 1

        for (dx in -gridRadius..gridRadius) {
            for (dz in -gridRadius..gridRadius) {
                val gk = gridKey(gridX + dx, gridZ + dz)
                val points = gridIndex.getPoints(gk)
                if (points.isNullOrEmpty()) continue

                for (packed in points) {
                    val road = BlockPos.of(packed)
                    val rdx = abs(px - road.x)
                    val rdz = abs(pz - road.z)
                    if (rdx <= margin && rdz <= margin) {
                        val yDiff = py - road.y
                        if (yDiff in -yBelow..yAbove) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    private fun buildChunkGridIndex(level: ServerLevel, cx: Int, cz: Int): ChunkGridIndex {
        val minX = cx shl 4
        val minZ = cz shl 4
        val maxX = minX + 15
        val maxZ = minZ + 15

        val roads = RoadShardStorage.queryRect(level, minX - GRID_SIZE, minZ - GRID_SIZE, maxX + GRID_SIZE, maxZ + GRID_SIZE)
        if (roads.isEmpty()) {
            return ChunkGridIndex.EMPTY
        }

        val index = ChunkGridIndex()
        for (rd in roads) {
            val segs = rd.roadSegmentList
            for (seg in segs) {
                addToIndex(index, seg.middlePos, minX, minZ, maxX, maxZ)
                for (p in seg.positions) {
                    addToIndex(index, p, minX, minZ, maxX, maxZ)
                }
            }
        }

        return if (index.isEmpty()) ChunkGridIndex.EMPTY else index
    }

    private fun addToIndex(index: ChunkGridIndex, p: BlockPos?, minX: Int, minZ: Int, maxX: Int, maxZ: Int) {
        if (p == null) return
        val x = p.x
        val z = p.z
        if (x in (minX - GRID_SIZE)..(maxX + GRID_SIZE) && z in (minZ - GRID_SIZE)..(maxZ + GRID_SIZE)) {
            val gridX = x shr GRID_SHIFT
            val gridZ = z shr GRID_SHIFT
            val gk = gridKey(gridX, gridZ)
            index.addPoint(gk, p.asLong())
        }
    }

    @Suppress("DEPRECATION")
    private fun extractServerLevel(level: WorldGenLevel): ServerLevel? {
        return when (level) {
            is ServerLevel -> level
            is WorldGenRegion -> level.level
            else -> null
        }
    }

    private fun chunkKey(cx: Int, cz: Int): Long {
        return (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
    }

    private fun gridKey(gx: Int, gz: Int): Long {
        return (gx.toLong() shl 32) or (gz.toLong() and 0xFFFFFFFFL)
    }

    private fun dimKey(level: ServerLevel): String {
        return level.dimension().location().toString()
    }

    @JvmStatic
    fun clearCache(level: ServerLevel?) {
        if (level != null) {
            CHUNK_INDEX.remove(dimKey(level))
        }
    }

    @JvmStatic
    fun clearAllCache() {
        CHUNK_INDEX.clear()
    }

    @JvmStatic
    fun invalidateChunk(level: ServerLevel?, cx: Int, cz: Int) {
        if (level == null) return
        val dimIndex = CHUNK_INDEX[dimKey(level)]
        dimIndex?.remove(chunkKey(cx, cz))
    }

    private class ChunkGridIndex {
        companion object {
            val EMPTY: ChunkGridIndex = ChunkGridIndex()
        }

        private val grids: MutableMap<Long, MutableSet<Long>> = HashMap()

        fun addPoint(gridKey: Long, packedPos: Long) {
            grids.computeIfAbsent(gridKey) { HashSet() }.add(packedPos)
        }

        fun getPoints(gridKey: Long): Set<Long>? {
            return grids[gridKey]
        }

        fun isEmpty(): Boolean {
            return grids.isEmpty()
        }
    }
}
