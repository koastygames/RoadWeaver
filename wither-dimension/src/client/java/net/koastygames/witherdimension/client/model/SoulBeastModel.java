package net.koastygames.witherdimension.client.model;

import net.koastygames.witherdimension.WitherDimensionMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public class SoulBeastModel extends EntityModel<LivingEntityRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(WitherDimensionMod.id("soul_beast"), "main");
    private final ModelPart head,frontLeft,frontRight,backLeft,backRight,tail;
    public SoulBeastModel(ModelPart root){super(root);head=root.getChild("head");frontLeft=root.getChild("front_left");frontRight=root.getChild("front_right");backLeft=root.getChild("back_left");backRight=root.getChild("back_right");tail=root.getChild("tail");}
    public static LayerDefinition createBodyLayer(){
        MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();
        root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,18).addBox(-5,-4,-7,10,8,16)
                .texOffs(0,42).addBox(-4,-6,-3,8,2,9),PartPose.offset(0,12,1));
        root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-4,-6,8,8,7)
                .texOffs(30,0).addBox(-3,3,-8,6,3,4),PartPose.offset(0,9,-7));
        root.addOrReplaceChild("front_left",CubeListBuilder.create().texOffs(40,20).addBox(-2,0,-2,4,10,4),PartPose.offset(-4,14,-4));
        root.addOrReplaceChild("front_right",CubeListBuilder.create().texOffs(40,20).mirror().addBox(-2,0,-2,4,10,4),PartPose.offset(4,14,-4));
        root.addOrReplaceChild("back_left",CubeListBuilder.create().texOffs(40,34).addBox(-2,0,-2,4,9,4),PartPose.offset(-4,15,6));
        root.addOrReplaceChild("back_right",CubeListBuilder.create().texOffs(40,34).mirror().addBox(-2,0,-2,4,9,4),PartPose.offset(4,15,6));
        root.addOrReplaceChild("tail",CubeListBuilder.create().texOffs(26,44).addBox(-1,-1,0,2,2,12),PartPose.offset(0,11,9));
        return LayerDefinition.create(mesh,64,64);
    }
    @Override public void setupAnim(LivingEntityRenderState s){super.setupAnim(s);head.xRot=s.xRot*Mth.DEG_TO_RAD*.6F;head.yRot=s.yRot*Mth.DEG_TO_RAD*.7F;float a=Math.min(1,s.walkAnimationSpeed*1.4F),p=s.walkAnimationPos;frontLeft.xRot=Mth.cos(p*.8F)*1.1F*a;backRight.xRot=frontLeft.xRot;frontRight.xRot=Mth.cos(p*.8F+Mth.PI)*1.1F*a;backLeft.xRot=frontRight.xRot;tail.yRot=Mth.cos(p*.45F)*.45F;}
}
