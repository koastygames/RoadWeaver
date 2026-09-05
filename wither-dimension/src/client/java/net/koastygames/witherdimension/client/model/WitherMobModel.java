package net.koastygames.witherdimension.client.model;

import net.koastygames.witherdimension.WitherDimensionMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public class WitherMobModel extends EntityModel<LivingEntityRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(WitherDimensionMod.id("wither_mob"),"main");
    private final ModelPart head,leftArm,rightArm,leftLeg,rightLeg;
    public WitherMobModel(ModelPart root){ super(root); head=root.getChild(PartNames.HEAD); leftArm=root.getChild(PartNames.LEFT_ARM); rightArm=root.getChild(PartNames.RIGHT_ARM); leftLeg=root.getChild(PartNames.LEFT_LEG); rightLeg=root.getChild(PartNames.RIGHT_LEG); }
    public static LayerDefinition createBodyLayer(){
        MeshDefinition mesh=new MeshDefinition(); PartDefinition root=mesh.getRoot();
        root.addOrReplaceChild(PartNames.BODY,CubeListBuilder.create().texOffs(16,16).addBox(-5,-6,-3,10,12,6),PartPose.offset(0,12,0));
        root.addOrReplaceChild(PartNames.HEAD,CubeListBuilder.create().texOffs(0,0).addBox(-4,-8,-4,8,8,8),PartPose.offset(0,6,0));
        root.addOrReplaceChild(PartNames.LEFT_ARM,CubeListBuilder.create().texOffs(40,16).addBox(-2,-2,-2,4,12,4),PartPose.offset(-7,8,0));
        root.addOrReplaceChild(PartNames.RIGHT_ARM,CubeListBuilder.create().texOffs(40,16).mirror().addBox(-2,-2,-2,4,12,4),PartPose.offset(7,8,0));
        root.addOrReplaceChild(PartNames.LEFT_LEG,CubeListBuilder.create().texOffs(0,16).addBox(-2,0,-2,4,12,4),PartPose.offset(-2.5F,12,0));
        root.addOrReplaceChild(PartNames.RIGHT_LEG,CubeListBuilder.create().texOffs(0,16).mirror().addBox(-2,0,-2,4,12,4),PartPose.offset(2.5F,12,0));
        return LayerDefinition.create(mesh,64,64);
    }
    @Override public void setupAnim(LivingEntityRenderState s){ super.setupAnim(s); head.xRot=s.xRot*Mth.DEG_TO_RAD; head.yRot=s.yRot*Mth.DEG_TO_RAD; float a=s.walkAnimationSpeed,p=s.walkAnimationPos; leftLeg.xRot=Mth.cos(p*.6662F)*1.4F*a; rightLeg.xRot=Mth.cos(p*.6662F+Mth.PI)*1.4F*a; leftArm.xRot=rightLeg.xRot*.75F; rightArm.xRot=leftLeg.xRot*.75F; }
}
