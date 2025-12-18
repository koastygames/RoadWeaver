package net.shiroha233.roadweaver.structures.pieces

import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType

/**
 * 模组结构片段类型注册
 *
 * 由 Fabric/NeoForge 平台各自实现注册逻辑，
 * 这里只定义引用。
 */
@Suppress("MemberVisibilityCanBePrivate")
object ModStructurePieceTypes {
    /**
     * 简单模板片段类型
     * 由平台层注册并赋值
     */
    @JvmField
    var SIMPLE_TEMPLATE: StructurePieceType? = null

    /**
     * 设置片段类型（由平台注册时调用）
     */
    @JvmStatic
    fun setSimpleTemplate(type: StructurePieceType) {
        SIMPLE_TEMPLATE = type
    }
}
