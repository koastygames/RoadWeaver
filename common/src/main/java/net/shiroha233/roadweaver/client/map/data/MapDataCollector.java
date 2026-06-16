package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图快照装配器。
 */
public final class MapDataCollector {
    private MapDataCollector() {}

    public static final int MAX_SNAPSHOT_STRUCTURES = 2048;
    public static final int MAX_SNAPSHOT_CONNECTIONS = 4096;
    public static final int MAX_SNAPSHOT_ROAD_POLYLINES = 512;
    public static final int MAX_SNAPSHOT_ROAD_POINTS = 32768;

    public static MapSnapshot build(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        MapStructureCollector.Result structureResult = MapStructureCollector.collect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        List<StructureConnection> connections = MapConnectionCollector.collect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        List<List<BlockPos>> roads = MapRoadCollector.collect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ);

        return new MapSnapshot(
                limitList(structureResult.structures(), MAX_SNAPSHOT_STRUCTURES),
                limitList(connections, MAX_SNAPSHOT_CONNECTIONS),
                structureResult.infos(),
                limitRoadPolylines(roads));
    }

    private static <T> List<T> limitList(List<T> input, int maxSize) {
        if (input == null || input.isEmpty()) return List.of();
        int limit = Math.max(0, maxSize);
        if (input.size() <= limit) return input;
        return new ArrayList<>(input.subList(0, limit));
    }

    private static List<List<BlockPos>> limitRoadPolylines(List<List<BlockPos>> roads) {
        if (roads == null || roads.isEmpty()) return List.of();
        ArrayList<List<BlockPos>> out = new ArrayList<>();
        int remainingPoints = MAX_SNAPSHOT_ROAD_POINTS;
        for (List<BlockPos> road : roads) {
            if (out.size() >= MAX_SNAPSHOT_ROAD_POLYLINES || remainingPoints <= 1) break;
            if (road == null || road.size() < 2) continue;
            int take = Math.min(road.size(), remainingPoints);
            if (take >= 2) {
                out.add(take == road.size() ? road : new ArrayList<>(road.subList(0, take)));
                remainingPoints -= take;
            }
        }
        return out;
    }
}
