package net.shiroha233.roadweaver.mixin.neoforge.dynamictree;

import com.dtteam.dynamictrees.worldgen.feature.DynamicTreeFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelSimulatedReader;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DynamicTrees 模组树木生成拦截器。
 * <p>
 * DynamicTrees 的树木生成不走标准的 Feature#place 流程，
 * 而是通过 DynamicTreeFeature.validTreePos() 静态方法判断位置是否可放置树木。
 * 因此需要直接 Mixin 这个方法来实现道路上的树木阻拦。
 * </p>
 * <p>
 * 注意：此 Mixin 通过 MixinConfigPlugin 条件加载，仅当 DynamicTrees 模组存在时才生效。
 * </p>
 */
@Mixin(DynamicTreeFeature.class)
public class DynamicTreeFeatureMixin {

    /**
     * 拦截 validTreePos 方法，在道路位置返回 false 阻止树木生成
     */
    @SuppressWarnings("deprecation")
    @Inject(
            method = "validTreePos",
            at = @At("HEAD"),
            cancellable = true,
            remap = false  // DynamicTrees 的方法不需要 remap
    )
    private static void roadweaver$validTreePosWithRoad(LevelSimulatedReader pLevel, BlockPos pPos, CallbackInfoReturnable<Boolean> cir) {
        // 只在区块生成阶段（WorldGenRegion）拦截
        if (pLevel instanceof WorldGenRegion worldGenRegion) {
            if (RoadPositionQuery.isOnRoad(worldGenRegion.getLevel(), pPos)) {
                cir.setReturnValue(false);
            }
        }
    }
}
