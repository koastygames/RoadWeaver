package net.koastygames.witherdimension.world.feature;

import com.mojang.serialization.Codec;
import net.koastygames.witherdimension.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class WitherLandmarkFeature extends Feature<NoneFeatureConfiguration> {
    public WitherLandmarkFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos origin = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, ctx.origin());
        if (origin.getY() < level.getMinY() + 8) return false;
        switch (r.nextInt(4)) {
            case 0 -> skeletalTower(level, origin, r);
            case 1 -> mausoleum(level, origin, r);
            case 2 -> ruinedShrine(level, origin, r);
            default -> ruinedCitadel(level, origin, r);
        }
        return true;
    }

    private static void set(WorldGenLevel l, BlockPos p, Block b) { l.setBlock(p, b.defaultBlockState(), 3); }
    private static void clear(WorldGenLevel l, BlockPos p) { l.setBlock(p, Blocks.AIR.defaultBlockState(), 3); }

    private static void floor(WorldGenLevel l, BlockPos o, int rx, int rz, Block a, Block b) {
        for (int x=-rx; x<=rx; x++) for (int z=-rz; z<=rz; z++) set(l, o.offset(x,0,z), ((x+z)&3)==0 ? b : a);
    }

    private static void boneColumn(WorldGenLevel l, BlockPos p, int h) {
        for (int y=0; y<h; y++) set(l, p.above(y), ModBlocks.BONE_PILLAR);
        set(l, p.above(h), ModBlocks.SKULL_LANTERN);
    }

    private static void skeletalTower(WorldGenLevel l, BlockPos o, RandomSource r) {
        int h = 34 + r.nextInt(13);
        floor(l, o, 6, 6, ModBlocks.WITHER_BRICKS, ModBlocks.CRACKED_WITHER_BRICKS);
        for (int y=1; y<=h; y++) {
            int rad = y < 5 ? 3 : 2;
            for (int x=-rad; x<=rad; x++) for (int z=-rad; z<=rad; z++) {
                boolean shell = Math.abs(x)==rad || Math.abs(z)==rad;
                if (shell) set(l, o.offset(x,y,z), y%7==0 ? ModBlocks.BONE_BRICKS : ModBlocks.POLISHED_WITHER_STONE);
                else clear(l, o.offset(x,y,z));
            }
            if (y%6==0) {
                for (int d=3; d<=6; d++) {
                    set(l,o.offset(d,y,0),ModBlocks.BONE_PILLAR); set(l,o.offset(-d,y,0),ModBlocks.BONE_PILLAR);
                    set(l,o.offset(0,y,d),ModBlocks.BONE_PILLAR); set(l,o.offset(0,y,-d),ModBlocks.BONE_PILLAR);
                }
            }
        }
        for (int x=-5;x<=5;x++) for(int z=-5;z<=5;z++) if(Math.abs(x)==5||Math.abs(z)==5) set(l,o.offset(x,h+1,z),ModBlocks.BONE_BRICKS);
        for (int y=h+2; y<=h+8; y++) set(l,o.above(y),ModBlocks.BONE_PILLAR);
        set(l,o.above(h+9),ModBlocks.SKULL_LANTERN);
        set(l,o.offset(0,1,0),ModBlocks.SOUL_BRAZIER);
        for (int x : new int[]{-5,5}) for (int z : new int[]{-5,5}) boneColumn(l,o.offset(x,1,z),8);
    }

    private static void mausoleum(WorldGenLevel l, BlockPos o, RandomSource r) {
        floor(l,o,10,14,ModBlocks.WITHER_BRICKS,ModBlocks.CRACKED_WITHER_BRICKS);
        for (int y=1;y<=9;y++) for(int x=-10;x<=10;x++) for(int z=-14;z<=14;z++) {
            boolean shell = Math.abs(x)==10 || Math.abs(z)==14;
            if (shell) set(l,o.offset(x,y,z), (y%4==0 || (x+z)%7==0) ? ModBlocks.BONE_BRICKS : ModBlocks.WITHER_BRICKS);
            else if(y<=8) clear(l,o.offset(x,y,z));
        }
        for(int x=-1;x<=1;x++) for(int y=1;y<=5;y++) clear(l,o.offset(x,y,-14));
        for(int x=-8;x<=8;x+=4) {
            boneColumn(l,o.offset(x,1,-11),7);
            boneColumn(l,o.offset(x,1,11),7);
        }
        for(int z=-8;z<=8;z+=4) {
            boneColumn(l,o.offset(-7,1,z),7);
            boneColumn(l,o.offset(7,1,z),7);
        }
        for(int tier=0;tier<5;tier++) {
            int rx=10-tier, rz=14-tier, y=10+tier;
            for(int x=-rx;x<=rx;x++) for(int z=-rz;z<=rz;z++) if(Math.abs(x)==rx||Math.abs(z)==rz) set(l,o.offset(x,y,z), tier%2==0?ModBlocks.BONE_BRICKS:ModBlocks.POLISHED_WITHER_STONE);
        }
        for(int y=1;y<=4;y++) for(int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) if(Math.abs(x)==2||Math.abs(z)==2) set(l,o.offset(x,y,z),ModBlocks.VOID_INFUSED_STONE);
        set(l,o.offset(0,1,0),ModBlocks.SOUL_BRAZIER);
        set(l,o.offset(-6,1,-8),ModBlocks.SKULL_LANTERN); set(l,o.offset(6,1,-8),ModBlocks.SKULL_LANTERN);
        set(l,o.offset(-6,1,8),ModBlocks.SKULL_LANTERN); set(l,o.offset(6,1,8),ModBlocks.SKULL_LANTERN);
    }

    private static void ruinedShrine(WorldGenLevel l, BlockPos o, RandomSource r) {
        floor(l,o,7,7,ModBlocks.ASH_BRICKS,ModBlocks.CRACKED_WITHER_BRICKS);
        for(int x : new int[]{-5,5}) for(int z : new int[]{-5,5}) boneColumn(l,o.offset(x,1,z),5+r.nextInt(3));
        // A discoverable ruined Wither Gate echo, intentionally incomplete so the player still earns/builds their own gate.
        for(int x=-2;x<=2;x++) { set(l,o.offset(x,1,2),ModBlocks.WITHERED_OBSIDIAN); set(l,o.offset(x,6,2),ModBlocks.WITHERED_OBSIDIAN); }
        for(int y=2;y<=5;y++) { set(l,o.offset(-2,y,2),ModBlocks.WITHERED_OBSIDIAN); if(y!=4) set(l,o.offset(2,y,2),ModBlocks.WITHERED_OBSIDIAN); }
        set(l,o.offset(0,1,-1),ModBlocks.SOUL_BRAZIER);
        for(int i=0;i<12;i++) {
            int x=r.nextInt(13)-6,z=r.nextInt(13)-6;
            if(r.nextBoolean()) set(l,o.offset(x,1,z),ModBlocks.WITHER_MOSS);
        }
    }

    private static void ruinedCitadel(WorldGenLevel l, BlockPos o, RandomSource r) {
        floor(l,o,8,8,ModBlocks.POLISHED_WITHER_STONE,ModBlocks.VOID_INFUSED_STONE);
        for(int x : new int[]{-6,6}) for(int z:new int[]{-6,6}) {
            int h=14+r.nextInt(8);
            for(int y=1;y<h;y++) for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) set(l,o.offset(x+dx,y,z+dz), y%5==0?ModBlocks.BONE_BRICKS:ModBlocks.WITHER_BRICKS);
            set(l,o.offset(x,h,z),ModBlocks.SKULL_LANTERN);
        }
        for(int d=-6;d<=6;d++) {
            set(l,o.offset(d,8,-6),ModBlocks.BONE_PILLAR); set(l,o.offset(d,8,6),ModBlocks.BONE_PILLAR);
            set(l,o.offset(-6,8,d),ModBlocks.BONE_PILLAR); set(l,o.offset(6,8,d),ModBlocks.BONE_PILLAR);
        }
        set(l,o.offset(0,1,0),ModBlocks.SOUL_BRAZIER);
    }
}
