package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;
import net.shiroha233.roadweaver.planning.PlanningUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 连接线快照收集。
 */
public final class MapConnectionCollector {
    private MapConnectionCollector() {}

    public static List<StructureConnection> collect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        List<StructureConnection> connections = provider.getStructureConnections(level);
        List<StructureConnection> highwayConnections = provider.getHighwayConnections(level);

        ArrayList<StructureConnection> out = new ArrayList<>();
        if (connections != null) {
            for (StructureConnection c : connections) {
                BlockPos a = c.from();
                BlockPos b = c.to();
                if (intersectsRect(a, b, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) out.add(c);
            }
        }

        if (highwayConnections != null && !highwayConnections.isEmpty()) {
            HashSet<Long> seenEdges = new HashSet<>();
            for (StructureConnection c : out) seenEdges.add(PlanningUtils.edgeKey(c.from(), c.to()));
            for (StructureConnection hc : highwayConnections) {
                BlockPos a = hc.from();
                BlockPos b = hc.to();
                if (!intersectsRect(a, b, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) continue;
                long key = PlanningUtils.edgeKey(a, b);
                if (seenEdges.add(key)) out.add(hc);
            }
        }

        return out;
    }

    private static boolean intersectsRect(BlockPos a, BlockPos b, int minX, int minZ, int maxX, int maxZ) {
        int segMinX = Math.min(a.getX(), b.getX());
        int segMaxX = Math.max(a.getX(), b.getX());
        int segMinZ = Math.min(a.getZ(), b.getZ());
        int segMaxZ = Math.max(a.getZ(), b.getZ());
        if (segMaxX < minX || segMinX > maxX) return false;
        if (segMaxZ < minZ || segMinZ > maxZ) return false;
        return true;
    }
}