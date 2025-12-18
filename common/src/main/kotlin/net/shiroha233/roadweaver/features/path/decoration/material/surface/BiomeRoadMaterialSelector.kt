package net.shiroha233.roadweaver.features.path.decoration.material.surface

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.shiroha233.roadweaver.config.NaturalPresetService

object BiomeRoadMaterialSelector {
    /**
     * 自然道路材质选择结果（包含基础材质和半砖材质）
     */
    data class MaterialResult(val baseMaterials: List<BlockState>, val slabMaterials: List<BlockState>)

    @JvmStatic
    fun forBiome(world: WorldGenLevel, pos: BlockPos): List<BlockState> {
        return forBiomeWithSlabs(world, pos).baseMaterials
    }

    @JvmStatic
    fun forBiomeWithSlabs(world: WorldGenLevel, pos: BlockPos): MaterialResult {
        val biome: Holder<Biome> = world.getBiome(pos)
        val keyOpt: java.util.Optional<ResourceKey<Biome>> = biome.unwrapKey()
        val biomeId: String? = keyOpt.map { it.location().toString() }.orElse(null)

        if (!biomeId.isNullOrBlank()) {
            try {
                val custom = NaturalPresetService.chooseCustomMaterialsForBiomeId(biomeId)
                if (!custom.isNullOrEmpty()) {
                    val slabs = NaturalPresetService.chooseCustomSlabMaterialsForBiomeId(biomeId)
                    return MaterialResult(custom, slabs ?: emptyList())
                }
            } catch (_: Throwable) {
            }
        }

        try {
            val fallback = NaturalPresetService.chooseCustomMaterialsForBiomeId("minecraft:plains")
            if (!fallback.isNullOrEmpty()) {
                val fallbackSlabs = NaturalPresetService.chooseCustomSlabMaterialsForBiomeId("minecraft:plains")
                return MaterialResult(fallback, fallbackSlabs ?: emptyList())
            }
        } catch (_: Throwable) {
        }

        return MaterialResult(list(Blocks.DIRT_PATH, Blocks.GRAVEL), emptyList())
    }

    private fun list(vararg blocks: Block): List<BlockState> {
        val out = ArrayList<BlockState>()
        for (b in blocks) out.add(b.defaultBlockState())
        return out
    }
}
