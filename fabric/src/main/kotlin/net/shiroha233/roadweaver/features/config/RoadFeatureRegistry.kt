package net.shiroha233.roadweaver.features.config

import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature
import net.minecraft.world.level.levelgen.placement.PlacedFeature
import net.shiroha233.roadweaver.RoadWeaver
import net.shiroha233.roadweaver.features.path.PathFeature
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig

class RoadFeatureRegistry private constructor() {
    companion object {
        @JvmStatic
        fun register() {
            val feature: Feature<PathFeatureConfig> = PathFeature(PathFeatureConfig.CODEC)
            Registry.register(
                BuiltInRegistries.FEATURE,
                ResourceLocation(RoadWeaver.MOD_ID, "road_feature"),
                feature
            )

            // 1.20.1：BiomeModifications.addFeature 需要对应 placed_feature 已存在于注册表。
            // 之前依赖数据包 JSON（placed_feature/configured_feature）会在缺失/被排除时直接崩溃。
            val configuredKey: ResourceKey<ConfiguredFeature<*, *>> = ResourceKey.create(
                Registries.CONFIGURED_FEATURE,
                ResourceLocation(RoadWeaver.MOD_ID, "road_feature")
            )
            val placedKey: ResourceKey<PlacedFeature> = ResourceKey.create(
                Registries.PLACED_FEATURE,
                ResourceLocation(RoadWeaver.MOD_ID, "road_feature_placed")
            )

            // 说明：ConfiguredFeature / PlacedFeature 在 1.20.1 属于动态注册表（data pack 驱动）。
            // 这里仅声明 ResourceKey 并用于 biome 注入；实际条目应由数据包（或 datagen 输出）提供。

            BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
                placedKey
            )
        }
    }
}
