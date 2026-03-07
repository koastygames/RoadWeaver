package net.shiroha233.roadweaver.features.highway.planning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.HighwayCellPathPlanningService;
import net.shiroha233.roadweaver.planning.PlanningUtils;
import net.shiroha233.roadweaver.runtime.ThreadPoolManager;
import net.shiroha233.roadweaver.util.ComputeService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highway 规划服务
 */
public final class HighwayPlanningService {
    private HighwayPlanningService() {}

    private static final class WindowCenter {
        private int gx;
        private int gz;
        private boolean dynamicActivated;

        private WindowCenter(int gx, int gz, boolean dynamicActivated) {
            this.gx = gx;
            this.gz = gz;
            this.dynamicActivated = dynamicActivated;
        }
    }

    private static final ConcurrentHashMap<Level, WindowCenter> WINDOW_CENTERS = new ConcurrentHashMap<>();

    public static void resetAll() {
        WINDOW_CENTERS.clear();
    }

    public static void initialPlan(ServerLevel level) {
        if (level == null)
            return;
        ModConfig cfg = ConfigService.get();
        String dimId = level.dimension().location().toString();
        if (!cfg.highwayEnabledForDimension(dimId) || !cfg.highwayAutoPlanEnabled())
            return;

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
        WINDOW_CENTERS.put(level, new WindowCenter(cellGx, cellGz, false));
        refreshSingleCell(level, cfg, cellGx, cellGz);
    }

    public static CompletableFuture<Void> initialPlanAsync(ServerLevel level) {
        if (level == null)
            return CompletableFuture.completedFuture(null);
        ModConfig cfg = ConfigService.get();
        String dimId = level.dimension().location().toString();
        if (!cfg.highwayEnabledForDimension(dimId) || !cfg.highwayAutoPlanEnabled())
            return CompletableFuture.completedFuture(null);

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
        WINDOW_CENTERS.put(level, new WindowCenter(cellGx, cellGz, false));
        return refreshSingleCellAsync(level, cfg, cellGx, cellGz);
    }

    private static void refreshSingleCell(ServerLevel level, ModConfig cfg, int cellGx, int cellGz) {
        if (level == null || cfg == null)
            return;
        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());

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

