package net.shiroha233.roadweaver.features.path.config;

import com.mojang.serialization.MapCodec;

/** Minimal 26.3 feature configuration retained for RoadWeaver's common APIs. */
public final class PathFeatureConfig {
    public static final MapCodec<PathFeatureConfig> CODEC = MapCodec.unit(new PathFeatureConfig());
    public PathFeatureConfig() {}
}
