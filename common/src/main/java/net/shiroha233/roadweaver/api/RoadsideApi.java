package net.shiroha233.roadweaver.api;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.structures.roadside.RoadsideDecorationSpec;
import net.shiroha233.roadweaver.structures.roadside.RoadsidePlacementRule;
import net.shiroha233.roadweaver.structures.roadside.RoadsideRegistry;
import net.shiroha233.roadweaver.structures.roadside.StructureScale;

/**
 * 路边装饰对外注册 API。
 *
 * 设计目标：
 * - 为其他模组 / 数据包提供一个稳定入口，将自定义结构挂到 RoadWeaver 的道路系统旁边；
 * - 内部仍然复用 RoadsideDecorationSpec / RoadsideRegistry，不改变现有生成逻辑；
 * - 保持 API 简单：传入模板 ID、尺寸、权重、规模和放置规则即可。
 */
public final class RoadsideApi {
    private RoadsideApi() {}

    /**
     * 获取一个链式构造器，方便附属模组按需设置字段后注册。
     */
    public static RoadsideDecorationBuilder builder() {
        return new RoadsideDecorationBuilder();
    }

    /**
    */
    public static void registerDecoration(RoadsideDecorationSpec spec) {
        if (spec == null) {
            return;
        }
        if (spec.id() == null || spec.templateId() == null || spec.sizeHint() == null
                || spec.scale() == null || spec.placementRule() == null) {
            return;
        }
        if (spec.weight() <= 0) {
            return;
        }
        RoadsideRegistry.register(spec);
    }

    /**
     * 注册一个完整自定义路边装饰。
     * <p>
     * 注意：
     * - 这里只负责把装饰规格注册到 RoadWeaver 的路边系统中；
     * - 具体结构模板需要由调用方自己放在 data pack 中，例如：
     *   data/<namespace>/structures/<path>.nbt，对应 templateId；
     * - placementRule 一般可复用 RoadsidePlacementRule 里的静态预设，或使用其 Builder 自行构造。
     */
    public static void registerDecoration(ResourceLocation id,
                                          ResourceLocation templateId,
                                          Vec3i sizeHint,
                                          int weight,
                                          boolean faceRoad,
                                          StructureScale scale,
                                          RoadsidePlacementRule placementRule) {
        RoadsideDecorationSpec spec = new RoadsideDecorationSpec(
                id,
                templateId,
                sizeHint,
                weight,
                faceRoad,
                scale,
                placementRule
        );
        registerDecoration(spec);
    }

    /**
     * 便捷重载：使用尺寸整数参数而不是 Vec3i。
     */
    public static void registerDecoration(ResourceLocation id,
                                          ResourceLocation templateId,
                                          int sizeX, int sizeY, int sizeZ,
                                          int weight,
                                          boolean faceRoad,
                                          StructureScale scale,
                                          RoadsidePlacementRule placementRule) {
        registerDecoration(id, templateId, new Vec3i(sizeX, sizeY, sizeZ), weight, faceRoad, scale, placementRule);
    }

    /**
     * 便捷重载：使用默认规则注册一个简单的小型装饰。
     * <p>
     * - 规模默认为 SMALL；
     * - 规则默认为 UNIVERSAL（所有群系、无道路长度限制）；
     * - 权重默认为 10。
     */
    public static void registerSimpleSmall(ResourceLocation id,
                                           ResourceLocation templateId,
                                           int sizeX, int sizeY, int sizeZ,
                                           boolean faceRoad) {
        registerDecoration(
                id,
                templateId,
                new Vec3i(sizeX, sizeY, sizeZ),
                10,
                faceRoad,
                StructureScale.SMALL,
                RoadsidePlacementRule.UNIVERSAL
        );
    }
}
