package net.shiroha233.roadweaver.mixin.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.HugeFungusFeature;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 阻止下界大蘑菇在道路上生成。
 */
@Mixin(HugeFungusFeature.class)
public abstract class HugeFungusFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void roadweaver$skipFungusOnRoad(FeaturePlaceContext<?> context,
                                             CallbackInfoReturnable<Boolean> cir) {
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();

        if (RoadPositionQuery.isOnRoad(level, origin)) {
            cir.setReturnValue(false);
        }
    }
}
