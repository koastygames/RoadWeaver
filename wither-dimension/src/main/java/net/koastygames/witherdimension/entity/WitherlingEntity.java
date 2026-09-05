package net.koastygames.witherdimension.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class WitherlingEntity extends AbstractWitherMob {
    public WitherlingEntity(EntityType<? extends WitherlingEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        setNoGravity(true);
        LivingEntity target = getTarget();
        if (target == null || level().isClientSide()) {
            setDeltaMovement(getDeltaMovement().scale(0.94D).add(0.0D, Math.sin(tickCount * 0.18D) * 0.008D, 0.0D));
            return;
        }
        Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D).subtract(position());
        if (aim.lengthSqr() > 0.01D) {
            Vec3 steering = aim.normalize().scale(0.075D);
            setDeltaMovement(getDeltaMovement().scale(0.82D).add(steering));
        }
        if (distanceToSqr(target) < 2.6D && tickCount % 22 == 0 && level() instanceof ServerLevel server) {
            target.hurtServer(server, damageSources().mobAttack(this), 5.0F);
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide()) {
            level().explode(this, getX(), getY(), getZ(), 1.25F, Level.ExplosionInteraction.MOB);
        }
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 18).add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.FLYING_SPEED, 0.48).add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.FOLLOW_RANGE, 28);
    }
}
