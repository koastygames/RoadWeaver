package net.shiroha233.roadweaver.structures.fabric;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.structures.pieces.ModStructurePieceTypes;
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece;
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes;
import net.shiroha233.roadweaver.structures.types.BridgeTemplateStructure;
import net.shiroha233.roadweaver.structures.types.RoadsideStructure;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;

/**
 * Fabric 平台的结构注册
 */
public final class StructureRegistryFabric {
    private StructureRegistryFabric() {}
    
    /**
     * 注册所有结构相关内容
     * 在模组初始化时调用
     */
    public static void register() {
        registerStructurePieceTypes();
        registerStructureTypes();
    }
    
    /**
     * 注册 StructurePieceType
     */
    private static void registerStructurePieceTypes() {
        // 注册简单模板片段类型（需要 StructureTemplateManager）
        StructurePieceType simpleTemplate = (StructurePieceType.StructureTemplateType) SimpleTemplatePiece::new;
        
        ResourceKey<StructurePieceType> simpleTemplateKey = ResourceKey.create(
                Registries.STRUCTURE_PIECE,
                ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "simple_template"));
        Registry.register(BuiltInRegistries.STRUCTURE_PIECE, simpleTemplateKey, simpleTemplate);
        
        ModStructurePieceTypes.setSimpleTemplate(simpleTemplate);
    }
    
    /**
     * 注册 StructureType
     */
    private static void registerStructureTypes() {
        // 注册路边结构类型
        StructureType<RoadsideStructure> roadsideType = () -> RoadsideStructure.CODEC;
        ResourceKey<StructureType<?>> roadsideKey = ResourceKey.create(
                Registries.STRUCTURE_TYPE,
                ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "roadside"));
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE, roadsideKey, roadsideType);
        ModStructureTypes.setRoadside(roadsideType);
        
        // 注册初始小屋结构类型
        StructureType<SpawnCabinStructure> spawnCabinType = () -> SpawnCabinStructure.CODEC;
        ResourceKey<StructureType<?>> spawnCabinKey = ResourceKey.create(
                Registries.STRUCTURE_TYPE,
                ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "spawn_cabin"));
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE, spawnCabinKey, spawnCabinType);
        ModStructureTypes.setSpawnCabin(spawnCabinType);

        // 注册桥类型
        StructureType<BridgeTemplateStructure> bridgeTemplateType = () -> BridgeTemplateStructure.CODEC;
        ResourceKey<StructureType<?>> bridgeKey = ResourceKey.create(
                Registries.STRUCTURE_TYPE,
                ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "bridge"));
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE, bridgeKey, bridgeTemplateType);
        ModStructureTypes.setBridge(bridgeTemplateType);
    }
}
