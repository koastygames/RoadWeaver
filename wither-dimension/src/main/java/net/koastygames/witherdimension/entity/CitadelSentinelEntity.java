package net.koastygames.witherdimension.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class CitadelSentinelEntity extends AbstractWitherMob {
    public CitadelSentinelEntity(EntityType<? extends CitadelSentinelEntity> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 55).add(Attributes.MOVEMENT_SPEED, 0.29)
                .add(Attributes.ATTACK_DAMAGE, 9).add(Attributes.FOLLOW_RANGE, 32);
    }
}
