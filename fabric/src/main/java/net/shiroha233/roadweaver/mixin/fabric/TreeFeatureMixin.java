package net.shiroha233.roadweaver.mixin.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 阻止树木在道路上生成。
 * 原理：在 TreeFeature.place() 开头检查树根位置是否在道路上，若是则跳过生成。
 */
@Mixin(TreeFeature.class)
public abstract class TreeFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void roadweaver$skipTreeOnRoad(FeaturePlaceContext<TreeConfiguration> context,
                                           CallbackInfoReturnable<Boolean> cir) {
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();

        if (RoadPositionQuery.isOnRoad(level, origin)) {
            // 在道路上，跳过生成
            cir.setReturnValue(false);
        }
    }
}
