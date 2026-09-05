package net.koastygames.witherdimension.client.model;

import net.koastygames.witherdimension.WitherDimensionMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public class CitadelSentinelModel extends EntityModel<LivingEntityRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(WitherDimensionMod.id("citadel_sentinel"), "main");
    private final ModelPart head,leftArm,rightArm,leftLeg,rightLeg,spear;
    public CitadelSentinelModel(ModelPart root){super(root);head=root.getChild("head");leftArm=root.getChild("left_arm");rightArm=root.getChild("right_arm");leftLeg=root.getChild("left_leg");rightLeg=root.getChild("right_leg");spear=root.getChild("spear");}
    public static LayerDefinition createBodyLayer(){
        MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();
        root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(16,20).addBox(-5,-8,-3,10,17,6)
                .texOffs(0,44).addBox(-7,-8,-4,14,4,8),PartPose.offset(0,10,0));
        root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-5,-4,8,10,8)
                .texOffs(32,0).addBox(-4,-9,-3,2,5,2).addBox(2,-9,-3,2,5,2),PartPose.offset(0,0,0));
        root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(44,16).addBox(-2,-2,-2,4,16,4),PartPose.offset(-7,6,0));
        root.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(44,16).mirror().addBox(-2,-2,-2,4,16,4),PartPose.offset(7,6,0));
        root.addOrReplaceChild("left_leg",CubeListBuilder.create().texOffs(0,20).addBox(-2,0,-2,4,14,4),PartPose.offset(-2.5F,10,0));
        root.addOrReplaceChild("right_leg",CubeListBuilder.create().texOffs(0,20).mirror().addBox(-2,0,-2,4,14,4),PartPose.offset(2.5F,10,0));
        root.addOrReplaceChild("spear",CubeListBuilder.create().texOffs(56,0).addBox(-1,-12,-1,2,28,2)
                .texOffs(48,36).addBox(-3,-15,-1,6,4,2),PartPose.offset(9,8,0));
        return LayerDefinition.create(mesh,64,64);
    }
    @Override public void setupAnim(LivingEntityRenderState s){super.setupAnim(s);head.xRot=s.xRot*Mth.DEG_TO_RAD;head.yRot=s.yRot*Mth.DEG_TO_RAD;float a=Math.min(1,s.walkAnimationSpeed),p=s.walkAnimationPos;leftLeg.xRot=Mth.cos(p*.62F)*1.0F*a;rightLeg.xRot=Mth.cos(p*.62F+Mth.PI)*1.0F*a;leftArm.xRot=rightLeg.xRot*.55F;rightArm.xRot=leftLeg.xRot*.35F-.45F;spear.xRot=rightArm.xRot;}
}
