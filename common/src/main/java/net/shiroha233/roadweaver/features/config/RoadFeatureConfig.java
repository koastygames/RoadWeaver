package net.shiroha233.roadweaver.features.config;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * 空配置：旧的“预设”字段已移除，所有材质/宽度由 config/ 路径下的预设与配置驱动。
 */
public class RoadFeatureConfig implements FeatureConfiguration {
    public static final Codec<RoadFeatureConfig> CODEC = Codec.unit(new RoadFeatureConfig());
}
