package net.shiroha233.roadweaver.api;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.shiroha233.roadweaver.structures.roadside.model.RoadsideDecorationSpec;
import net.shiroha233.roadweaver.structures.roadside.model.StructureScale;
import net.shiroha233.roadweaver.structures.roadside.rules.RoadsidePlacementRule;

/**
 * 路边装饰构造器，便于附属模组以链式方式创建规格。
 * 仅做参数收集与校验，保持 SRP。
 */
public final class RoadsideDecorationBuilder {
    private ResourceLocation id;
    private ResourceLocation templateId;
    private Vec3i sizeHint = new Vec3i(3, 3, 3);
    private int weight = 10;
    private boolean faceRoad = false;
    private StructureScale scale = StructureScale.SMALL;
    private RoadsidePlacementRule placementRule = RoadsidePlacementRule.UNIVERSAL;

    public RoadsideDecorationBuilder id(ResourceLocation id) {
        this.id = id;
        return this;
    }

    public RoadsideDecorationBuilder template(ResourceLocation templateId) {
        this.templateId = templateId;
        return this;
    }

    public RoadsideDecorationBuilder size(Vec3i size) {
        if (size != null) this.sizeHint = size;
        return this;
    }

    public RoadsideDecorationBuilder size(int x, int y, int z) {
        this.sizeHint = new Vec3i(x, y, z);
        return this;
    }

    public RoadsideDecorationBuilder weight(int weight) {
        this.weight = weight;
        return this;
    }

    public RoadsideDecorationBuilder faceRoad(boolean faceRoad) {
        this.faceRoad = faceRoad;
        return this;
    }

    public RoadsideDecorationBuilder scale(StructureScale scale) {
        if (scale != null) this.scale = scale;
        return this;
    }

    public RoadsideDecorationBuilder rule(RoadsidePlacementRule rule) {
        if (rule != null) this.placementRule = rule;
        return this;
    }

    /**
     * 校验并构造规格。若必需字段缺失或权重无效则返回 null。
     */
    public RoadsideDecorationSpec build() {
        if (id == null || templateId == null) return null;
        if (sizeHint == null || placementRule == null || scale == null) return null;
        if (weight <= 0) return null;
        return new RoadsideDecorationSpec(id, templateId, sizeHint, weight, faceRoad, scale, placementRule);
    }
}
