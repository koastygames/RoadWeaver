package net.koastygames.witherdimension.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.koastygames.witherdimension.WitherDimensionMod;
import net.koastygames.witherdimension.entity.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<WitherlingEntity> WITHERLING = register("witherling", EntityType.Builder.of(WitherlingEntity::new, MobCategory.MONSTER).sized(0.65F, 0.9F));
    public static final EntityType<BoneBruteEntity> BONE_BRUTE = register("bone_brute", EntityType.Builder.of(BoneBruteEntity::new, MobCategory.MONSTER).sized(1.35F, 2.7F));
    public static final EntityType<SoulBeastEntity> SOUL_BEAST = register("soul_beast", EntityType.Builder.of(SoulBeastEntity::new, MobCategory.MONSTER).sized(1.25F, 1.2F));
    public static final EntityType<CitadelSentinelEntity> CITADEL_SENTINEL = register("citadel_sentinel", EntityType.Builder.of(CitadelSentinelEntity::new, MobCategory.MONSTER).sized(0.8F, 2.45F));

    private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, WitherDimensionMod.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
    public static void initialize() {
        FabricDefaultAttributeRegistry.register(WITHERLING, WitherlingEntity.attributes());
        FabricDefaultAttributeRegistry.register(BONE_BRUTE, BoneBruteEntity.attributes());
        FabricDefaultAttributeRegistry.register(SOUL_BEAST, SoulBeastEntity.attributes());
        FabricDefaultAttributeRegistry.register(CITADEL_SENTINEL, CitadelSentinelEntity.attributes());
    }
    private ModEntities() { }
}
