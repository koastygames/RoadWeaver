package net.shiroha233.roadweaver.helpers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;

/**
 * Level 相关的兼容工具。
 * 
 * 单一职责：屏蔽跨版本(1.21.x)的API改名/迁移，避免业务代码到处写条件分支。
 */
public final class LevelCompat {
    private LevelCompat() {}

    /**
     * 1.21.10 中 ServerLevel 不再提供 getSharedSpawnPos()。
     * 出生点信息迁移到了 RespawnData 中，这里统一封装。
     */
    public static BlockPos getWorldSpawnPos(ServerLevel level) {
        return getWorldSpawnPos((Level) level);
    }

    /**
     * 客户端/服务端通用的出生点获取。
     */
    public static BlockPos getWorldSpawnPos(Level level) {
        if (level == null) return BlockPos.ZERO;
        try {
            return level.getRespawnData().pos();
        } catch (Throwable t) {
            return BlockPos.ZERO;
        }
    }

    /**
     * 获取玩家所在的 ServerLevel。
     * 1.21.10 中 ServerPlayer#serverLevel() 被移除/改名，改用 level()。
     */
    public static ServerLevel getServerLevel(ServerPlayer player) {
        if (player == null) return null;
        return player.level();
    }

    /**
     * 1.21.10：getMinBuildHeight() -> getMinY()
     */
    public static int getMinY(LevelHeightAccessor level) {
        return level.getMinY();
    }

    /**
     * 1.21.10：getMinSection() -> getMinSectionY()
     */
    public static int getMinSectionY(LevelHeightAccessor level) {
        return level.getMinSectionY();
    }

    /**
     * 1.21.10：ServerPlayer#teleportTo(ServerLevel, x,y,z, yRot,xRot) 重载被移除，改为带相对分量集合的重载。
     * 这里集中封装，避免业务/网络代码到处散落改动。
     */
    public static boolean teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yRot, float xRot) {
        if (player == null || level == null) return false;
        try {
            return player.teleportTo(level, x, y, z,
                java.util.EnumSet.noneOf(net.minecraft.world.entity.Relative.class),
                yRot, xRot, false);
        } catch (Throwable t) {
            try {
                player.teleportTo(x, y, z);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }
}
