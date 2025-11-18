package net.shiroha233.roadweaver.helpers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.persistence.WorldDataProvider;

import java.util.*;

public final class StructureConnector {
    private StructureConnector() {}

    private static final Queue<Records.StructureConnection> CACHED = new ArrayDeque<>();

    public static Queue<Records.StructureConnection> cachedStructureConnections() {
        return CACHED;
    }

    public static void cacheNewConnection(ServerLevel level, boolean locateAtPlayerIgnored) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        Records.StructureLocationData data = provider.getStructureLocations(level);
        List<BlockPos> list = data.structureLocations();
        if (list == null || list.size() < 2) return;
        createNewStructureConnection(level);
    }

    private static void createNewStructureConnection(ServerLevel level) {
        WorldDataProvider provider = WorldDataProvider.getInstance();
        Records.StructureLocationData data = provider.getStructureLocations(level);
        List<BlockPos> all = data.structureLocations();
        if (all == null || all.size() < 2) return;
        BlockPos latest = all.get(all.size() - 1);
        BlockPos closest = findClosest(latest, all);
        if (closest == null) return;
        List<Records.StructureConnection> connections = new ArrayList<>(provider.getStructureConnections(level));
        if (!exists(connections, latest, closest)) {
            Records.StructureConnection c = new Records.StructureConnection(latest, closest, Records.ConnectionStatus.PLANNED);
            connections.add(c);
            provider.setStructureConnections(level, connections);
            CACHED.add(c);
        }
    }

    private static boolean exists(List<Records.StructureConnection> existing, BlockPos a, BlockPos b) {
        for (Records.StructureConnection c : existing) {
            if ((c.from().equals(a) && c.to().equals(b)) || (c.from().equals(b) && c.to().equals(a))) return true;
        }
        return false;
    }

    private static BlockPos findClosest(BlockPos cur, List<BlockPos> all) {
        BlockPos best = null;
        double min = Double.MAX_VALUE;
        for (BlockPos p : all) {
            if (p.equals(cur)) continue;
            double d = cur.distSqr(p);
            if (d < min) { min = d; best = p; }
        }
        return best;
    }
}
