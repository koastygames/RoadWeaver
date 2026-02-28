package net.shiroha233.roadweaver.features.highway.config;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Highway Feature 配置
 * 职责：提供 Minecraft Feature 系统所需的配置接口
 */
public final class HighwayFeatureConfig implements FeatureConfiguration {
    public static final Codec<HighwayFeatureConfig> CODEC = Codec.unit(new HighwayFeatureConfig());
}
