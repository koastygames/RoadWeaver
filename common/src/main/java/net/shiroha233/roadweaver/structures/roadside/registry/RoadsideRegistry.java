package net.shiroha233.roadweaver.structures.roadside.registry;

import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.structures.roadside.model.RoadsideDecorationSpec;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 路边装饰注册中心
 *
 * 职责：
 * - 维护 id -> RoadsideDecorationSpec 的映射
 * - 从 RoadsideType 枚举初始化内建内容
 * - 为后续对外注册 API 预留统一入口
 */
public final class RoadsideRegistry {
    private RoadsideRegistry() {}

    private static final Map<ResourceLocation, RoadsideDecorationSpec> BY_ID = new ConcurrentHashMap<>();
    private static volatile boolean builtinInitialized = false;

    /**
     * 获取所有已注册的路边装饰规格
     */
    public static Collection<RoadsideDecorationSpec> all() {
        ensureBuiltin();
        return BY_ID.values();
    }

    /**
     * 根据 ID 获取装饰规格
     */
    public static RoadsideDecorationSpec get(ResourceLocation id) {
        ensureBuiltin();
        return BY_ID.get(id);
    }

    /**
     * 注册一个装饰规格
     * 目前主要由内建枚举初始化使用，未来可作为对外 API 入口
     */
    public static void register(RoadsideDecorationSpec spec) {
        Objects.requireNonNull(spec, "spec");
        BY_ID.putIfAbsent(spec.id(), spec);
    }

    /**
     * 确保内建的枚举类型已初始化到注册表
     * 采用懒加载，避免在未使用路边系统时浪费初始化开销
     */
    private static void ensureBuiltin() {
        if (builtinInitialized) {
            return;
        }
        synchronized (RoadsideRegistry.class) {
            if (builtinInitialized) {
                return;
            }
            for (RoadsideType type : RoadsideType.values()) {
                ResourceLocation id = new ResourceLocation("roadweaver", "roadside/" + type.name().toLowerCase());
                RoadsideDecorationSpec spec = new RoadsideDecorationSpec(
                        id,
                        type.templateId(),
                        type.sizeHint(),
                        type.weight(),
                        type.faceRoad(),
                        type.scale(),
                        type.placementRule()
                );
                register(spec);
            }
            builtinInitialized = true;
        }
    }

    /**
     * 清空注册表（用于热重载或服务器关闭）
     */
    static void clear() {
        BY_ID.clear();
        builtinInitialized = false;
    }
}
