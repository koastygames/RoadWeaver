package net.shiroha233.roadweaver.mixin.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.AbstractHugeMushroomFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 阻止大型蘑菇在道路上生成（红/棕色大蘑菇）。
 */
@Mixin(AbstractHugeMushroomFeature.class)
public abstract class HugeMushroomFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void roadweaver$skipMushroomOnRoad(FeaturePlaceContext<?> context,
                                               CallbackInfoReturnable<Boolean> cir) {
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();

        if (RoadPositionQuery.isOnRoad(level, origin)) {
            cir.setReturnValue(false);
        }
    }
}
