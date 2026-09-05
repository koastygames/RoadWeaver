package net.koastygames.witherdimension.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CitadelSentinelEntity extends AbstractWitherMob {
    public CitadelSentinelEntity(EntityType<? extends CitadelSentinelEntity> type, Level level) { super(type, level); }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) return;
        LivingEntity target = getTarget();
        if (target != null && tickCount % 72 == 0 && level() instanceof ServerLevel server) {
            double d = distanceToSqr(target);
            if (d > 20.0D && d < 324.0D && hasLineOfSight(target)) {
                target.hurtServer(server, damageSources().mobAttack(this), 7.0F);
                Vec3 push = target.position().subtract(position()).normalize().scale(0.65D);
                target.push(push.x, 0.18D, push.z);
            }
        }
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 68).add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 10).add(Attributes.ARMOR, 6).add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.FOLLOW_RANGE, 36);
    }
}
