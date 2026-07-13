package net.shiroha233.roadweaver.persistence.sqlite;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.persistence.files.SignTextFileStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 路牌文本存储门面，底层已切换到文件型存储。
 */
public final class SignTextSqliteStorage {
    private SignTextSqliteStorage() {}

    public static final int TYPE_DISTANCE = 0;
    public static final int TYPE_SEA_QUESTION = 1;

    public record PendingSignText(long id, BlockPos pos, int signType, String payload) {}

    public record PendingSignWrite(BlockPos pos, int signType, String payload) {}

    public static void upsert(ServerLevel level, BlockPos pos, int signType, String payload) {
        if (!isOverworld(level) || pos == null) return;
        upsertBatch(level, List.of(new PendingSignWrite(pos, signType, payload)));
    }

    public static void upsertBatch(ServerLevel level, Collection<PendingSignWrite> writes) {
        if (!isOverworld(level) || writes == null || writes.isEmpty()) return;
        ArrayList<SignTextFileStorage.PendingSignWrite> batch = new ArrayList<>(writes.size());
        for (PendingSignWrite write : writes) {
            if (write == null || write.pos() == null) continue;
            batch.add(new SignTextFileStorage.PendingSignWrite(write.pos(), write.signType(), write.payload()));
        }
        SignTextFileStorage.upsertBatch(level, batch);
    }

    public static List<PendingSignText> queryByChunk(ServerLevel level, int chunkX, int chunkZ, int limit) {
        if (!isOverworld(level) || limit <= 0) return List.of();
        List<SignTextFileStorage.PendingSignText> rows = SignTextFileStorage.queryByChunk(level, chunkX, chunkZ, limit);
        if (rows.isEmpty()) return List.of();
        ArrayList<PendingSignText> out = new ArrayList<>(rows.size());
        for (SignTextFileStorage.PendingSignText row : rows) {
            out.add(new PendingSignText(row.id(), row.pos(), row.signType(), row.payload()));
        }
        return out;
    }

    public static void deleteByIds(ServerLevel level, List<Long> ids) {
        if (!isOverworld(level)) return;
        SignTextFileStorage.deleteByIds(level, ids);
    }

    private static boolean isOverworld(ServerLevel level) {
        return level != null && Level.OVERWORLD.equals(level.dimension());
    }
}
