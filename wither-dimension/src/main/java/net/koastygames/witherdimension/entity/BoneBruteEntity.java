package net.koastygames.witherdimension.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class BoneBruteEntity extends AbstractWitherMob {
    public BoneBruteEntity(EntityType<? extends BoneBruteEntity> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 70).add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 11).add(Attributes.FOLLOW_RANGE, 28);
    }
}
