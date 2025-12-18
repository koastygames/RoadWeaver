package net.shiroha233.roadweaver.structures.fabric

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.structure.StructureType
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType
import net.shiroha233.roadweaver.RoadWeaver
import net.shiroha233.roadweaver.structures.pieces.ModStructurePieceTypes
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes
import net.shiroha233.roadweaver.structures.types.RoadsideStructure
import net.shiroha233.roadweaver.structures.types.SpawnCabinStructure

/**
 * Fabric 平台的结构注册
 */
object StructureRegistryFabric {

    /**
     * 注册所有结构相关内容
     * 在模组初始化时调用
     */
    @JvmStatic
    fun register() {
        registerStructurePieceTypes()
        registerStructureTypes()
    }

    /**
     * 注册 StructurePieceType
     */
    private fun registerStructurePieceTypes() {
        // 注册简单模板片段类型（需要 StructureTemplateManager）
        val simpleTemplate: StructurePieceType =
            StructurePieceType.StructureTemplateType { ctx, tag -> SimpleTemplatePiece(ctx, tag) }

        Registry.register(
            BuiltInRegistries.STRUCTURE_PIECE,
            ResourceLocation(RoadWeaver.MOD_ID, "simple_template"),
            simpleTemplate
        )

        ModStructurePieceTypes.setSimpleTemplate(simpleTemplate)
    }

    /**
     * 注册 StructureType
     */
    private fun registerStructureTypes() {
        // 注册路边结构类型
        val roadsideType: StructureType<RoadsideStructure> = StructureType { RoadsideStructure.CODEC.codec() }
        Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            ResourceLocation(RoadWeaver.MOD_ID, "roadside"),
            roadsideType
        )
        ModStructureTypes.setRoadside(roadsideType)

        // 注册初始小屋结构类型
        val spawnCabinType: StructureType<SpawnCabinStructure> = StructureType { SpawnCabinStructure.CODEC.codec() }
        Registry.register(
            BuiltInRegistries.STRUCTURE_TYPE,
            ResourceLocation(RoadWeaver.MOD_ID, "spawn_cabin"),
            spawnCabinType
        )
        ModStructureTypes.setSpawnCabin(spawnCabinType)
    }
}
