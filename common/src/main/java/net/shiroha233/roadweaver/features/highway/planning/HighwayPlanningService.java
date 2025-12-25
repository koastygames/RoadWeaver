package net.shiroha233.roadweaver.features.highway.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.planning.HighwayCellPathPlanningService;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.util.ComputeService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highway 规划服务。
 *
 * 职责:
 * - 基于世界坐标网格（每 1000 方块一个网格点）生成 Highway 相邻连接边，并写入 WorldDataProvider 的 highwayConnections。
 * - 不修改 path 的连接列表。
 */
public final class HighwayPlanningService {
    private HighwayPlanningService() {}

    private static final class WindowCenter {
        private int gx;
        private int gz;
        // 是否已进入“九宫格动态规划”模式。
        // 设计目的：服务器启动/初始生成阶段仅规划 1x1 cell，避免启动即铺满 3x3；
        // 玩家进入游戏后再触发一次 3x3 规划。
        private boolean dynamicActivated;

        private WindowCenter(int gx, int gz, boolean dynamicActivated) {
            this.gx = gx;
            this.gz = gz;
            this.dynamicActivated = dynamicActivated;
        }
    }

    // 以“Highway 网格单元格（cell）”为单位的滚动窗口中心。
    private static final ConcurrentHashMap<Level, WindowCenter> WINDOW_CENTERS = new ConcurrentHashMap<>();

    public static void resetAll() {
        WINDOW_CENTERS.clear();
    }

    public static void initialPlan(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return;
        ModConfig cfg = ConfigService.get();
        String dimId = level.dimension().location().toString();
        if (!cfg.highwayEnabledForDimension(dimId) || !cfg.highwayAutoPlanEnabled()) return;

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        // 初次加载：只加载“玩家当前所在的 1x1 cell”。
        // 原理：避免服务器启动即铺满大范围窗口；同时确保窗口/网格以玩家所在 cell 为中心。
        BlockPos centerPos = level.getSharedSpawnPos();
        var server = level.getServer();
        if (server != null) {
            var p = server.getPlayerList().getPlayers().stream()
                    .filter(sp -> sp != null && sp.serverLevel() == level)
                    .findFirst()
                    .orElse(null);
            if (p != null) {
                centerPos = p.blockPosition();
            }
        }

        int cellGx = floorDiv(centerPos.getX(), gridBlocks);
        int cellGz = floorDiv(centerPos.getZ(), gridBlocks);
        // 初始阶段始终只规划 1x1 cell。
        // 九宫格动态规划将由 planAroundPlayer 在玩家进入游戏后首次触发。
        WINDOW_CENTERS.put(level, new WindowCenter(cellGx, cellGz, false));
        refreshSingleCell(level, cfg, cellGx, cellGz);
    }

    public static CompletableFuture<Void> initialPlanAsync(ServerLevel level) {
        if (level == null || !Level.OVERWORLD.equals(level.dimension())) return CompletableFuture.completedFuture(null);
        ModConfig cfg = ConfigService.get();
        String dimId = level.dimension().location().toString();
        if (!cfg.highwayEnabledForDimension(dimId) || !cfg.highwayAutoPlanEnabled()) return CompletableFuture.completedFuture(null);

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        BlockPos centerPos = level.getSharedSpawnPos();
        var server = level.getServer();
        if (server != null) {
            var p = server.getPlayerList().getPlayers().stream()
                    .filter(sp -> sp != null && sp.serverLevel() == level)
                    .findFirst()
                    .orElse(null);
            if (p != null) {
                centerPos = p.blockPosition();
            }
        }

        int cellGx = floorDiv(centerPos.getX(), gridBlocks);
        int cellGz = floorDiv(centerPos.getZ(), gridBlocks);
        // 初始阶段始终只规划 1x1 cell。
        WINDOW_CENTERS.put(level, new WindowCenter(cellGx, cellGz, false));
        return refreshSingleCellAsync(level, cfg, cellGx, cellGz);
    }

    private static void refreshSingleCell(ServerLevel level, ModConfig cfg, int cellGx, int cellGz) {
        if (level == null || cfg == null) return;
        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());

