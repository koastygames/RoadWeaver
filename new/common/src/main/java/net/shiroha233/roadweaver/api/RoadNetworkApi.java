package net.shiroha233.roadweaver.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.core.model.StructureLocationData;
import net.shiroha233.roadweaver.generation.RoadGenerationService;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.persistence.sqlite.StructureSqliteStorage;
import net.shiroha233.roadweaver.planning.RoadPlanningService;

import java.util.ArrayList;
import java.util.List;

public final class RoadNetworkApi {
    private RoadNetworkApi() {
    }

    public static void registerStructureEndpoint(ServerLevel level, BlockPos pos) {
        registerStructureEndpoint(level, pos, null, false);
    }

    public static void registerStructureEndpoint(ServerLevel level, BlockPos pos, boolean autoConnect) {
        registerStructureEndpoint(level, pos, null, autoConnect);
    }

    public static void registerStructureEndpoint(ServerLevel level, BlockPos pos, String structureId,
            boolean autoConnect) {
        if (level == null || pos == null)
            return;
        WorldDataProvider provider = WorldDataProvider.getInstance();
        StructureLocationData existing = provider.getStructureLocations(level);
        List<BlockPos> locations = existing != null ? new ArrayList<>(existing.structureLocations())
                : new ArrayList<>();
        List<StructureInfo> infos = existing != null ? new ArrayList<>(existing.structureInfos())
                : new ArrayList<>();

        if (structureId != null && !structureId.isEmpty()) {
            StructureInfo info = new StructureInfo(pos, structureId);
            infos.add(info);
            if (!locations.contains(pos)) {
                locations.add(pos);
            }
        } else {
            if (!locations.contains(pos)) {
                locations.add(pos);
            }
        }

        StructureLocationData updated = new StructureLocationData(locations, infos);
        provider.setStructureLocations(level, updated);

        String id = (structureId != null && !structureId.isEmpty()) ? structureId : "unknown";
        StructureSqliteStorage.addStructures(
                level,
                java.util.List.of(new StructureInfo(new BlockPos(pos.getX(), 0, pos.getZ()), id)),
                StructureSqliteStorage.SOURCE_MANUAL
        );
    }

    public static void ensureConnection(ServerLevel level, BlockPos from, BlockPos to) {
        ensureConnection(level, from, to, false);
    }

    public static void ensureConnection(ServerLevel level, BlockPos from, BlockPos to, boolean generateImmediately) {
        if (level == null || from == null || to == null)
            return;
        if (from.equals(to))
            return;

        WorldDataProvider provider = WorldDataProvider.getInstance();
        StructureLocationData existing = provider.getStructureLocations(level);
        List<BlockPos> locations = existing != null ? new ArrayList<>(existing.structureLocations())
                : new ArrayList<>();
        List<StructureInfo> infos = existing != null ? new ArrayList<>(existing.structureInfos())
                : new ArrayList<>();

        boolean changed = false;
        if (!locations.contains(from)) {
            locations.add(from);
            changed = true;
        }
        if (!locations.contains(to)) {
            locations.add(to);
            changed = true;
        }
        if (changed) {
            StructureLocationData updated = new StructureLocationData(locations, infos);
            provider.setStructureLocations(level, updated);
        }

        StructureSqliteStorage.addStructures(
                level,
                java.util.List.of(
                        new StructureInfo(new BlockPos(from.getX(), 0, from.getZ()), "unknown"),
                        new StructureInfo(new BlockPos(to.getX(), 0, to.getZ()), "unknown")
                ),
                StructureSqliteStorage.SOURCE_MANUAL
        );

        List<StructureConnection> existingConns = provider.getStructureConnections(level);
        List<StructureConnection> list = existingConns != null ? new ArrayList<>(existingConns)
                : new ArrayList<>();
        boolean exists = false;
        for (StructureConnection c : list) {
            if (sameEdge(c, from, to)) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            list.add(new StructureConnection(from, to, ConnectionStatus.PLANNED));
            provider.setStructureConnections(level, list);
        }

        if (generateImmediately) {
            StructureConnection conn = new StructureConnection(from, to,
                    ConnectionStatus.GENERATING);
            List<StructureConnection> currentList = provider.getStructureConnections(level);
            List<StructureConnection> all = currentList != null ? new ArrayList<>(currentList)
                    : new ArrayList<>();
            boolean found = false;
            for (int i = 0; i < all.size(); i++) {
                if (sameEdge(all.get(i), from, to)) {
                    all.set(i, conn);
                    found = true;
                    break;
                }
            }
            if (!found)
                all.add(conn);
            provider.setStructureConnections(level, all);
            boolean success = RoadGenerationService.generateTask(level, conn);
            ConnectionStatus finalStatus = success ? ConnectionStatus.COMPLETED
                    : ConnectionStatus.FAILED;
            StructureConnection finalConn = new StructureConnection(from, to, finalStatus);

            currentList = provider.getStructureConnections(level);
            all = currentList != null ? new ArrayList<>(currentList) : new ArrayList<>();
            found = false;
            for (int i = 0; i < all.size(); i++) {
                if (sameEdge(all.get(i), from, to)) {
                    all.set(i, finalConn);
                    found = true;
                    break;
                }
            }
            if (!found)
                all.add(finalConn);
            provider.setStructureConnections(level, all);
        }
    }

    public static void planRegion(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        if (level == null)
            return;
        RoadPlanningService.planRectAsync(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
    }

    private static boolean sameEdge(StructureConnection c, BlockPos a, BlockPos b) {
        BlockPos af = c.from();
        BlockPos at = c.to();
        return (af.equals(a) && at.equals(b)) || (af.equals(b) && at.equals(a));
    }
}
