package net.shiroha233.roadweaver.structures.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.structures.pieces.ModStructurePieceTypes;
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece;
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes;
import net.shiroha233.roadweaver.structures.types.BridgeTemplateStructure;
import net.shiroha233.roadweaver.structures.types.RoadsideStructure;
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure;

/**
 * NeoForge 骞冲彴鐨勭粨鏋勬敞鍐?
 */
public final class StructureRegistryNeoForge {
    private StructureRegistryNeoForge() {}
    
    // StructurePieceType 娉ㄥ唽鍣?
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, RoadWeaver.MOD_ID);
    
    // StructureType 娉ㄥ唽鍣?
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, RoadWeaver.MOD_ID);
    
    // 绠€鍗曟ā鏉跨墖娈电被鍨嬶紙闇€瑕?StructureTemplateManager锛?
    public static final DeferredHolder<StructurePieceType, StructurePieceType> SIMPLE_TEMPLATE =
            STRUCTURE_PIECE_TYPES.register("simple_template",
                () -> (StructurePieceType.StructureTemplateType) SimpleTemplatePiece::new);
    
    // 璺竟缁撴瀯绫诲瀷
    public static final DeferredHolder<StructureType<?>, StructureType<RoadsideStructure>> ROADSIDE =
            STRUCTURE_TYPES.register("roadside",
                () -> () -> RoadsideStructure.CODEC);
    
    // 鍒濆灏忓眿缁撴瀯绫诲瀷
    public static final DeferredHolder<StructureType<?>, StructureType<SpawnCabinStructure>> SPAWN_CABIN =
            STRUCTURE_TYPES.register("spawn_cabin",
                () -> () -> SpawnCabinStructure.CODEC);

    // 妗ユ妯℃澘绫诲瀷
    public static final DeferredHolder<StructureType<?>, StructureType<BridgeTemplateStructure>> BRIDGE_TEMPLATE =
            STRUCTURE_TYPES.register("bridge",
                    () -> () -> BridgeTemplateStructure.CODEC);
    
    /**
     * 娉ㄥ唽鍒颁簨浠舵€荤嚎
     */
    public static void register(IEventBus modBus) {
        STRUCTURE_PIECE_TYPES.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        
        // 娉ㄥ唽瀹屾垚鍚庤缃潤鎬佸紩鐢紙寤惰繜鍒版敞鍐屽畬鎴愬悗锛?
        modBus.addListener((FMLCommonSetupEvent event) -> {
            event.enqueueWork(() -> {
                ModStructurePieceTypes.setSimpleTemplate(SIMPLE_TEMPLATE.get());
                ModStructureTypes.setRoadside(ROADSIDE.get());
                ModStructureTypes.setSpawnCabin(SPAWN_CABIN.get());
                ModStructureTypes.setBridge(BRIDGE_TEMPLATE.get());
            });
        });
    }
}

