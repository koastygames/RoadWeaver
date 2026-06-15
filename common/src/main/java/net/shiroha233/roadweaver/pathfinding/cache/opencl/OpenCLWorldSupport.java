package net.shiroha233.roadweaver.pathfinding.cache.opencl;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个维度/世界级别的 OpenCL 粗采样支持状态。
 */
public final class OpenCLWorldSupport {
    private static final Map<ResourceLocation, String> UNSUPPORTED_DIMENSIONS = new ConcurrentHashMap<>();

    private OpenCLWorldSupport() {}

    public static boolean isUnsupported(ResourceLocation dimensionId) {
        return dimensionId != null && UNSUPPORTED_DIMENSIONS.containsKey(dimensionId);
    }

    public static String unsupportedReason(ResourceLocation dimensionId) {
        return dimensionId == null ? null : UNSUPPORTED_DIMENSIONS.get(dimensionId);
    }

    public static void markUnsupported(ResourceLocation dimensionId, String reason) {
        if (dimensionId != null) {
            UNSUPPORTED_DIMENSIONS.putIfAbsent(dimensionId, reason == null || reason.isBlank() ? "unknown" : reason);
        }
    }

    public static void clear() {
        UNSUPPORTED_DIMENSIONS.clear();
    }
}