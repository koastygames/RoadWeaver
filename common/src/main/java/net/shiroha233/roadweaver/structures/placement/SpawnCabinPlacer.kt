package net.shiroha233.roadweaver.structures.placement

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.Structure
import net.shiroha233.roadweaver.persistence.WorldDataProvider
import net.shiroha233.roadweaver.structures.precompute.PendingStructureStorage
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure

/**
 * 初始小屋放置器
 *
 * 职责：
 * 1. 在世界首开时在出生点附近预计算初始小屋位置
 * 2. 存储到 PendingStructureStorage，让 Beardifier 自动处理地形适应
 * 3. 幂等性检查（避免重复放置）
 */
object SpawnCabinPlacer {
    private val STRUCTURE_ID = ResourceLocation.fromNamespaceAndPath("roadweaver", "spawn_cabin")

    /**
     * 确保初始小屋已放置
     *
     * @return 如果放置了新的小屋返回 true
     */
    @JvmStatic
    fun ensurePlaced(level: ServerLevel?): Boolean {
        if (level == null) return false

        // 获取出生点
        val spawn = level.sharedSpawnPos

        // 幂等性检查：查看世界数据中是否已有结构记录
        val provider = WorldDataProvider.getInstance()
        val locs = provider.getStructureLocations(level)
        if (locs != null && locs.structureLocations.isNotEmpty()) {
            return false
        }

        // 从注册表获取结构定义
        val structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
        val structure: Structure? = structureRegistry[STRUCTURE_ID]

        val spawnCabin = structure as? SpawnCabinStructure ?: return false

        // 计算放置位置
        val y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.x, spawn.z)
        val anchor = BlockPos(spawn.x, y, spawn.z)
        val sizeHint = spawnCabin.sizeHint()

        // 存储到预计算系统，让 Beardifier 自动处理地形适应
        PendingStructureStorage.addPendingStructure(
            level,
            STRUCTURE_ID,
            anchor,
            Rotation.NONE,
            sizeHint.x,
            sizeHint.y,
            sizeHint.z
        )

        // 记录到世界数据（用于幂等性检查）
        provider.addStructureLocation(level, anchor)

        return true
    }
}
