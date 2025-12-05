package net.shiroha233.roadweaver.structures.roadside.model;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.structures.roadside.rules.RoadsidePlacementRule;

/**
 * 路边装饰规格（内部数据模型）
 *
 * 设计目的：
 * - 把目前 RoadsideType 中与单个结构相关的静态信息抽离出来
 * - 为后续注册中心 / 对外 API / 数据驱动（datapack）预留统一载体
 *
 * 说明：
 * - 目前仅在 common 内部使用，未来可以在 API 包中暴露注册接口
 * - 先保持与 RoadsideType 字段一一对应，保证行为兼容
 */
public final class RoadsideDecorationSpec {
    private final ResourceLocation id;          // 装饰自身 ID（非模板 ID）
    private final ResourceLocation templateId;  // 结构模板 ID
    private final Vec3i sizeHint;               // 尺寸提示
    private final int weight;                   // 抽签权重
    private final boolean faceRoad;             // 是否需要朝向道路
    private final StructureScale scale;         // 规模（影响偏移/地形阈值）
    private final RoadsidePlacementRule placementRule; // 放置规则（群系/道路长度）

    public RoadsideDecorationSpec(ResourceLocation id,
                                  ResourceLocation templateId,
                                  Vec3i sizeHint,
                                  int weight,
                                  boolean faceRoad,
                                  StructureScale scale,
                                  RoadsidePlacementRule placementRule) {
        this.id = id;
        this.templateId = templateId;
        this.sizeHint = sizeHint;
        this.weight = weight;
        this.faceRoad = faceRoad;
        this.scale = scale;
        this.placementRule = placementRule;
    }

    public ResourceLocation id() {
        return id;
    }

    public ResourceLocation templateId() {
        return templateId;
    }

    public Vec3i sizeHint() {
        return sizeHint;
    }

    public int weight() {
        return weight;
    }

    public boolean faceRoad() {
        return faceRoad;
    }

    public StructureScale scale() {
        return scale;
    }

    public RoadsidePlacementRule placementRule() {
        return placementRule;
    }
}
