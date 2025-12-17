package net.shiroha233.roadweaver.structures.neoforge

import net.minecraft.core.registries.Registries
import net.minecraft.world.level.levelgen.structure.StructureType
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import net.shiroha233.roadweaver.RoadWeaver
import net.shiroha233.roadweaver.structures.pieces.ModStructurePieceTypes
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes
import net.shiroha233.roadweaver.structures.types.RoadsideStructure
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure
import java.util.function.Supplier

/**
 * NeoForge 平台的结构注册
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

    // 简单模板片段类型（需要 StructureTemplateManager）
    @JvmField
    val SIMPLE_TEMPLATE: DeferredHolder<StructurePieceType, StructurePieceType> =
        STRUCTURE_PIECE_TYPES.register(
            "simple_template",
            Supplier { StructurePieceType.StructureTemplateType(::SimpleTemplatePiece) }
        )

    // 路边结构类型
    @JvmField
    val ROADSIDE: DeferredHolder<StructureType<*>, StructureType<RoadsideStructure>> =
        STRUCTURE_TYPES.register(
            "roadside",
            Supplier { StructureType { RoadsideStructure.CODEC } }
        )

    // 初始小屋结构类型
    @JvmField
    val SPAWN_CABIN: DeferredHolder<StructureType<*>, StructureType<SpawnCabinStructure>> =
        STRUCTURE_TYPES.register(
            "spawn_cabin",
            Supplier { StructureType { SpawnCabinStructure.CODEC } }
        )

    /**
     * 注册到事件总线
     */
    @JvmStatic
    fun register(modBus: IEventBus) {
        STRUCTURE_PIECE_TYPES.register(modBus)
        STRUCTURE_TYPES.register(modBus)

        // 注册完成后设置静态引用（延迟到注册完成后）
        modBus.addListener { event: net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent ->
            event.enqueueWork {
                ModStructurePieceTypes.setSimpleTemplate(SIMPLE_TEMPLATE.get())
                ModStructureTypes.setRoadside(ROADSIDE.get())
                ModStructureTypes.setSpawnCabin(SPAWN_CABIN.get())
            }
        }
    }
}
