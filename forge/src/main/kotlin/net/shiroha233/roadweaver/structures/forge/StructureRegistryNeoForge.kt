package net.shiroha233.roadweaver.structures.forge

import net.minecraft.core.registries.Registries
import net.minecraft.world.level.levelgen.structure.StructureType
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.shiroha233.roadweaver.RoadWeaver
import net.shiroha233.roadweaver.structures.pieces.ModStructurePieceTypes
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes
import net.shiroha233.roadweaver.structures.types.RoadsideStructure
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure
import java.util.function.Supplier

/**
 * Forge 平台的结构注册 (1.20.1)
 */
@Suppress("MemberVisibilityCanBePrivate")
object StructureRegistryNeoForge {
    // StructurePieceType 注册器
    @JvmField
    val STRUCTURE_PIECE_TYPES: DeferredRegister<StructurePieceType> =
        DeferredRegister.create(Registries.STRUCTURE_PIECE, RoadWeaver.MOD_ID)

    // StructureType 注册器
    @JvmField
    val STRUCTURE_TYPES: DeferredRegister<StructureType<*>> =
        DeferredRegister.create(Registries.STRUCTURE_TYPE, RoadWeaver.MOD_ID)

    // 简单模板片段类型
    @JvmField
    val SIMPLE_TEMPLATE: RegistryObject<StructurePieceType> =
        STRUCTURE_PIECE_TYPES.register("simple_template") {
            StructurePieceType.StructureTemplateType { templateManager, tag -> SimpleTemplatePiece(templateManager, tag) }
        }

    // 路边结构类型
    @JvmField
    val ROADSIDE: RegistryObject<StructureType<RoadsideStructure>> =
        STRUCTURE_TYPES.register("roadside") {
            StructureType { RoadsideStructure.CODEC.codec() }
        }

    // 初始小屋结构类型
    @JvmField
    val SPAWN_CABIN: RegistryObject<StructureType<SpawnCabinStructure>> =
        STRUCTURE_TYPES.register("spawn_cabin") {
            StructureType { SpawnCabinStructure.CODEC.codec() }
        }

    @JvmStatic
    fun register(modBus: IEventBus) {
        STRUCTURE_PIECE_TYPES.register(modBus)
        STRUCTURE_TYPES.register(modBus)

        modBus.addListener { event: FMLCommonSetupEvent ->
            event.enqueueWork {
                ModStructurePieceTypes.setSimpleTemplate(SIMPLE_TEMPLATE.get())
                ModStructureTypes.setRoadside(ROADSIDE.get())
                ModStructureTypes.setSpawnCabin(SPAWN_CABIN.get())
            }
        }
    }
}
