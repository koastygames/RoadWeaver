package net.koastygames.witherdimension.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CitadelSentinelEntity extends AbstractWitherMob {
    public CitadelSentinelEntity(EntityType<? extends CitadelSentinelEntity> type, Level level) { super(type, level); }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) return;
        LivingEntity target = getTarget();
        if (target != null && tickCount % 72 == 0) {
            double d = distanceToSqr(target);
            if (d > 20.0D && d < 324.0D) {
                Vec3 velocity = target.position().add(0.0D, target.getBbHeight() * 0.45D, 0.0D)
                        .subtract(position().add(0.0D, 1.7D, 0.0D)).normalize().scale(0.72D);
                WitherSkull skull = new WitherSkull(level(), this, velocity);
                skull.setPos(getX(), getY() + 1.7D, getZ());
                level().addFreshEntity(skull);
            }
        }
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 68).add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 10).add(Attributes.ARMOR, 6).add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.FOLLOW_RANGE, 36);
    }
}
