package net.koastygames.witherdimension.block;

import java.util.Set;
import net.koastygames.witherdimension.WitherDimensionMod;
import net.koastygames.witherdimension.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class WitherGateBlock extends Block {
    public WitherGateBlock(Properties properties) { super(properties); }
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player) || player.isPassenger()) return;
        if (player.getPortalCooldown() > 0) return;
        ServerLevel current=(ServerLevel)level; ServerLevel target; BlockPos arrival;
        if(current.dimension().equals(WitherDimensionMod.WITHER_LEVEL)){
            target=current.getServer().getLevel(Level.OVERWORLD); if(target==null)return; arrival=target.getSharedSpawnPos().above();
        } else {
            target=current.getServer().getLevel(WitherDimensionMod.WITHER_LEVEL); if(target==null)return; arrival=new BlockPos(0,72,0); prepareArrival(target,arrival);
        }
        player.setPortalCooldown();
        player.teleportTo(target,arrival.getX()+0.5,arrival.getY()+1.0,arrival.getZ()+0.5, Set.<Relative>of(),player.getYRot(),player.getXRot(),false);
    }
    private static void prepareArrival(ServerLevel level, BlockPos c){
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            level.setBlock(c.offset(dx,0,dz),ModBlocks.WITHER_STONE.defaultBlockState(),3);
            for(int dy=1;dy<=4;dy++)level.setBlock(c.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),3);
        }
    }
}