    private static CompletableFuture<Void> refreshSingleCellAsync(ServerLevel level, ModConfig cfg, int cellGx,
            int cellGz) {
        if (level == null || cfg == null)
            return CompletableFuture.completedFuture(null);
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
            if (server == null)
                return;
            server.execute(() -> HighwayCellPathPlanningService.planCompletedCellsInRect(level, cellMinX, cellMinZ,
                    cellMaxX, cellMaxZ));
        });
    }

    public static void planAroundPlayer(ServerPlayer player) {
        if (player == null)
            return;
        ServerLevel level = player.serverLevel();

        ModConfig cfg = ConfigService.get();
        String dimId = level.dimension().location().toString();
        if (!cfg.highwayEnabledForDimension(dimId) || !cfg.highwayAutoPlanEnabled())
            return;

        WindowCenter center = WINDOW_CENTERS.get(level);
        if (center == null) {
            int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
            int playerCellGx = floorDiv(player.getBlockX(), gridBlocks);
            int playerCellGz = floorDiv(player.getBlockZ(), gridBlocks);
            center = new WindowCenter(playerCellGx, playerCellGz, cfg.highwayDynamicPlanEnabled());
            WINDOW_CENTERS.put(level, center);
            if (cfg.highwayDynamicPlanEnabled()) {
                refreshWindowAsync(level, cfg, playerCellGx, playerCellGz);
            } else {
                refreshSingleCellAsync(level, cfg, playerCellGx, playerCellGz);
            }
            return;
        }

        if (!cfg.highwayDynamicPlanEnabled()) {
            center.dynamicActivated = false;
        }

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

        int gridBlocks = Math.max(1, cfg.highwayGridBlocks());
        int playerCellGx = floorDiv(player.getBlockX(), gridBlocks);
        int playerCellGz = floorDiv(player.getBlockZ(), gridBlocks);

        int dx = playerCellGx - center.gx;
        int dz = playerCellGz - center.gz;
        if (dx == 0 && dz == 0)
            return;

        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) {
            center.gx = playerCellGx;
            center.gz = playerCellGz;
            refreshWindowAsync(level, cfg, center.gx, center.gz);
            return;
        }

        if (Math.abs(dx) == 1 || Math.abs(dz) == 1) {
            center.gx += dx;
            center.gz += dz;
            refreshWindowAsync(level, cfg, center.gx, center.gz);
        }
    }

    private static CompletableFuture<Void> refreshWindowAsync(ServerLevel level, ModConfig cfg, int centerCellGx,
            int centerCellGz) {
        if (level == null || cfg == null)
            return CompletableFuture.completedFuture(null);

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

        return planRectAsync(level, minX, minZ, maxX, maxZ).thenRun(() -> {
            var server = level.getServer();
            if (server == null)
                return;
            server.execute(() -> HighwayCellPathPlanningService.planCompletedCellsInRect(level, cellMinX, cellMinZ,
                    cellMaxX, cellMaxZ));
        });
    }

    private static void planRect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        int gridBlocks = Math.max(1, ConfigService.get().highwayGridBlocks());
        List<StructureConnection> planned = buildGridConnections(gridBlocks, minBlockX, minBlockZ, maxBlockX,
                maxBlockZ);
        if (planned.isEmpty())
            return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> existing = provider.getHighwayConnections(level);
        List<StructureConnection> merged = mergeConnections(existing, planned);
        if (merged.size() != (existing == null ? 0 : existing.size())) {
            provider.setHighwayConnections(level, merged);
        }
    }

    public static CompletableFuture<Void> planRectAsync(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX,
            int maxBlockZ) {
        final long epoch = ThreadPoolManager.currentEpoch();
        final ModConfig cfgSnap = ConfigService.get();
        final int gridBlocks = Math.max(1, cfgSnap.highwayGridBlocks());

        return ComputeService.supplyAsync(() -> {
            if (Thread.currentThread().isInterrupted())
                return new ArrayList<StructureConnection>();
            if (!ThreadPoolManager.isEpoch(epoch))
                return new ArrayList<StructureConnection>();

            if (cfgSnap == null || !cfgSnap.highwayEnabled() || !cfgSnap.highwayAutoPlanEnabled()) {
                return new ArrayList<StructureConnection>();
            }

            return new ArrayList<>(buildGridConnections(gridBlocks, minBlockX, minBlockZ, maxBlockX, maxBlockZ));
        }).thenAccept(incoming -> {
            if (incoming == null || incoming.isEmpty())
                return;
            if (!ThreadPoolManager.isEpoch(epoch))
                return;
            var server = level.getServer();
            if (server == null)
                return;

            server.execute(() -> {
                if (!ThreadPoolManager.isEpoch(epoch))
                    return;
                WorldDataProvider provider = WorldDataProvider.getInstance();
                List<StructureConnection> existing = provider.getHighwayConnections(level);
                List<StructureConnection> merged = mergeConnections(existing, incoming);
                if (merged.size() != (existing == null ? 0 : existing.size())) {
                    provider.setHighwayConnections(level, merged);
                }
            });
        });
    }

    private static List<StructureConnection> buildGridConnections(int gridBlocks,
            int minBlockX,
            int minBlockZ,
            int maxBlockX,
            int maxBlockZ) {
        int gx0 = floorDiv(minBlockX, gridBlocks);
        int gz0 = floorDiv(minBlockZ, gridBlocks);
        int gx1 = floorDiv(maxBlockX, gridBlocks);
        int gz1 = floorDiv(maxBlockZ, gridBlocks);

        ArrayList<StructureConnection> out = new ArrayList<>();

        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                BlockPos a = new BlockPos(gx * gridBlocks, 0, gz * gridBlocks);

                if (gx + 1 <= gx1) {
                    BlockPos b = new BlockPos((gx + 1) * gridBlocks, 0, gz * gridBlocks);
                    out.add(new StructureConnection(a, b, ConnectionStatus.PLANNED));
                }
                if (gz + 1 <= gz1) {
                    BlockPos c = new BlockPos(gx * gridBlocks, 0, (gz + 1) * gridBlocks);
                    out.add(new StructureConnection(a, c, ConnectionStatus.PLANNED));
                }
            }
        }

        return out;
    }

    private static List<StructureConnection> mergeConnections(List<StructureConnection> existing,
            List<StructureConnection> incoming) {
        HashSet<Long> seen = new HashSet<>();
        ArrayList<StructureConnection> out = new ArrayList<>();

        if (existing != null) {
            for (StructureConnection c : existing) {
                long k = PlanningUtils.edgeKey(c.from(), c.to());
                if (seen.add(k))
                    out.add(c);
            }
        }

        for (StructureConnection c : incoming) {
            long k = PlanningUtils.edgeKey(c.from(), c.to());
            if (seen.add(k)) {
                out.add(new StructureConnection(c.from(), c.to(), ConnectionStatus.PLANNED));
            }
        }

        return out;
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a))
            r--;
        return r;
    }
}
