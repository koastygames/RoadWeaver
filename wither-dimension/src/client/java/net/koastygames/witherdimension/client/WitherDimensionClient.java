package net.koastygames.witherdimension.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.koastygames.witherdimension.client.model.WitherMobModel;
import net.koastygames.witherdimension.client.render.WitherMobRenderer;
import net.koastygames.witherdimension.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class WitherDimensionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(WitherMobModel.LAYER, WitherMobModel::createBodyLayer);
        EntityRenderers.register(ModEntities.WITHERLING, c -> new WitherMobRenderer<>(c, "witherling", 0.35F));
        EntityRenderers.register(ModEntities.BONE_BRUTE, c -> new WitherMobRenderer<>(c, "bone_brute", 0.75F));
        EntityRenderers.register(ModEntities.SOUL_BEAST, c -> new WitherMobRenderer<>(c, "soul_beast", 0.55F));
        EntityRenderers.register(ModEntities.CITADEL_SENTINEL, c -> new WitherMobRenderer<>(c, "citadel_sentinel", 0.5F));
    }
}
