package net.koastygames.witherdimension.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

public class BoneBruteEntity extends AbstractWitherMob {
    public BoneBruteEntity(EntityType<? extends BoneBruteEntity> type, Level level) { super(type, level); }

    @Override
    public void aiStep() {
        super.aiStep();
        LivingEntity target = getTarget();
        if (target != null && !level().isClientSide() && distanceToSqr(target) < 18.0D && tickCount % 50 == 0 && level() instanceof ServerLevel server) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            double len = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
            target.push(dx / len * 1.35D, 0.55D, dz / len * 1.35D);
            target.hurtServer(server, damageSources().mobAttack(this), 8.0F);
        }
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 90).add(Attributes.MOVEMENT_SPEED, 0.20)
                .add(Attributes.ATTACK_DAMAGE, 12).add(Attributes.ARMOR, 8).add(Attributes.KNOCKBACK_RESISTANCE, 0.75)
                .add(Attributes.FOLLOW_RANGE, 30);
    }
}
