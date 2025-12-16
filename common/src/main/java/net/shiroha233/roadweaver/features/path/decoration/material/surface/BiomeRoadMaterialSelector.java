package net.shiroha233.roadweaver.features.path.decoration.material.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.NaturalPresetService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BiomeRoadMaterialSelector {
    private BiomeRoadMaterialSelector() {}

    public static List<BlockState> forBiome(WorldGenLevel world, BlockPos pos) {
        Holder<Biome> biome = world.getBiome(pos);
        Optional<ResourceKey<Biome>> keyOpt = biome.unwrapKey();
        if (keyOpt.isPresent()) {
            ResourceKey<Biome> key = keyOpt.get();
            // 自然道路材质：完全由 natural_presets.json 决定（按 biomeId 精确匹配）
            try {
                List<BlockState> custom = NaturalPresetService.chooseCustomMaterialsForBiomeId(key.location().toString());
                if (custom != null && !custom.isEmpty()) {
                    return custom;
                }
            } catch (Throwable ignored) {
            }
        }
        // 回退：使用默认预设（minecraft:plains），保证自然道路永远有材质可用
        try {
            List<BlockState> fallback = NaturalPresetService.chooseCustomMaterialsForBiomeId("minecraft:plains");
            if (fallback != null && !fallback.isEmpty()) {
                return fallback;
            }
        } catch (Throwable ignored) {
        }
        return list(Blocks.DIRT_PATH, Blocks.GRAVEL);
    }

    private static List<BlockState> list(net.minecraft.world.level.block.Block... blocks) {
        List<BlockState> out = new ArrayList<>();
        for (var b : blocks) out.add(b.defaultBlockState());
        return out;
    }
}
