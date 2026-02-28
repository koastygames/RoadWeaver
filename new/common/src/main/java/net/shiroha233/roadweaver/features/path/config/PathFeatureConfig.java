package net.shiroha233.roadweaver.features.path.config;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * 普通道路 Feature 配置
 * 
 * 职责：提供世界生成 Feature 所需的配置接口
 * 注：实际配置由 ModConfig 统一管理，此处为空配置
 */
public class PathFeatureConfig implements FeatureConfiguration {
    public static final Codec<PathFeatureConfig> CODEC = Codec.unit(new PathFeatureConfig());
}
