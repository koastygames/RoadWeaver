package net.shiroha233.roadweaver.mixin.fabric;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.shiroha233.roadweaver.generation.InitialGenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class PlayerDimensionChangeMixin {
    @Inject(method = "setServerLevel", at = @At("RETURN"))
    private void roadweaver$onSetServerLevel(ServerLevel level, CallbackInfo ci) {
        if (level != null) {
            InitialGenManager.begin(level);
        }
    }
}
