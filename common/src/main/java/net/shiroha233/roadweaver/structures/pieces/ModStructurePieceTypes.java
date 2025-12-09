package net.shiroha233.roadweaver.structures.pieces;

import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * 模组结构片段类型注册
 * 
 * 由 Fabric/NeoForge 平台各自实现注册逻辑，
 * 这里只定义引用。
 */
public final class ModStructurePieceTypes {
    private ModStructurePieceTypes() {}
    
    /**
     * 简单模板片段类型
     * 由平台层注册并赋值
     */
    public static StructurePieceType SIMPLE_TEMPLATE;
    
    /**
     * 设置片段类型（由平台注册时调用）
     */
    public static void setSimpleTemplate(StructurePieceType type) {
        SIMPLE_TEMPLATE = type;
    }
}
