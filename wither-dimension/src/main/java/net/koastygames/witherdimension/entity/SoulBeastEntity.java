package net.koastygames.witherdimension.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class SoulBeastEntity extends AbstractWitherMob {
    public SoulBeastEntity(EntityType<? extends SoulBeastEntity> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.42)
                .add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.FOLLOW_RANGE, 24);
    }
}
