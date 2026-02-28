package net.shiroha233.roadweaver.features.config;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.features.highway.HighwayFeature;
import net.shiroha233.roadweaver.features.highway.config.HighwayFeatureConfig;
import net.shiroha233.roadweaver.features.path.PathFeature;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;

/**
 * Fabric Feature 注册
 * 职责：注册道路和高速公路 Feature 到世界生成系统
 */
public final class RoadFeatureRegistry {
    private RoadFeatureRegistry() {}

    public static void register() {
        Feature<PathFeatureConfig> feature = new PathFeature(PathFeatureConfig.CODEC);
        Registry.register(BuiltInRegistries.FEATURE, new ResourceLocation(RoadWeaver.MOD_ID, "road_feature"), feature);

        Feature<HighwayFeatureConfig> highwayFeature = new HighwayFeature(HighwayFeatureConfig.CODEC);
        Registry.register(BuiltInRegistries.FEATURE, new ResourceLocation(RoadWeaver.MOD_ID, "highway_feature"), highwayFeature);

        ResourceKey<PlacedFeature> placedKey = ResourceKey.create(
                Registries.PLACED_FEATURE,
                new ResourceLocation(RoadWeaver.MOD_ID, "road_feature_placed"));
        BiomeModifications.addFeature(
                BiomeSelectors.all(),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
                placedKey);

        ResourceKey<PlacedFeature> highwayPlacedKey = ResourceKey.create(
                Registries.PLACED_FEATURE,
                new ResourceLocation(RoadWeaver.MOD_ID, "highway_feature_placed"));
        BiomeModifications.addFeature(
                BiomeSelectors.all(),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
                highwayPlacedKey);
    }
}
