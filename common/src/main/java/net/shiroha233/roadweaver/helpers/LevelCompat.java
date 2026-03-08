package net.shiroha233.roadweaver.helpers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;

public final class LevelCompat {
    private LevelCompat() {
    }

    public static BlockPos getWorldSpawnPos(Level level) {
        if (level == null) {
            return BlockPos.ZERO;
        }
        LevelData.RespawnData respawnData = level.getLevelData().getRespawnData();
        return respawnData != null ? respawnData.pos() : BlockPos.ZERO;
    }
}
