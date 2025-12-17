package net.shiroha233.roadweaver.mixin.neoforge.dynamictree

import com.dtteam.dynamictrees.worldgen.feature.DynamicTreeFeature
import net.minecraft.core.BlockPos
import net.minecraft.server.level.WorldGenRegion
import net.minecraft.world.level.LevelSimulatedReader
import net.shiroha233.roadweaver.persistence.RoadPositionQuery
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(DynamicTreeFeature::class)
class DynamicTreeFeatureMixin {

    private companion object {
        @JvmStatic
        @Suppress("DEPRECATION")
        @Inject(
            method = ["validTreePos"],
            at = [At("HEAD")],
            cancellable = true,
            remap = false
        )
        private fun `roadweaver$validTreePosWithRoad`(
            pLevel: LevelSimulatedReader,
            pPos: BlockPos,
            cir: CallbackInfoReturnable<Boolean>
        ) {
            if (pLevel is WorldGenRegion) {
                if (RoadPositionQuery.isOnRoad(pLevel.level, pPos)) {
                    cir.returnValue = false
                }
            }
        }
    }
}
