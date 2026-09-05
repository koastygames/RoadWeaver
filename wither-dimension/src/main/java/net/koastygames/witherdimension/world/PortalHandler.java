package net.koastygames.witherdimension.world;

import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.koastygames.witherdimension.WitherDimensionMod;
import net.koastygames.witherdimension.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
            teleport(player, level, locatePortal(level, check));
        }
    }

    private static PortalInfo locatePortal(ServerLevel level, BlockPos inside) {
        Direction.Axis axis = level.getBlockState(inside.east()).is(ModBlocks.WITHER_GATE)
                || level.getBlockState(inside.west()).is(ModBlocks.WITHER_GATE) ? Direction.Axis.X : Direction.Axis.Z;
        BlockPos p = inside;
        while (level.getBlockState(p.below()).is(ModBlocks.WITHER_GATE)) p = p.below();
        Direction negative = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
        while (level.getBlockState(p.relative(negative)).is(ModBlocks.WITHER_GATE)) p = p.relative(negative);
        Direction positive = negative.getOpposite();
        int width = 1;
        BlockPos scan = p;
        while (width < 5 && level.getBlockState(scan.relative(positive)).is(ModBlocks.WITHER_GATE)) {
            scan = scan.relative(positive);
            width++;
        }
        BlockPos center = p.relative(positive, width / 2);
        return new PortalInfo(center, axis);
    }

    private static void teleport(ServerPlayer player, ServerLevel current, PortalInfo portal) {
        ServerLevel target;
        BlockPos arrival;
        if (current.dimension().equals(WitherDimensionMod.WITHER_LEVEL)) {
            target = current.getServer().getLevel(Level.OVERWORLD);
            if (target == null) return;
            if (target.getBlockState(portal.fieldBaseCenter()).is(ModBlocks.WITHER_GATE)) {
                arrival = exitPoint(portal);
            } else {
                BlockPos surface = target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(portal.fieldBaseCenter().getX(), 80, portal.fieldBaseCenter().getZ()));
                arrival = surface.above();
            }
        } else {
            target = current.getServer().getLevel(WitherDimensionMod.WITHER_LEVEL);
            if (target == null) return;
            int safeY = Math.max(-48, Math.min(280, portal.fieldBaseCenter().getY()));
            PortalInfo mapped = new PortalInfo(new BlockPos(portal.fieldBaseCenter().getX(), safeY, portal.fieldBaseCenter().getZ()), portal.axis());
            prepareReturnShrine(target, mapped);
            arrival = exitPoint(mapped);
        }
        player.setPortalCooldown();
        player.teleportTo(target, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5,
                Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
    }

    private static BlockPos exitPoint(PortalInfo portal) {
        return portal.axis() == Direction.Axis.X
                ? portal.fieldBaseCenter().offset(0, 0, 3)
                : portal.fieldBaseCenter().offset(3, 0, 0);
    }

    private static void prepareReturnShrine(ServerLevel level, PortalInfo portal) {
        BlockPos fieldBase = portal.fieldBaseCenter();
        BlockPos floor = fieldBase.below(2);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                level.setBlock(floor.offset(dx, 0, dz), ((dx + dz) & 3) == 0
                        ? ModBlocks.CRACKED_WITHER_BRICKS.defaultBlockState() : ModBlocks.WITHER_BRICKS.defaultBlockState(), 3);
                for (int dy = 1; dy <= 9; dy++) level.setBlock(floor.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        BlockPos frameBase = fieldBase.below();
        for (int horizontal = -2; horizontal <= 2; horizontal++) {
            for (int dy = 0; dy <= 6; dy++) {
                boolean border = horizontal == -2 || horizontal == 2 || dy == 0 || dy == 6;
                BlockPos p = offset(frameBase, portal.axis(), horizontal, dy);
                level.setBlock(p, border ? ModBlocks.WITHERED_OBSIDIAN.defaultBlockState() : ModBlocks.WITHER_GATE.defaultBlockState(), 3);
            }
        }

        for (int side : new int[]{-4, 4}) {
            BlockPos column = offset(frameBase, portal.axis(), side, 0);
            for (int y = 0; y <= 5; y++) level.setBlock(column.above(y), ModBlocks.BONE_PILLAR.defaultBlockState(), 3);
            level.setBlock(column.above(6), ModBlocks.SKULL_LANTERN.defaultBlockState(), 3);
        }

        BlockPos brazierA = portal.axis() == Direction.Axis.X ? frameBase.offset(-3, 0, 2) : frameBase.offset(2, 0, -3);
        BlockPos brazierB = portal.axis() == Direction.Axis.X ? frameBase.offset(3, 0, 2) : frameBase.offset(2, 0, 3);
        level.setBlock(brazierA, ModBlocks.SOUL_BRAZIER.defaultBlockState(), 3);
        level.setBlock(brazierB, ModBlocks.SOUL_BRAZIER.defaultBlockState(), 3);
    }

    private static BlockPos offset(BlockPos base, Direction.Axis axis, int horizontal, int dy) {
        return axis == Direction.Axis.X ? base.offset(horizontal, dy, 0) : base.offset(0, dy, horizontal);
    }

    private record PortalInfo(BlockPos fieldBaseCenter, Direction.Axis axis) { }
}
