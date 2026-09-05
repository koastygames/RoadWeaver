package net.koastygames.witherdimension.world;

import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.koastygames.witherdimension.WitherDimensionMod;
import net.koastygames.witherdimension.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

public final class PortalHandler {
    private PortalHandler() { }

    public static void initialize() {
        ServerTickEvents.END_LEVEL_TICK.register(PortalHandler::tickLevel);
    }

    private static void tickLevel(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.isPassenger() || player.getPortalCooldown() > 0) continue;
            BlockPos feet = player.blockPosition();
            BlockPos check = level.getBlockState(feet).is(ModBlocks.WITHER_GATE) ? feet : feet.above();
            if (!level.getBlockState(check).is(ModBlocks.WITHER_GATE)) continue;
            teleport(player, level);
        }
    }

    private static void teleport(ServerPlayer player, ServerLevel current) {
        ServerLevel target;
        BlockPos arrival;
        if (current.dimension().equals(WitherDimensionMod.WITHER_LEVEL)) {
            target = current.getServer().getLevel(Level.OVERWORLD);
            if (target == null) return;
            BlockPos base = target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(0, 80, 0));
            arrival = base.above();
        } else {
            target = current.getServer().getLevel(WitherDimensionMod.WITHER_LEVEL);
            if (target == null) return;
            arrival = new BlockPos(0, 72, 0);
            prepareArrival(target, arrival);
        }
        player.setPortalCooldown();
        player.teleportTo(target, arrival.getX() + 0.5, arrival.getY() + 1.0, arrival.getZ() + 0.5,
                Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
    }

    private static void prepareArrival(ServerLevel level, BlockPos center) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlock(center.offset(dx, 0, dz), ModBlocks.WITHER_STONE.defaultBlockState(), 3);
                for (int dy = 1; dy <= 4; dy++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }
}
