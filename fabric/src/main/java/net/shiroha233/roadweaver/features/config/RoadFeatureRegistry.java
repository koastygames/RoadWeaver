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
import net.shiroha233.roadweaver.features.RoadFeature;

public final class RoadFeatureRegistry {
    private RoadFeatureRegistry() {}

    public static void register() {
        Feature<RoadFeatureConfig> feature = new RoadFeature(RoadFeatureConfig.CODEC);
        Registry.register(BuiltInRegistries.FEATURE, new ResourceLocation(RoadWeaver.MOD_ID, "road_feature"), feature);

        // 使用现有数据包中的 placed_feature 键完成注入（该 JSON 仅作为 Hook，不承载预设）
        ResourceKey<PlacedFeature> placedKey = ResourceKey.create(
                Registries.PLACED_FEATURE,
                new ResourceLocation(RoadWeaver.MOD_ID, "road_feature_placed")
        );
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
                placedKey
        );
    }
}
