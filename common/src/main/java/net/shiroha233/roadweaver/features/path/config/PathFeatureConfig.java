package net.shiroha233.roadweaver.features.path.config;

import com.mojang.serialization.MapCodec;

public final class PathFeatureConfig {
    public static final MapCodec<PathFeatureConfig> CODEC=MapCodec.unit(new PathFeatureConfig());
    public PathFeatureConfig(){}
}
