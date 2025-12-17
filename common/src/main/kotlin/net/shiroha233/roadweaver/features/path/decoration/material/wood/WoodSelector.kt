package net.shiroha233.roadweaver.features.path.decoration.material.wood

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.shiroha233.roadweaver.helpers.Records

object WoodSelector {
    @JvmStatic
    fun forBiome(world: WorldGenLevel, pos: BlockPos): Records.WoodAssets {
        val biome = world.getBiome(pos)
        val optKey: java.util.Optional<ResourceKey<Biome>> = biome.unwrapKey()
        if (optKey.isPresent) {
            val key = optKey.get()
            return when {
                key == Biomes.BAMBOO_JUNGLE -> Records.WoodAssets(Blocks.BAMBOO_FENCE, Blocks.BAMBOO_HANGING_SIGN, Blocks.BAMBOO_PLANKS)
                biome.`is`(BiomeTags.IS_JUNGLE) -> Records.WoodAssets(Blocks.JUNGLE_FENCE, Blocks.JUNGLE_HANGING_SIGN, Blocks.JUNGLE_PLANKS)
                biome.`is`(BiomeTags.IS_SAVANNA) -> Records.WoodAssets(Blocks.ACACIA_FENCE, Blocks.ACACIA_HANGING_SIGN, Blocks.ACACIA_PLANKS)
                key == Biomes.DARK_FOREST -> Records.WoodAssets(Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_HANGING_SIGN, Blocks.DARK_OAK_PLANKS)
                key == Biomes.CHERRY_GROVE -> Records.WoodAssets(Blocks.CHERRY_FENCE, Blocks.CHERRY_HANGING_SIGN, Blocks.CHERRY_PLANKS)
                key == Biomes.BIRCH_FOREST || key == Biomes.OLD_GROWTH_BIRCH_FOREST -> Records.WoodAssets(Blocks.BIRCH_FENCE, Blocks.BIRCH_HANGING_SIGN, Blocks.BIRCH_PLANKS)
                biome.`is`(BiomeTags.IS_TAIGA) -> Records.WoodAssets(Blocks.SPRUCE_FENCE, Blocks.SPRUCE_HANGING_SIGN, Blocks.SPRUCE_PLANKS)
                else -> Records.WoodAssets(Blocks.OAK_FENCE, Blocks.OAK_HANGING_SIGN, Blocks.OAK_PLANKS)
            }
        }
        return Records.WoodAssets(Blocks.OAK_FENCE, Blocks.OAK_HANGING_SIGN, Blocks.OAK_PLANKS)
    }
}
