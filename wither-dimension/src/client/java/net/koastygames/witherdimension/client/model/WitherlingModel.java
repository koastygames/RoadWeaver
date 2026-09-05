package net.koastygames.witherdimension.client.model;

import net.koastygames.witherdimension.WitherDimensionMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public class WitherlingModel extends EntityModel<LivingEntityRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(WitherDimensionMod.id("witherling"), "main");
    private final ModelPart head, leftHead, rightHead, tail, body;
    public WitherlingModel(ModelPart root) {
        super(root); body=root.getChild("body"); head=root.getChild("head"); leftHead=root.getChild("left_head"); rightHead=root.getChild("right_head"); tail=root.getChild("tail");
    }
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh=new MeshDefinition(); PartDefinition root=mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0,16).addBox(-2,-2,-5,4,4,10)
                .texOffs(28,16).addBox(-5,-1,-2,10,2,2).texOffs(28,20).addBox(-4,-1,2,8,2,2), PartPose.offset(0,13,0));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0,0).addBox(-4,-4,-4,8,8,8), PartPose.offset(0,9,-5));
        root.addOrReplaceChild("left_head", CubeListBuilder.create().texOffs(32,0).addBox(-3,-3,-3,6,6,6), PartPose.offset(-6,11,-1));
        root.addOrReplaceChild("right_head", CubeListBuilder.create().texOffs(32,0).mirror().addBox(-3,-3,-3,6,6,6), PartPose.offset(6,11,-1));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0,30).addBox(-1,-1,0,2,2,9), PartPose.offset(0,13,5));
        return LayerDefinition.create(mesh,64,64);
    }
    @Override public void setupAnim(LivingEntityRenderState s) {
        super.setupAnim(s); float yaw=s.yRot*Mth.DEG_TO_RAD, pitch=s.xRot*Mth.DEG_TO_RAD;
        head.yRot=yaw; head.xRot=pitch; leftHead.yRot=yaw*0.55F-0.18F; rightHead.yRot=yaw*0.55F+0.18F;
        tail.yRot=Mth.cos(s.walkAnimationPos*.4F)*.32F; body.zRot=Mth.cos(s.walkAnimationPos*.28F)*.04F;
    }
}
