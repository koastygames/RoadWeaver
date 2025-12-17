package net.shiroha233.roadweaver.mixin.neoforge

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration
import net.shiroha233.roadweaver.generation.ChunkGenTracker
import net.shiroha233.roadweaver.persistence.RoadPositionQuery
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(Feature::class)
abstract class GenericFeatureMixin {

    private companion object {
        @Unique
        private val TREE_FEATURE_IDS: Set<String> = setOf(
            "minecraft:tree",
            "minecraft:huge_brown_mushroom",
            "minecraft:huge_red_mushroom",
            "minecraft:huge_fungus",
            "minecraft:chorus_plant",
            "oh_the_trees_youll_grow:tree_from_nbt_v1",
            "ohthetreesyoullgrow:tree_from_nbt_v1"
        )

        @Unique
        private val TREE_FEATURE_NAMESPACES: Set<String> = setOf(
            "biomesoplenty",
            "biomeswevegone",
            "bwg",
            "byg",
            "twilightforest",
            "regions_unexplored",
            "aether"
        )

        @Unique
        private fun `roadweaver$containsTreeKeyword`(name: String): Boolean {
            return name.contains("tree") ||
                name.contains("fungus") ||
                name.contains("mushroom") ||
                name.contains("bamboo") ||
                name.contains("chorus") ||
                name.contains("bush") ||
                name.contains("shrub")
        }
    }

    @Inject(
        method = [
            "place(Lnet/minecraft/world/level/levelgen/feature/configurations/FeatureConfiguration;" +
                "Lnet/minecraft/world/level/WorldGenLevel;" +
                "Lnet/minecraft/world/level/chunk/ChunkGenerator;" +
                "Lnet/minecraft/util/RandomSource;" +
                "Lnet/minecraft/core/BlockPos;)Z"
        ],
        at = [At("HEAD")],
        cancellable = true
    )
    private fun `roadweaver$skipTreeLikeFeaturesOnRoad`(
        config: FeatureConfiguration,
        level: WorldGenLevel,
        chunkGenerator: ChunkGenerator,
        random: RandomSource,
        pos: BlockPos,
        cir: CallbackInfoReturnable<Boolean>
    ) {
        if (!ChunkGenTracker.isWorldGenPhase(level)) {
            return
        }

        val feature = (this as Feature<*>)
        if (`roadweaver$isTreeLikeFeature`(feature, config) && RoadPositionQuery.isOnRoad(level, pos)) {
            cir.returnValue = false
        }
    }

    @Unique
    private fun `roadweaver$isTreeLikeFeature`(feature: Feature<*>, config: FeatureConfiguration?): Boolean {
        val featureId: ResourceLocation? = BuiltInRegistries.FEATURE.getKey(feature)
        if (featureId != null) {
            val fullId = featureId.toString()
            if (TREE_FEATURE_IDS.contains(fullId)) {
                return true
            }

            val namespace = featureId.namespace
            val path = featureId.path.lowercase()

            if (TREE_FEATURE_NAMESPACES.contains(namespace) && `roadweaver$containsTreeKeyword`(path)) {
                return true
            }

            if (`roadweaver$containsTreeKeyword`(path)) {
                return true
            }
        }

        if (config != null) {
            if (config is TreeConfiguration) {
                return true
            }

            val configClassName = config.javaClass.name.lowercase()
            if (configClassName.contains("tree") ||
                configClassName.contains("nbt") ||
                configClassName.contains("structure")
            ) {
                return true
            }
        }

        val featureClassName = feature.javaClass.name.lowercase()
        return `roadweaver$containsTreeKeyword`(featureClassName)
    }
}