        // 1x1 cell 的边界点是 2x2 点阵
        int minPointGx = cellGx;
        int maxPointGx = cellGx + 1;
        int minPointGz = cellGz;
        int maxPointGz = cellGz + 1;

        int minX = minPointGx * gridBlocks;
        int maxX = maxPointGx * gridBlocks;
        int minZ = minPointGz * gridBlocks;
        int maxZ = maxPointGz * gridBlocks;
        planRect(level, minX, minZ, maxX, maxZ);

        HighwayCellPathPlanningService.retainPlannedCellsInRect(level, cellGx, cellGz, cellGx, cellGz);
        int cellMinX = cellGx * gridBlocks;
        int cellMaxX = (cellGx + 1) * gridBlocks;
        int cellMinZ = cellGz * gridBlocks;
        int cellMaxZ = (cellGz + 1) * gridBlocks;
        HighwayCellPathPlanningService.planCompletedCellsInRect(level, cellMinX, cellMinZ, cellMaxX, cellMaxZ);
    }

    private static CompletableFuture<Void> refreshSingleCellAsync(ServerLevel level, ModConfig cfg, int cellGx, int cellGz) {
        if (level == null || cfg == null) return CompletableFuture.completedFuture(null);
        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());

        int minPointGx = cellGx;
        int maxPointGx = cellGx + 1;
        int minPointGz = cellGz;
        int maxPointGz = cellGz + 1;

        int minX = minPointGx * gridBlocks;
        int maxX = maxPointGx * gridBlocks;
        int minZ = minPointGz * gridBlocks;
        int maxZ = maxPointGz * gridBlocks;

        HighwayCellPathPlanningService.retainPlannedCellsInRect(level, cellGx, cellGz, cellGx, cellGz);

        int cellMinX = cellGx * gridBlocks;
        int cellMaxX = (cellGx + 1) * gridBlocks;
        int cellMinZ = cellGz * gridBlocks;
        int cellMaxZ = (cellGz + 1) * gridBlocks;

        return planRectAsync(level, minX, minZ, maxX, maxZ).thenRun(() -> {
            var server = level.getServer();
            if (server == null) return;
            server.execute(() -> HighwayCellPathPlanningService.planCompletedCellsInRect(level, cellMinX, cellMinZ, cellMaxX, cellMaxZ));
        });
    }

    public static void planAroundPlayer(ServerPlayer player) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();
        if (!Level.OVERWORLD.equals(level.dimension())) return;

        ModConfig cfg = ConfigService.get();
        String dimId = level.dimension().location().toString();
        if (!cfg.highwayEnabledForDimension(dimId) || !cfg.highwayAutoPlanEnabled()) return;

        WindowCenter center = WINDOW_CENTERS.get(level);
        if (center == null) {
            int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
            int playerCellGx = floorDiv(player.getBlockX(), gridBlocks);
            int playerCellGz = floorDiv(player.getBlockZ(), gridBlocks);
            // 玩家已进入游戏：若启用动态规划，则直接激活九宫格。
            center = new WindowCenter(playerCellGx, playerCellGz, cfg.highwayDynamicPlanEnabled());
            WINDOW_CENTERS.put(level, center);
            if (cfg.highwayDynamicPlanEnabled()) {
                refreshWindowAsync(level, cfg, playerCellGx, playerCellGz);
            } else {
                refreshSingleCellAsync(level, cfg, playerCellGx, playerCellGz);
            }
            return;
        }

        // 若动态规划开关被关闭：回退到 1x1，且允许未来再次开启时重新“激活九宫格”。
        if (!cfg.highwayDynamicPlanEnabled()) {
            center.dynamicActivated = false;
        }

        // 启用了动态规划，但当前还处于“初始阶段仅 1x1 cell”的状态：
        // 玩家进入游戏后首次触发九宫格规划。
        if (cfg.highwayDynamicPlanEnabled() && !center.dynamicActivated) {
            int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
            int playerCellGx = floorDiv(player.getBlockX(), gridBlocks);
            int playerCellGz = floorDiv(player.getBlockZ(), gridBlocks);
            center.gx = playerCellGx;
            center.gz = playerCellGz;
            center.dynamicActivated = true;
            refreshWindowAsync(level, cfg, center.gx, center.gz);
            return;
        }

        // 若未开启动态拓展：保持“仅 1x1 cell”，并且让该 cell 永远跟随玩家（玩家居中）。
        // 这样不会铺满 3x3，也符合“初次加载只加载玩家所处方格”。
        if (!cfg.highwayDynamicPlanEnabled()) {
            int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
            int playerCellGx = floorDiv(player.getBlockX(), gridBlocks);
            int playerCellGz = floorDiv(player.getBlockZ(), gridBlocks);
            if (playerCellGx != center.gx || playerCellGz != center.gz) {
                center.gx = playerCellGx;
                center.gz = playerCellGz;
                refreshSingleCellAsync(level, cfg, playerCellGx, playerCellGz);
            }
            return;
        }

        // 以玩家所在 cell 为依据维护 3x3 滚动窗口：
        // - 玩家进入窗口边缘 cell（相对中心 dx/dz 为 +/-1）时，窗口中心滚动到玩家所在 cell
        // - 滚动窗口仅用于“增量补齐”窗口内的规划边；不会删除历史 highwayConnections
        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        int playerCellGx = floorDiv(player.getBlockX(), gridBlocks);
        int playerCellGz = floorDiv(player.getBlockZ(), gridBlocks);

        int dx = playerCellGx - center.gx;
        int dz = playerCellGz - center.gz;
        if (dx == 0 && dz == 0) return;

        // 玩家跨越多个 cell（传送/快速移动）时，直接重置窗口中心
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) {
            center.gx = playerCellGx;
            center.gz = playerCellGz;
            refreshWindowAsync(level, cfg, center.gx, center.gz);
            return;
        }

        // 玩家进入边缘 cell：滚动窗口，让玩家回到新窗口中心
        if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
            center.gx += dx;
            center.gz += dz;
            refreshWindowAsync(level, cfg, center.gx, center.gz);
        }
    }

    private static CompletableFuture<Void> refreshWindowAsync(ServerLevel level, ModConfig cfg, int centerCellGx, int centerCellGz) {
        if (level == null || cfg == null) return CompletableFuture.completedFuture(null);

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        int minPointGx = centerCellGx - 1;
        int maxPointGx = centerCellGx + 2;
        int minPointGz = centerCellGz - 1;
        int maxPointGz = centerCellGz + 2;

        int minX = minPointGx * gridBlocks;
        int maxX = maxPointGx * gridBlocks;
        int minZ = minPointGz * gridBlocks;
        int maxZ = maxPointGz * gridBlocks;

        int minCellGx = centerCellGx - 1;
        int maxCellGx = centerCellGx + 1;
        int minCellGz = centerCellGz - 1;
        int maxCellGz = centerCellGz + 1;
        HighwayCellPathPlanningService.retainPlannedCellsInRect(level, minCellGx, minCellGz, maxCellGx, maxCellGz);

        int cellMinX = minCellGx * gridBlocks;
        int cellMaxX = (maxCellGx + 1) * gridBlocks;
        int cellMinZ = minCellGz * gridBlocks;
        int cellMaxZ = (maxCellGz + 1) * gridBlocks;

        // 先异步补齐窗口内的 PLANNED 边，然后在主线程尝试触发已完成 cell 的回补。
        return planRectAsync(level, minX, minZ, maxX, maxZ).thenRun(() -> {
            var server = level.getServer();
            if (server == null) return;
            server.execute(() -> HighwayCellPathPlanningService.planCompletedCellsInRect(level, cellMinX, cellMinZ, cellMaxX, cellMaxZ));
        });
    }

    private static void planRect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        int gridBlocks = Math.max(1, ConfigService.get().highwayGridBlocks());
        List<Records.StructureConnection> planned = buildGridConnections(gridBlocks, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        if (planned.isEmpty()) return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<Records.StructureConnection> existing = provider.getHighwayConnections(level);
        List<Records.StructureConnection> merged = mergeConnections(existing, planned);
        if (merged.size() != (existing == null ? 0 : existing.size())) {
            provider.setHighwayConnections(level, merged);
        }
    }

    public static CompletableFuture<Void> planRectAsync(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        final long epoch = ThreadPoolManager.currentEpoch();
        final ModConfig cfgSnap = ConfigService.get();
        final int gridBlocks = Math.max(1, cfgSnap.highwayGridBlocks());

        return ComputeService.supplyAsync(() -> {
            if (Thread.currentThread().isInterrupted()) return new ArrayList<Records.StructureConnection>();
            if (!ThreadPoolManager.isEpoch(epoch)) return new ArrayList<Records.StructureConnection>();

            // 注意：这里需要使用同一份 cfgSnap 的网格间距，避免规划线程运行期间配置变化导致“网格错位”。
            if (cfgSnap == null || !cfgSnap.highwayEnabled() || !cfgSnap.highwayAutoPlanEnabled()) {
                return new ArrayList<Records.StructureConnection>();
            }

            return new ArrayList<>(buildGridConnections(gridBlocks, minBlockX, minBlockZ, maxBlockX, maxBlockZ));
        }).thenAccept(incoming -> {
            if (incoming == null || incoming.isEmpty()) return;
            if (!ThreadPoolManager.isEpoch(epoch)) return;
            var server = level.getServer();
            if (server == null) return;

            server.execute(() -> {
                if (!ThreadPoolManager.isEpoch(epoch)) return;
                WorldDataProvider provider = WorldDataProvider.getInstance();
                List<Records.StructureConnection> existing = provider.getHighwayConnections(level);
                List<Records.StructureConnection> merged = mergeConnections(existing, incoming);
                if (merged.size() != (existing == null ? 0 : existing.size())) {
                    provider.setHighwayConnections(level, merged);
                }
            });
        });
    }

    private static List<Records.StructureConnection> buildGridConnections(int gridBlocks,
                                                                          int minBlockX,
                                                                           int minBlockZ,
                                                                           int maxBlockX,
                                                                           int maxBlockZ) {
        // 注意：这里不再固定“扩一圈”。
        // 原因：当 gridBlocks 很大（例如 1000/2000）时，扩一圈会让实际规划范围
        // 比配置的 radiusChunks 大出接近 1 个 gridBlocks，体感差距非常明显。
        // 边界补边由后续动态规划/再次调用 planRect 补齐即可。
        int gx0 = floorDiv(minBlockX, gridBlocks);
        int gz0 = floorDiv(minBlockZ, gridBlocks);
        int gx1 = floorDiv(maxBlockX, gridBlocks);
        int gz1 = floorDiv(maxBlockZ, gridBlocks);

        ArrayList<Records.StructureConnection> out = new ArrayList<>();

        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                BlockPos a = new BlockPos(gx * gridBlocks, 0, gz * gridBlocks);

                // 只连接“右”和“下”，避免重复边；mergeConnections 会做全局去重。
                if (gx + 1 <= gx1) {
                    BlockPos b = new BlockPos((gx + 1) * gridBlocks, 0, gz * gridBlocks);
                    out.add(new Records.StructureConnection(a, b, Records.ConnectionStatus.PLANNED));
                }
                if (gz + 1 <= gz1) {
                    BlockPos c = new BlockPos(gx * gridBlocks, 0, (gz + 1) * gridBlocks);
                    out.add(new Records.StructureConnection(a, c, Records.ConnectionStatus.PLANNED));
                }
            }
        }

        return out;
    }

    private static List<Records.StructureConnection> mergeConnections(List<Records.StructureConnection> existing,
                                                                      List<Records.StructureConnection> incoming) {
        HashSet<Long> seen = new HashSet<>();
        ArrayList<Records.StructureConnection> out = new ArrayList<>();

        if (existing != null) {
            for (Records.StructureConnection c : existing) {
                long k = PlanningUtils.edgeKey(c.from(), c.to());
                if (seen.add(k)) out.add(c);
            }
        }

        for (Records.StructureConnection c : incoming) {
            long k = PlanningUtils.edgeKey(c.from(), c.to());
            if (seen.add(k)) {
                out.add(new Records.StructureConnection(c.from(), c.to(), Records.ConnectionStatus.PLANNED));
            }
        }

        return out;
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }
}
