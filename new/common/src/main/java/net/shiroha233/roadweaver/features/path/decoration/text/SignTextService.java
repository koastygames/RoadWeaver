package net.shiroha233.roadweaver.features.path.decoration.text;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 路牌文本服务：负责异步写入路牌文本，避免触发同步区块加载。
 */
public final class SignTextService {
    private SignTextService() {}

    private static final int MAX_RETRY_TICKS = 200;
    private static final int PROCESS_BUDGET_PER_TICK = 64;
    private static final ConcurrentLinkedQueue<PendingWrite> PENDING = new ConcurrentLinkedQueue<>();

    private enum PendingType {
        DISTANCE,
        SEA_QUESTION
    }

    private record PendingWrite(BlockPos pos, PendingType type, String text, int triesLeft) {}

    public static void tick(ServerLevel level) {
        if (level == null) return;
        if (PENDING.isEmpty()) return;
        int budget = PROCESS_BUDGET_PER_TICK;
        while (budget-- > 0) {
            PendingWrite pw = PENDING.poll();
            if (pw == null) return;
            if (pw.triesLeft() <= 0) continue;
            boolean ok = switch (pw.type()) {
                case DISTANCE -> tryWriteDistanceSign(level, pw.pos(), pw.text());
                case SEA_QUESTION -> tryWriteSeaQuestionSign(level, pw.pos());
            };
            if (!ok) {
                PENDING.add(new PendingWrite(pw.pos(), pw.type(), pw.text(), pw.triesLeft() - 1));
            }
        }
    }

    public static void clearPending() {
        PENDING.clear();
    }

    public static void writeDistanceSign(WorldGenLevel level, BlockPos pos, String text) {
        net.minecraft.world.level.Level l = level.getLevel();
        if (!(l instanceof net.minecraft.server.level.ServerLevel sLevel)) return;
        sLevel.getServer().execute(() -> {
            boolean ok = tryWriteDistanceSign(sLevel, pos, text);
            if (!ok) {
                PENDING.add(new PendingWrite(pos, PendingType.DISTANCE, text, MAX_RETRY_TICKS));
            }
        });
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
        sLevel.getServer().execute(() -> {
            boolean ok = tryWriteSeaQuestionSign(sLevel, pos);
            if (!ok) {
                PENDING.add(new PendingWrite(pos, PendingType.SEA_QUESTION, "", MAX_RETRY_TICKS));
            }
        });
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
}
