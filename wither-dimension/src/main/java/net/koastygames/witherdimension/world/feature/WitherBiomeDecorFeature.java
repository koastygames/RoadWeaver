package net.koastygames.witherdimension.world.feature;

import com.mojang.serialization.Codec;
import net.koastygames.witherdimension.WitherDimensionMod;
import net.koastygames.witherdimension.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class WitherBiomeDecorFeature extends Feature<NoneFeatureConfiguration> {
    private static final ResourceKey<Biome> ASHEN = biome("ashen_wastelands");
    private static final ResourceKey<Biome> SOULSWAMP = biome("soulswamp");
    private static final ResourceKey<Biome> BASALT = biome("basalt_spires");
    private static final ResourceKey<Biome> CRYSTAL = biome("cursed_crystal_caverns");

    public WitherBiomeDecorFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    private static ResourceKey<Biome> biome(String name) {
        return ResourceKey.create(Registries.BIOME, WitherDimensionMod.id(name));
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, ctx.origin());
        if (surface.getY() <= level.getMinY() + 2) return false;

        if (level.getBiome(surface).is(ASHEN)) {
            paintSurface(level, surface, ModBlocks.ASH_STONE, ModBlocks.WITHER_STONE, r);
            decorateAshen(level, surface, r);
        } else if (level.getBiome(surface).is(SOULSWAMP)) {
            paintSurface(level, surface, ModBlocks.SOULSTONE, ModBlocks.SOUL_SAND_BRICKS, r);
            decorateSoulSwamp(level, surface, r);
        } else if (level.getBiome(surface).is(BASALT)) {
            paintSurface(level, surface, ModBlocks.WITHER_STONE, ModBlocks.POLISHED_WITHER_STONE, r);
            decorateBasalt(level, surface, r);
        } else if (level.getBiome(surface).is(CRYSTAL)) {
            paintSurface(level, surface, ModBlocks.VOID_INFUSED_STONE, ModBlocks.WITHER_STONE, r);
            decorateCrystal(level, surface, r);
        }
        return true;
    }

    private static void set(WorldGenLevel l, BlockPos p, Block b) { l.setBlock(p, b.defaultBlockState(), 3); }

    private static void paintSurface(WorldGenLevel level, BlockPos center, Block top, Block under, RandomSource r) {
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                BlockPos air = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, center.offset(x, 0, z));
                BlockPos ground = air.below();
                if (level.getBlockState(ground).is(Blocks.WATER) || level.getBlockState(ground).is(Blocks.LAVA)) continue;
                set(level, ground, r.nextInt(8) == 0 ? under : top);
                if (ground.getY() > level.getMinY() + 1 && !level.getBlockState(ground.below()).isAir()) {
                    set(level, ground.below(), under);
                }
            }
        }
    }

    private static void decorateAshen(WorldGenLevel l, BlockPos o, RandomSource r) {
        int kind = r.nextInt(4);
        if (kind == 0) {
            int h = 3 + r.nextInt(5);
            for (int y = 0; y < h; y++) set(l, o.above(y), ModBlocks.SOUL_FIRE_LOG);
            for (int i = 0; i < 3; i++) {
                int y = 2 + r.nextInt(Math.max(1, h - 1));
                int dx = r.nextBoolean() ? 1 : -1;
                for (int d = 1; d <= 2 + r.nextInt(2); d++) set(l, o.offset(dx * d, y, 0), ModBlocks.SOUL_FIRE_LOG);
            }
        } else if (kind == 1) {
            for (int i = -3; i <= 3; i++) {
                BlockPos p = l.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, o.offset(i, 0, i / 2));
                if (r.nextFloat() > .2F) set(l, p.below(), Blocks.LAVA);
            }
        } else {
            int rad = 1 + r.nextInt(3);
            for (int x = -rad; x <= rad; x++) for (int z = -rad; z <= rad; z++)
                if (x*x + z*z <= rad*rad + 1) set(l, o.offset(x, -1, z), r.nextBoolean() ? ModBlocks.ASH_STONE : ModBlocks.ASH_BRICKS);
        }
    }

    private static void decorateSoulSwamp(WorldGenLevel l, BlockPos o, RandomSource r) {
        if (r.nextBoolean()) {
            int rad = 2 + r.nextInt(3);
            for (int x = -rad; x <= rad; x++) for (int z = -rad; z <= rad; z++) {
                if (x*x + z*z <= rad*rad) {
                    BlockPos p = o.offset(x, -1, z);
                    set(l, p, ModBlocks.SOULSTONE);
                    if (l.getBlockState(p.above()).isAir()) set(l, p.above(), Blocks.WATER);
                }
            }
        } else {
            int h = 4 + r.nextInt(5);
            for (int y = 0; y < h; y++) set(l, o.above(y), ModBlocks.SOUL_FIRE_LOG);
            for (int d = 1; d <= 3; d++) {
                if (r.nextBoolean()) set(l, o.offset(d, h - 2, 0), ModBlocks.SOUL_FIRE_LOG);
                if (r.nextBoolean()) set(l, o.offset(-d, h - 3, 0), ModBlocks.SOUL_FIRE_LOG);
            }
            if (l.getBlockState(o.east()).isAir()) set(l, o.east(), Blocks.SOUL_FIRE);
        }
    }

    private static void decorateBasalt(WorldGenLevel l, BlockPos o, RandomSource r) {
        if (r.nextInt(5) == 0) {
            BlockPos island = o.above(14 + r.nextInt(14));
            for (int y = 0; y < 5; y++) {
                int rad = Math.max(1, 4 - y);
                for (int x = -rad; x <= rad; x++) for (int z = -rad; z <= rad; z++)
                    if (x*x + z*z <= rad*rad + 2) set(l, island.offset(x, -y, z), r.nextInt(4)==0 ? ModBlocks.BONE_BRICKS : ModBlocks.WITHER_STONE);
            }
            return;
        }
        int h = 8 + r.nextInt(19);
        for (int y = 0; y < h; y++) {
            int rad = y < 4 ? 2 : 1;
            for (int x = -rad; x <= rad; x++) for (int z = -rad; z <= rad; z++)
                if (x*x + z*z <= rad*rad + 1) set(l, o.offset(x, y, z), r.nextInt(6)==0 ? Blocks.BASALT : Blocks.BLACKSTONE);
        }
        if (r.nextInt(3) == 0) {
            int bridgeY = Math.min(h - 2, 8 + r.nextInt(Math.max(1, h - 8)));
            for (int x = -4; x <= 4; x++) set(l, o.offset(x, bridgeY, 0), ModBlocks.BONE_PILLAR);
        }
    }

    private static void decorateCrystal(WorldGenLevel l, BlockPos o, RandomSource r) {
        int crystals = 4 + r.nextInt(8);
        for (int i = 0; i < crystals; i++) {
            int dx = r.nextInt(9) - 4, dz = r.nextInt(9) - 4;
            BlockPos p = l.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, o.offset(dx, 0, dz));
            int h = 1 + r.nextInt(5);
            for (int y = 0; y < h; y++) set(l, p.above(y), y == 0 && r.nextInt(3)==0 ? ModBlocks.CURSED_CRYSTAL_ORE : ModBlocks.CURSED_CRYSTAL_BLOCK);
            if (r.nextInt(4) == 0 && l.getBlockState(p.east()).isAir()) set(l, p.east(), ModBlocks.WITHER_MOSS);
        }
    }
}
