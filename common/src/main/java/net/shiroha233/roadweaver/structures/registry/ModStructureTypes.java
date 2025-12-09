package net.shiroha233.roadweaver.structures.registry;

import net.minecraft.world.level.levelgen.structure.StructureType;
import net.shiroha233.roadweaver.structures.types.RoadsideStructure;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;

/**
 * 模组结构类型注册
 * 
 * 由 Fabric/NeoForge 平台各自实现注册逻辑，
 * 这里只定义引用。
 */
public final class ModStructureTypes {
    private ModStructureTypes() {}
    
    /**
     * 路边结构类型
     */
    public static StructureType<RoadsideStructure> ROADSIDE;
    
    /**
     * 初始小屋结构类型
     */
    public static StructureType<SpawnCabinStructure> SPAWN_CABIN;
    
    /**
     * 设置路边结构类型（由平台注册时调用）
     */
    public static void setRoadside(StructureType<RoadsideStructure> type) {
        ROADSIDE = type;
    }
    
    /**
     * 设置初始小屋结构类型（由平台注册时调用）
     */
    public static void setSpawnCabin(StructureType<SpawnCabinStructure> type) {
        SPAWN_CABIN = type;
    }
}
