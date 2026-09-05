package net.koastygames.witherdimension.world;

import net.koastygames.witherdimension.WitherDimensionMod;
import net.koastygames.witherdimension.world.feature.WitherLandmarkFeature;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class ModWorldgen {
    public static final Feature<NoneFeatureConfiguration> WITHER_LANDMARK = Registry.register(
            BuiltInRegistries.FEATURE, WitherDimensionMod.id("wither_landmark"),
            new WitherLandmarkFeature(NoneFeatureConfiguration.CODEC));
    public static void initialize() { }
    private ModWorldgen() { }
}
