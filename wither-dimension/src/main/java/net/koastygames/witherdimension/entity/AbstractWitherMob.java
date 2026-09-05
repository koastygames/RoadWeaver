package net.koastygames.witherdimension.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public abstract class AbstractWitherMob extends PathfinderMob {
    protected AbstractWitherMob(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05, false));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.85));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }
}
