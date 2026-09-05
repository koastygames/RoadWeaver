package net.koastygames.witherdimension.client.render;

import net.koastygames.witherdimension.WitherDimensionMod;
import net.koastygames.witherdimension.client.model.WitherMobModel;
import net.koastygames.witherdimension.entity.AbstractWitherMob;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class WitherMobRenderer<T extends AbstractWitherMob> extends MobRenderer<T, LivingEntityRenderState, WitherMobModel> {
    private final Identifier texture;
    public WitherMobRenderer(EntityRendererProvider.Context context,String textureName,float shadow){ super(context,new WitherMobModel(context.bakeLayer(WitherMobModel.LAYER)),shadow); texture=WitherDimensionMod.id("textures/entity/"+textureName+".png"); }
    @Override public LivingEntityRenderState createRenderState(){ return new LivingEntityRenderState(); }
    @Override public Identifier getTextureLocation(LivingEntityRenderState state){ return texture; }
}
