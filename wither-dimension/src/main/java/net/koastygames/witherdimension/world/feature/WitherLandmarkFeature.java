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
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level(); RandomSource r = ctx.random();
        BlockPos origin = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, ctx.origin());
        int variant = r.nextInt(4);
        if (origin.getY() < level.getMinY()+8) return false;
        switch (variant) {
            case 0 -> skeletalTower(level, origin, r);
            case 1 -> mausoleum(level, origin, r);
            case 2 -> ruinedShrine(level, origin, r);
            default -> basaltSpire(level, origin, r);
        }
        return true;
    }
    private static void set(WorldGenLevel l, BlockPos p, Block b){ l.setBlock(p,b.defaultBlockState(),3); }
    private static void skeletalTower(WorldGenLevel l, BlockPos o, RandomSource r){
        int h=18+r.nextInt(10);
        for(int y=0;y<h;y++){
            set(l,o.above(y), ModBlocks.WITHER_STONE);
            if(y%3==0){ for(int d=1;d<=3;d++){ set(l,o.offset(d,y,0),ModBlocks.BONE_BRICKS); set(l,o.offset(-d,y,0),ModBlocks.BONE_BRICKS); }}
        }
        for(int x=-3;x<=3;x++) for(int z=-3;z<=3;z++) if(Math.abs(x)==3||Math.abs(z)==3) set(l,o.offset(x,0,z),ModBlocks.WITHER_STONE);
    }
    private static void mausoleum(WorldGenLevel l, BlockPos o, RandomSource r){
        for(int x=-5;x<=5;x++) for(int z=-7;z<=7;z++) for(int y=0;y<=5;y++){
            boolean shell=y==0||y==5||Math.abs(x)==5||Math.abs(z)==7;
            if(shell) set(l,o.offset(x,y,z), (x+z+y)%5==0?ModBlocks.BONE_BRICKS:ModBlocks.WITHER_STONE);
            else if(y>0) set(l,o.offset(x,y,z),Blocks.AIR);
        }
        for(int y=1;y<4;y++){ set(l,o.offset(-1,y,-7),Blocks.AIR);set(l,o.offset(0,y,-7),Blocks.AIR);set(l,o.offset(1,y,-7),Blocks.AIR); }
        set(l,o.offset(0,1,0),ModBlocks.SOUL_BRAZIER);
    }
    private static void ruinedShrine(WorldGenLevel l, BlockPos o, RandomSource r){
        for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) if(r.nextFloat()>.18F) set(l,o.offset(x,0,z),ModBlocks.ASH_STONE);
        for(int x: new int[]{-3,3}) for(int z:new int[]{-3,3}) for(int y=1;y<=4+r.nextInt(3);y++) set(l,o.offset(x,y,z),ModBlocks.BONE_BRICKS);
        set(l,o.offset(0,1,0),ModBlocks.SOUL_BRAZIER);
    }
    private static void basaltSpire(WorldGenLevel l, BlockPos o, RandomSource r){
        int h=10+r.nextInt(17);
        for(int y=0;y<h;y++){ int rad=Math.max(1,3-y/7); for(int x=-rad;x<=rad;x++)for(int z=-rad;z<=rad;z++) if(x*x+z*z<=rad*rad+1) set(l,o.offset(x,y,z), y%5==0?ModBlocks.BONE_BRICKS:ModBlocks.WITHER_STONE); }
    }
}
