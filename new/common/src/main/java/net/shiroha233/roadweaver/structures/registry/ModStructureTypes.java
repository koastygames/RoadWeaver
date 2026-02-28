package net.shiroha233.roadweaver.structures.registry;

import net.minecraft.world.level.levelgen.structure.StructureType;
import net.shiroha233.roadweaver.structures.types.BridgeTemplateStructure;
import net.shiroha233.roadweaver.structures.types.RoadsideStructure;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;

/**
 * 模组结构类型注册中心
 * 职责：持有平台层注册的结构类型引用
 */
public final class ModStructureTypes {
    private ModStructureTypes() {}
    
    public static StructureType<RoadsideStructure> ROADSIDE;
    public static StructureType<SpawnCabinStructure> SPAWN_CABIN;
    public static StructureType<BridgeTemplateStructure> BRIDGE_TEMPLATE;
    
    public static void setRoadside(StructureType<RoadsideStructure> type) {
        ROADSIDE = type;
    }
    
    public static void setSpawnCabin(StructureType<SpawnCabinStructure> type) {
        SPAWN_CABIN = type;
    }

    public static void setBridge(StructureType<BridgeTemplateStructure> type) {
        BRIDGE_TEMPLATE = type;
    }
}
