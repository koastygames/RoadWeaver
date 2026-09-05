package net.koastygames.witherdimension.block;

import net.koastygames.witherdimension.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

        GateFrame frame = findFrame(level, pos);
        if (frame == null) {
            player.sendSystemMessage(Component.translatable("message.witherdimension.invalid_gate"));
            return InteractionResult.FAIL;
        }

        fillPortal(level, frame);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.sendSystemMessage(Component.translatable("message.witherdimension.gate_open"));
        return InteractionResult.SUCCESS;
    }

    private GateFrame findFrame(Level level, BlockPos brazierPos) {
        GateFrame[] candidates = new GateFrame[] {
                new GateFrame(brazierPos.north(), Direction.Axis.X),
                new GateFrame(brazierPos.south(), Direction.Axis.X),
                new GateFrame(brazierPos.east(), Direction.Axis.Z),
                new GateFrame(brazierPos.west(), Direction.Axis.Z)
        };
        for (GateFrame candidate : candidates) if (hasFrame(level, candidate)) return candidate;
        return null;
    }

    private boolean hasFrame(Level level, GateFrame frame) {
        // Concept-art proportions: 5 blocks wide x 7 blocks tall, leaving a 3 x 5 portal field.
        for (int horizontal = -2; horizontal <= 2; horizontal++) {
            for (int dy = 0; dy <= 6; dy++) {
                boolean border = horizontal == -2 || horizontal == 2 || dy == 0 || dy == 6;
                BlockPos p = offset(frame.baseCenter(), frame.axis(), horizontal, dy);
                if (border) {
                    if (!level.getBlockState(p).is(ModBlocks.WITHERED_OBSIDIAN)) return false;
                } else if (!level.getBlockState(p).isAir() && !level.getBlockState(p).is(ModBlocks.WITHER_GATE)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void fillPortal(Level level, GateFrame frame) {
        for (int horizontal = -1; horizontal <= 1; horizontal++) {
            for (int dy = 1; dy <= 5; dy++) {
                BlockPos p = offset(frame.baseCenter(), frame.axis(), horizontal, dy);
                level.setBlock(p, ModBlocks.WITHER_GATE.defaultBlockState(), 3);
            }
        }
    }

    private static BlockPos offset(BlockPos baseCenter, Direction.Axis axis, int horizontal, int dy) {
        return axis == Direction.Axis.X ? baseCenter.offset(horizontal, dy, 0) : baseCenter.offset(0, dy, horizontal);
    }

    private record GateFrame(BlockPos baseCenter, Direction.Axis axis) { }
}
