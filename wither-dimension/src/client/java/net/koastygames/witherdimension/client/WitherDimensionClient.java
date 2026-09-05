package net.koastygames.witherdimension.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.koastygames.witherdimension.client.model.*;
import net.koastygames.witherdimension.client.render.WitherMobRenderer;
import net.koastygames.witherdimension.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class WitherDimensionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModelLayerRegistry.registerModelLayer(WitherlingModel.LAYER, WitherlingModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(BoneBruteModel.LAYER, BoneBruteModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(SoulBeastModel.LAYER, SoulBeastModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(CitadelSentinelModel.LAYER, CitadelSentinelModel::createBodyLayer);

        EntityRenderers.register(ModEntities.WITHERLING, c -> new WitherMobRenderer<>(c, new WitherlingModel(c.bakeLayer(WitherlingModel.LAYER)), "witherling", 0.35F));
        EntityRenderers.register(ModEntities.BONE_BRUTE, c -> new WitherMobRenderer<>(c, new BoneBruteModel(c.bakeLayer(BoneBruteModel.LAYER)), "bone_brute", 0.9F));
        EntityRenderers.register(ModEntities.SOUL_BEAST, c -> new WitherMobRenderer<>(c, new SoulBeastModel(c.bakeLayer(SoulBeastModel.LAYER)), "soul_beast", 0.7F));
        EntityRenderers.register(ModEntities.CITADEL_SENTINEL, c -> new WitherMobRenderer<>(c, new CitadelSentinelModel(c.bakeLayer(CitadelSentinelModel.LAYER)), "citadel_sentinel", 0.6F));
    }
}
