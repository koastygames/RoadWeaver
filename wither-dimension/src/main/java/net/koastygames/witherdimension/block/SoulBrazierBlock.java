package net.koastygames.witherdimension.block;

import net.koastygames.witherdimension.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class SoulBrazierBlock extends Block {
    public SoulBrazierBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.NETHER_STAR)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockPos plane = pos.north();
        if (!hasFrame(level, plane)) {
            player.sendSystemMessage(Component.translatable("message.witherdimension.invalid_gate"));
            return InteractionResult.FAIL;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                level.setBlock(plane.offset(dx, dy, 0), ModBlocks.WITHER_GATE.defaultBlockState(), 3);
            }
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.sendSystemMessage(Component.translatable("message.witherdimension.gate_open"));
        return InteractionResult.SUCCESS;
    }

    private boolean hasFrame(Level level, BlockPos baseCenter) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 5; dy++) {
                boolean border = dx == -2 || dx == 2 || dy == 0 || dy == 5;
                BlockPos p = baseCenter.offset(dx, dy, 0);
                if (border) {
                    if (!level.getBlockState(p).is(ModBlocks.WITHERED_OBSIDIAN)) return false;
                } else if (!level.getBlockState(p).isAir() && !level.getBlockState(p).is(ModBlocks.WITHER_GATE)) {
                    return false;
                }
            }
        }
        return true;
    }
}
