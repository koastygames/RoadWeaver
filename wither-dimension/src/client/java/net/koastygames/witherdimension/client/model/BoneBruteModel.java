package net.koastygames.witherdimension.client.model;

import net.koastygames.witherdimension.WitherDimensionMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public class BoneBruteModel extends EntityModel<LivingEntityRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(WitherDimensionMod.id("bone_brute"), "main");
    private final ModelPart head,leftArm,rightArm,leftLeg,rightLeg;
    public BoneBruteModel(ModelPart root){ super(root); head=root.getChild("head");leftArm=root.getChild("left_arm");rightArm=root.getChild("right_arm");leftLeg=root.getChild("left_leg");rightLeg=root.getChild("right_leg"); }
    public static LayerDefinition createBodyLayer(){
        MeshDefinition mesh=new MeshDefinition(); PartDefinition root=mesh.getRoot();
        root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,20).addBox(-7,-8,-4,14,16,8)
                .texOffs(0,44).addBox(-9,-7,-5,18,5,10),PartPose.offset(0,11,0));
        root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-5,-5,-5,10,10,10),PartPose.offset(0,2,0));
        root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(40,0).addBox(-3,-3,-3,6,18,6),PartPose.offset(-10,7,0));
        root.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(40,0).mirror().addBox(-3,-3,-3,6,18,6),PartPose.offset(10,7,0));
        root.addOrReplaceChild("left_leg",CubeListBuilder.create().texOffs(36,24).addBox(-3,0,-3,6,13,6),PartPose.offset(-4,11,0));
        root.addOrReplaceChild("right_leg",CubeListBuilder.create().texOffs(36,24).mirror().addBox(-3,0,-3,6,13,6),PartPose.offset(4,11,0));
        return LayerDefinition.create(mesh,64,64);
    }
    @Override public void setupAnim(LivingEntityRenderState s){ super.setupAnim(s);head.xRot=s.xRot*Mth.DEG_TO_RAD;head.yRot=s.yRot*Mth.DEG_TO_RAD;float a=Math.min(1,s.walkAnimationSpeed),p=s.walkAnimationPos;leftLeg.xRot=Mth.cos(p*.55F)*.9F*a;rightLeg.xRot=Mth.cos(p*.55F+Mth.PI)*.9F*a;leftArm.xRot=rightLeg.xRot*.65F-.12F;rightArm.xRot=leftLeg.xRot*.65F-.12F; }
}
