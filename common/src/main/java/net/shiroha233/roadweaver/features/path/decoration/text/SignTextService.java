package net.shiroha233.roadweaver.features.path.decoration.text;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.LevelChunk;
import net.shiroha233.roadweaver.persistence.sharded.SignTextShardStorage;
import net.shiroha233.roadweaver.persistence.sqlite.SignTextSqliteStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 路牌文本服务
 */
public final class SignTextService {
    private SignTextService() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private static final int CHUNK_BUDGET_PER_TICK = 8;
    private static final int WRITE_BUDGET_PER_CHUNK = 32;
    private static final int MAX_DISTANCE_TEXT_LEN = 32;

    private static final ConcurrentHashMap<ServerLevel, ConcurrentLinkedQueue<Long>> DIRTY_CHUNKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, Boolean>> DIRTY_MARKS = new ConcurrentHashMap<>();

    public static void tick(ServerLevel level) {
        if (level == null) return;

        ConcurrentLinkedQueue<Long> queue = DIRTY_CHUNKS.get(level);
        if (queue == null || queue.isEmpty()) return;

        ConcurrentHashMap<Long, Boolean> marks = DIRTY_MARKS.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        int budget = CHUNK_BUDGET_PER_TICK;
        while (budget-- > 0) {
            Long chunkKey = queue.poll();
            if (chunkKey == null) return;
            marks.remove(chunkKey);

            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            processChunk(level, chunkX, chunkZ);
        }
    }

    public static void clearPending() {
        DIRTY_CHUNKS.clear();
        DIRTY_MARKS.clear();
    }

    public static void onChunkReady(ServerLevel level, ChunkPos chunkPos) {
        if (level == null || chunkPos == null) return;
        markChunkDirty(level, chunkPos.x, chunkPos.z);
    }

    public static void onDimensionUnload(ServerLevel level) {
        if (level == null) return;
        DIRTY_CHUNKS.remove(level);
        DIRTY_MARKS.remove(level);
    }

    public static void writeDistanceSign(WorldGenLevel level, BlockPos pos, String text) {
        net.minecraft.world.level.Level l = level.getLevel();
        if (!(l instanceof net.minecraft.server.level.ServerLevel sLevel)) return;

        try {
            String safeText = sanitizeDistanceText(text);
            SignTextShardStorage.upsert(sLevel, pos, SignTextShardStorage.TYPE_DISTANCE, safeText);
            markChunkDirty(sLevel, pos.getX() >> 4, pos.getZ() >> 4);
        } catch (Throwable t) {
            LOGGER.warn("Queue distance sign text failed at {}", pos, t);
        }
    }

    private static boolean tryWriteDistanceSign(ServerLevel sLevel, BlockPos pos, String text) {
        LevelChunk chunk = sLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        BlockEntity be = chunk.getBlockEntity(pos);
        if (!(be instanceof HangingSignBlockEntity sign)) return false;

        SignText front = sign.getText(true);
        front = front.setMessage(0, Component.translatable("gui.roadweaver.sign.next_location"));
        front = front.setMessage(1, Component.literal(text + " m"));
        front = front.setMessage(2, Component.literal(""));
        front = front.setMessage(3, Component.literal(""));
        sign.setText(front, true);

        SignText back = sign.getText(false);
        back = back.setMessage(0, Component.literal("----------"));
        back = back.setMessage(1, Component.translatable("gui.roadweaver.sign.welcome"));
        back = back.setMessage(2, Component.translatable("gui.roadweaver.sign.traveller"));
        back = back.setMessage(3, Component.literal("----------"));
        sign.setText(back, false);

        sign.setChanged();
        return true;
    }

    public static void writeSeaQuestionSign(WorldGenLevel level, BlockPos pos) {
        net.minecraft.world.level.Level l = level.getLevel();
        if (!(l instanceof net.minecraft.server.level.ServerLevel sLevel)) return;

        try {
            SignTextShardStorage.upsert(sLevel, pos, SignTextShardStorage.TYPE_SEA_QUESTION, "");
            markChunkDirty(sLevel, pos.getX() >> 4, pos.getZ() >> 4);
        } catch (Throwable t) {
            LOGGER.warn("Queue sea-question sign text failed at {}", pos, t);
        }
    }

    private static boolean tryWriteSeaQuestionSign(ServerLevel sLevel, BlockPos pos) {
        LevelChunk chunk = sLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        BlockEntity be = chunk.getBlockEntity(pos);
        if (!(be instanceof HangingSignBlockEntity sign)) return false;

        SignText front = sign.getText(true);
        front = front.setMessage(0, Component.translatable("gui.roadweaver.sign.sea_question.line1"));
        front = front.setMessage(1, Component.translatable("gui.roadweaver.sign.sea_question.line2"));
        front = front.setMessage(2, Component.literal(""));
        front = front.setMessage(3, Component.literal(""));
        sign.setText(front, true);

        SignText back = sign.getText(false);
        back = back.setMessage(0, Component.literal(""));
        back = back.setMessage(1, Component.literal(""));
        back = back.setMessage(2, Component.literal(""));
        back = back.setMessage(3, Component.literal(""));
        sign.setText(back, false);

        sign.setChanged();
        return true;
    }

    private static void processChunk(ServerLevel level, int chunkX, int chunkZ) {
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) return;

        List<SignTextSqliteStorage.PendingSignText> pending = SignTextShardStorage.queryByChunk(level, chunkX, chunkZ,
                WRITE_BUDGET_PER_CHUNK);
        if (pending.isEmpty()) return;

        ArrayList<Long> doneIds = new ArrayList<>(pending.size());
        boolean needRetry = false;

        for (SignTextSqliteStorage.PendingSignText row : pending) {
            boolean ok = switch (row.signType()) {
                case SignTextShardStorage.TYPE_DISTANCE -> tryWriteDistanceSign(level, row.pos(), row.payload());
                case SignTextShardStorage.TYPE_SEA_QUESTION -> tryWriteSeaQuestionSign(level, row.pos());
                default -> true;
            };

            if (ok || shouldDrop(level, row.pos())) {
                doneIds.add(row.id());
            } else {
                needRetry = true;
            }
        }

        if (!doneIds.isEmpty()) {
            SignTextShardStorage.deleteByIds(level, doneIds);
        }
        if (needRetry) {
            markChunkDirty(level, chunkX, chunkZ);
        }
    }

    private static void markChunkDirty(ServerLevel level, int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        ConcurrentHashMap<Long, Boolean> marks = DIRTY_MARKS.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        if (marks.putIfAbsent(key, Boolean.TRUE) != null) return;
        DIRTY_CHUNKS.computeIfAbsent(level, l -> new ConcurrentLinkedQueue<>()).add(key);
    }

    private static boolean shouldDrop(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).is(BlockTags.ALL_HANGING_SIGNS);
    }

    private static String sanitizeDistanceText(String text) {
        if (text == null || text.isEmpty()) return "0";
        if (text.length() <= MAX_DISTANCE_TEXT_LEN) return text;
        return text.substring(0, MAX_DISTANCE_TEXT_LEN);
    }
}
