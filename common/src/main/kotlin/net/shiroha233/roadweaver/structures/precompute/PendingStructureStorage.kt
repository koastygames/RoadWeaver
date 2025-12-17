package net.shiroha233.roadweaver.structures.precompute

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Rotation
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * 预计算结构存储服务
 *
 * 存储道路规划阶段预计算的结构位置，供区块生成阶段使用。
 *
 * 工作流程：
 * 1. 道路规划完成后，调用 addPendingStructure() 添加待放置结构
 * 2. 区块生成时（STRUCTURE_STARTS 阶段），Mixin 调用 getPendingStructures() 获取该区块的结构
 * 3. 结构被注入后，调用 markAsInjected() 标记已处理
 */
object PendingStructureStorage {
    // 按维度和区块索引的待放置结构
    // Key: dimension -> chunkKey -> List<PendingRoadsideStructure>
    private val PENDING: ConcurrentHashMap<ResourceLocation, ConcurrentHashMap<Long, MutableList<PendingRoadsideStructure>>> =
        ConcurrentHashMap()

    // 已注入的区块（避免重复注入）- 使用 LRU 限制大小
    private const val MAX_INJECTED_PER_DIM = 4096
    private val INJECTED: ConcurrentHashMap<ResourceLocation, MutableSet<Long>> = ConcurrentHashMap()

    /**
     * 创建带大小限制的已注入区块集合
     */
    private fun createLimitedSet(): MutableSet<Long> {
        val lruMap: MutableMap<Long, Boolean> = Collections.synchronizedMap(
            object : LinkedHashMap<Long, Boolean>(256, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Boolean>): Boolean {
                    return size > MAX_INJECTED_PER_DIM
                }
            }
        )
        return Collections.newSetFromMap(lruMap)
    }

    /**
     * 添加待放置的结构
     */
    @JvmStatic
    fun addPendingStructure(
        level: ServerLevel,
        structureId: ResourceLocation,
        anchor: BlockPos,
        rotation: Rotation,
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int
    ) {
        val dimKey = level.dimension().location()
        val pending = PendingRoadsideStructure(structureId, anchor, rotation, sizeX, sizeY, sizeZ)
        val chunkKey = pending.chunkKey()

        PENDING.computeIfAbsent(dimKey) { ConcurrentHashMap() }
            .computeIfAbsent(chunkKey) { Collections.synchronizedList(mutableListOf()) }
            .add(pending)
    }

    /**
     * 获取指定区块的待放置结构
     */
    @JvmStatic
    fun getPendingStructures(level: ServerLevel, chunkPos: ChunkPos): List<PendingRoadsideStructure> {
        val dimKey = level.dimension().location()
        val chunkKey = (chunkPos.x.toLong() shl 32) or (chunkPos.z.toLong() and 0xFFFFFFFFL)

        // 检查是否已注入
        val injected = INJECTED[dimKey]
        if (injected != null && injected.contains(chunkKey)) {
            return emptyList()
        }

        val dimMap = PENDING[dimKey] ?: return emptyList()
        val structures = dimMap[chunkKey]
        return if (structures != null) ArrayList(structures) else emptyList()
    }

    /**
     * 标记区块已完成结构注入
     */
    @JvmStatic
    fun markAsInjected(level: ServerLevel, chunkPos: ChunkPos) {
        val dimKey = level.dimension().location()
        val chunkKey = (chunkPos.x.toLong() shl 32) or (chunkPos.z.toLong() and 0xFFFFFFFFL)

        INJECTED.computeIfAbsent(dimKey) { createLimitedSet() }.add(chunkKey)

        // 移除已处理的待放置结构（释放内存）
        PENDING[dimKey]?.remove(chunkKey)
    }

    /**
     * 检查区块是否有待放置结构
     */
    @JvmStatic
    fun hasPendingStructures(level: ServerLevel, chunkPos: ChunkPos): Boolean {
        val dimKey = level.dimension().location()
        val chunkKey = (chunkPos.x.toLong() shl 32) or (chunkPos.z.toLong() and 0xFFFFFFFFL)

        // 已注入则无待放置
        val injected = INJECTED[dimKey]
        if (injected != null && injected.contains(chunkKey)) {
            return false
        }

        val dimMap = PENDING[dimKey] ?: return false
        val structures = dimMap[chunkKey]
        return structures != null && structures.isNotEmpty()
    }

    /**
     * 清理指定维度的数据（世界卸载时调用）
     */
    @JvmStatic
    fun clearDimension(dimension: ResourceLocation) {
        PENDING.remove(dimension)
        INJECTED.remove(dimension)
    }

    /**
     * 清理所有数据（服务器关闭时调用）
     */
    @JvmStatic
    fun clearAll() {
        PENDING.clear()
        INJECTED.clear()
    }

    /**
     * 获取待放置结构总数（调试用）
     */
    @JvmStatic
    fun getPendingCount(level: ServerLevel): Int {
        val dimKey = level.dimension().location()
        val dimMap = PENDING[dimKey] ?: return 0
        return dimMap.values.sumOf { it.size }
    }
}
