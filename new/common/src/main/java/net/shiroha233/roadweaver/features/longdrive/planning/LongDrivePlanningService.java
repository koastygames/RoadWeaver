package net.shiroha233.roadweaver.features.longdrive.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.features.longdrive.config.LongDriveGenerationConfig;
import net.shiroha233.roadweaver.features.longdrive.generation.LongDriveRoad;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.util.ComputeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 长途驾驶主干道规划服务
 * 维护主干道"路头"位置，确保路头始终领先玩家 leadDistance 格
 */
public final class LongDrivePlanningService {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");

    private static final ConcurrentHashMap<Level, RoadHead> ROAD_HEADS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Level, AtomicBoolean> EXTENDING = new ConcurrentHashMap<>();

    private LongDrivePlanningService() {}

    public static void resetAll() {
        ROAD_HEADS.clear();
        EXTENDING.clear();
    }

    /**
     * 初始规划：从出生点开始生成第一段主干道
     */
    public static void initialPlan(ServerLevel level) {
        if (level == null) return;
        ModConfig cfg = ConfigService.get();
        if (!cfg.longDrive().enabled()) return;

        BlockPos spawn = level.getSharedSpawnPos();
        int sy = level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                spawn.getX(), spawn.getZ());
        BlockPos start = new BlockPos(spawn.getX(), sy, spawn.getZ());

        double[] dir = computeDirection(level);
        LongDriveGenerationConfig genCfg = LongDriveGenerationConfig.from(cfg);

        LongDriveRoad road = new LongDriveRoad(level, start, dir[0], dir[1], genCfg);
        BlockPos end = road.generate(genCfg.segmentLength());

        if (end != null) {
            ROAD_HEADS.put(level, new RoadHead(end, dir[0], dir[1]));
            LOGGER.info("LongDrive: initial segment generated, head at {}", end);
        } else {
            ROAD_HEADS.put(level, new RoadHead(start, dir[0], dir[1]));
            LOGGER.warn("LongDrive: initial segment failed, head stays at {}", start);
        }
    }

    /**
     * 玩家移动时检查是否需要延伸主干道
     */
    public static void tickPlayer(ServerPlayer player) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();
        ModConfig cfg = ConfigService.get();
        if (!cfg.longDrive().enabled()) return;

        RoadHead head = ROAD_HEADS.get(level);
        if (head == null) return;

        int leadDist = cfg.longDrive().leadDistance();
        double dx = head.pos.getX() - player.getX();
        double dz = head.pos.getZ() - player.getZ();
        double distSq = dx * dx + dz * dz;

        if (distSq < (long) leadDist * leadDist) {
            AtomicBoolean extending = EXTENDING.computeIfAbsent(level, l -> new AtomicBoolean(false));
            if (!extending.compareAndSet(false, true)) return;

            final long epoch = ThreadPoolManager.currentEpoch();
            final LongDriveGenerationConfig genCfg = LongDriveGenerationConfig.from(cfg);
            final RoadHead currentHead = head;

            ComputeService.supplyAsync(() -> {
                if (Thread.currentThread().isInterrupted()) return null;
                if (!ThreadPoolManager.isEpoch(epoch)) return null;

                LongDriveRoad road = new LongDriveRoad(
                        level, currentHead.pos, currentHead.dirX, currentHead.dirZ, genCfg);
                return road.generate(genCfg.segmentLength());
            }).thenAccept(newEnd -> {
                try {
                    if (newEnd == null || !ThreadPoolManager.isEpoch(epoch)) return;
                    var server = level.getServer();
                    if (server == null) return;
                    server.execute(() -> {
                        if (!ThreadPoolManager.isEpoch(epoch)) return;
                        ROAD_HEADS.put(level, new RoadHead(newEnd, currentHead.dirX, currentHead.dirZ));
                    });
                } finally {
                    extending.set(false);
                }
            }).exceptionally(t -> {
                LOGGER.warn("LongDrive: extend failed", t);
                extending.set(false);
                return null;
            });
        }
    }

    /**
     * 基于世界种子计算主干道大方向（归一化 XZ 向量）
     */
    static double[] computeDirection(ServerLevel level) {
        long seed = level.getSeed();
        double angle = ((seed & 0xFFFF) / 65536.0) * Math.PI * 2.0;
        return new double[]{Math.cos(angle), Math.sin(angle)};
    }

    public static BlockPos getRoadHead(ServerLevel level) {
        RoadHead h = ROAD_HEADS.get(level);
        return h != null ? h.pos : null;
    }

    private static final class RoadHead {
        final BlockPos pos;
        final double dirX;
        final double dirZ;

        RoadHead(BlockPos pos, double dirX, double dirZ) {
            this.pos = pos;
            this.dirX = dirX;
            this.dirZ = dirZ;
        }
    }
}
