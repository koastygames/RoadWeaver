package net.shiroha233.roadweaver.structures.pieces;

import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * 模组结构片段类型注册
 * 职责：持有平台层注册的结构片段类型引用
 */
public final class ModStructurePieceTypes {
    private ModStructurePieceTypes() {}
    
    public static StructurePieceType SIMPLE_TEMPLATE;
    
    public static void setSimpleTemplate(StructurePieceType type) {
        SIMPLE_TEMPLATE = type;
    }
}
