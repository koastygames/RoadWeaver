package net.shiroha233.roadweaver.structures.registry

import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.levelgen.structure.Structure
import net.shiroha233.roadweaver.structures.data.BiomeCategory
import net.shiroha233.roadweaver.structures.types.RoadsideStructure
import java.util.concurrent.ConcurrentHashMap

// 路边结构注册中心
//
// 从世界的 Structure 注册表中读取所有 RoadsideStructure，
// 提供根据条件筛选和选择结构的方法。
//
// 数据来源：datapack 中的 worldgen/structure/*.json
object RoadsideStructureRegistry {
    // 缓存：每个世界的路边结构列表
    private val CACHE: MutableMap<ResourceKey<*>, List<RoadsideStructureEntry>> = ConcurrentHashMap()

    /**
     * 结构条目，包含 Holder 引用和解析后的结构
     */
    data class RoadsideStructureEntry(
        val id: ResourceLocation,
        val holder: Holder<Structure>,
        val structure: RoadsideStructure
    )

    /**
     * 获取所有已注册的路边结构
     */
    @JvmStatic
    fun getAll(level: ServerLevel): List<RoadsideStructureEntry> {
        val dimensionKey: ResourceKey<*> = level.dimension()
        return CACHE.computeIfAbsent(dimensionKey) { loadFromRegistry(level.registryAccess()) }
    }

    /**
     * 根据条件选择一个路边结构
     *
     * @return 选中的结构，如果没有符合条件的返回 null
     */
    @JvmStatic
    fun choose(level: ServerLevel, biome: BiomeCategory, roadLength: Int, random: RandomSource): RoadsideStructureEntry? {
        val all = getAll(level)
        val candidates = ArrayList<RoadsideStructureEntry>()
        var totalWeight = 0

        for (entry in all) {
            val structure = entry.structure

            // 群系过滤
            if (!structure.placementRule().isBiomeAllowed(biome)) {
                continue
            }

            // 道路长度过滤
            if (!structure.placementRule().isRoadLongEnough(roadLength)) {
                continue
            }

            val weight = structure.weight()
            if (weight <= 0) {
                continue
            }

            candidates.add(entry)
            totalWeight += weight
        }

        if (candidates.isEmpty() || totalWeight <= 0) {
            return null
        }

        // 权重随机选择
        val roll = random.nextInt(totalWeight)
        var sum = 0
        for (entry in candidates) {
            sum += entry.structure.weight()
            if (roll < sum) {
                return entry
            }
        }

        return candidates[0]
    }

    /**
     * 从注册表加载所有路边结构
     */
    private fun loadFromRegistry(registryAccess: RegistryAccess): List<RoadsideStructureEntry> {
        val result = ArrayList<RoadsideStructureEntry>()
        val structureRegistry: Registry<Structure> = registryAccess.registryOrThrow(Registries.STRUCTURE)

        for (entry in structureRegistry.entrySet()) {
            val id = entry.key.location()
            val structure = entry.value

            // 只收集 RoadsideStructure 类型
            val roadsideStructure = structure as? RoadsideStructure ?: continue
            val holder = structureRegistry.getHolderOrThrow(entry.key)
            result.add(RoadsideStructureEntry(id, holder, roadsideStructure))
        }

        return result
    }

    /**
     * 清除缓存（在世界卸载或重载时调用）
     */
    @JvmStatic
    fun clearCache() {
        CACHE.clear()
    }

    /**
     * 清除指定维度的缓存
     */
    @JvmStatic
    fun clearCache(dimensionKey: ResourceKey<*>) {
        CACHE.remove(dimensionKey)
    }
}
