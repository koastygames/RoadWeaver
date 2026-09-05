package net.koastygames.witherdimension.client.render;

import net.koastygames.witherdimension.WitherDimensionMod;
import net.koastygames.witherdimension.entity.AbstractWitherMob;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class WitherMobRenderer<T extends AbstractWitherMob, M extends EntityModel<LivingEntityRenderState>> extends MobRenderer<T, LivingEntityRenderState, M> {
    private final Identifier texture;
    public WitherMobRenderer(EntityRendererProvider.Context context, M model, String textureName, float shadow) {
        super(context, model, shadow);
        texture = WitherDimensionMod.id("textures/entity/" + textureName + ".png");
    }
    @Override public LivingEntityRenderState createRenderState() { return new LivingEntityRenderState(); }
    @Override public Identifier getTextureLocation(LivingEntityRenderState state) { return texture; }
}
