package net.shiroha233.roadweaver.structures.registry

import net.minecraft.world.level.levelgen.structure.StructureType
import net.shiroha233.roadweaver.structures.types.RoadsideStructure
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure

/**
 * 模组结构类型注册
 *
 * 由 Fabric/NeoForge 平台各自实现注册逻辑，
 * 这里只定义引用。
 */
@Suppress("MemberVisibilityCanBePrivate")
object ModStructureTypes {
    /**
     * 路边结构类型
     */
    @JvmField
    var ROADSIDE: StructureType<RoadsideStructure>? = null

    /**
     * 初始小屋结构类型
     */
    @JvmField
    var SPAWN_CABIN: StructureType<SpawnCabinStructure>? = null

    /**
     * 设置路边结构类型（由平台注册时调用）
     */
    @JvmStatic
    fun setRoadside(type: StructureType<RoadsideStructure>) {
        ROADSIDE = type
    }

    /**
     * 设置初始小屋结构类型（由平台注册时调用）
     */
    @JvmStatic
    fun setSpawnCabin(type: StructureType<SpawnCabinStructure>) {
        SPAWN_CABIN = type
    }
}
