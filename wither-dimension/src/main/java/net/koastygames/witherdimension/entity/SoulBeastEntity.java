package net.koastygames.witherdimension.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class SoulBeastEntity extends AbstractWitherMob {
    public SoulBeastEntity(EntityType<? extends SoulBeastEntity> type, Level level) { super(type, level); }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) return;
        LivingEntity target = getTarget();
        if (target != null && tickCount % 48 == 0) {
            Vec3 dash = target.position().subtract(position());
            if (dash.lengthSqr() > 0.01D) setDeltaMovement(dash.normalize().scale(1.25D).add(0.0D, 0.18D, 0.0D));
        }
        if (target != null && tickCount % 105 == 0 && distanceToSqr(target) > 64.0D) {
            Vec3 toward = position().add(target.position().subtract(position()).normalize().scale(5.0D));
            setPos(toward.x, toward.y + 0.2D, toward.z);
        }
        if (tickCount % 9 == 0) {
            BlockPos p = blockPosition();
            if (level().getBlockState(p).isAir() && !level().getBlockState(p.below()).isAir()) {
                level().setBlock(p, Blocks.SOUL_FIRE.defaultBlockState(), 3);
            }
        }
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 44).add(Attributes.MOVEMENT_SPEED, 0.43)
                .add(Attributes.ATTACK_DAMAGE, 8).add(Attributes.FOLLOW_RANGE, 32);
    }
}
