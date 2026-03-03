package net.shiroha233.roadweaver.persistence.sharded;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.persistence.sqlite.SignTextSqliteStorage;

import java.util.List;

/**
 * 路牌文本延迟写入存储门面
 */
public final class SignTextShardStorage {
    private SignTextShardStorage() {}

    public static final int TYPE_DISTANCE = SignTextSqliteStorage.TYPE_DISTANCE;
    public static final int TYPE_SEA_QUESTION = SignTextSqliteStorage.TYPE_SEA_QUESTION;

    public static void upsert(ServerLevel level, BlockPos pos, int signType, String payload) {
        SignTextSqliteStorage.upsert(level, pos, signType, payload);
    }

    public static List<SignTextSqliteStorage.PendingSignText> queryByChunk(ServerLevel level, int chunkX, int chunkZ, int limit) {
        return SignTextSqliteStorage.queryByChunk(level, chunkX, chunkZ, limit);
    }

    public static void deleteByIds(ServerLevel level, List<Long> ids) {
        SignTextSqliteStorage.deleteByIds(level, ids);
    }
}
