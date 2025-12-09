package net.shiroha233.roadweaver.mixin.forge.dynamictree;

import com.ferreusveritas.dynamictrees.worldgen.DynamicTreeFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelSimulatedReader;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DynamicTreeFeature.class)
public class DynamicTreeFeatureMixin {
    @SuppressWarnings("deprecation") // 屏蔽弃用提示
    @Inject(remap = false,method = "validTreePos",
            at = @At("HEAD"), cancellable = true)
    private static void roadweaver$validTreePosWithRoad(LevelSimulatedReader pLevel, BlockPos pPos, CallbackInfoReturnable<Boolean> cir) {
        if (pLevel instanceof WorldGenRegion worldGenRegion && RoadPositionQuery.isOnRoad(worldGenRegion.getLevel(),pPos)) {
            cir.setReturnValue(false);
        }
    }
}
