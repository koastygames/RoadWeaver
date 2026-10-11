package net.shiroha233.roadweaver.mixin.fabric.rtf;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.shiroha233.roadweaver.persistence.RoadPositionQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ReTerraForged 模组模板树木生成阻拦
 * 拦截 TemplateFeature.place() 方法（用于 NBT 结构树）
 */
@Pseudo
@Mixin(targets = "raccoonman.reterraforged.world.worldgen.feature.template.TemplateFeature")
public class RTFTemplateFeatureMixin {

    /**
     * 拦截 place 方法，阻止在道路上生成模板树木
     * 使用 remap = true（默认）让 Mixin 自动处理 Minecraft 类的映射
     */
    @Inject(method = "place(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void roadweaver$blockRTFTemplateOnRoad(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin,
                                                    CallbackInfoReturnable<Boolean> cir) {
        try {
            if (RoadPositionQuery.isOnRoad(level, origin)) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {}
    }
}
