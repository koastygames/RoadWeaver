package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.persistence.files.StructureFileStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * 结构点快照收集。
 */
public final class MapStructureCollector {
    private MapStructureCollector() {}

    public record Result(List<BlockPos> structures, List<StructureInfo> infos) {}

    public static Result collect(ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
        ModConfig cfg = ConfigService.get();
        boolean allowPredicted = cfg != null
                && cfg.structurePrediction().enabled()
                && cfg.structurePrediction().isEnabledForDimension(level.dimension().location().toString());
        int[] sources = allowPredicted
                ? new int[]{StructureFileStorage.SOURCE_MANUAL, StructureFileStorage.SOURCE_PREDICTED}
                : new int[]{StructureFileStorage.SOURCE_MANUAL};

        List<StructureInfo> cached = StructureFileStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, sources);
        HashMap<Long, StructureInfo> bestInfoByPos = new HashMap<>();
        HashSet<BlockPos> structuresSet = new HashSet<>();
        for (StructureInfo info : cached) {
            if (info == null || info.pos() == null) continue;
            mergeBestStructureInfo(bestInfoByPos, info);
            BlockPos p = info.pos();
            structuresSet.add(new BlockPos(p.getX(), 0, p.getZ()));
        }
        for (StructureConnection connection : MapConnectionCollector.collect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ)) {
            addConnectionEndpoint(bestInfoByPos, structuresSet, connection.from(), minBlockX, minBlockZ, maxBlockX, maxBlockZ);
            addConnectionEndpoint(bestInfoByPos, structuresSet, connection.to(), minBlockX, minBlockZ, maxBlockX, maxBlockZ);
        }
        return new Result(new ArrayList<>(structuresSet), new ArrayList<>(bestInfoByPos.values()));
    }

    private static void addConnectionEndpoint(java.util.Map<Long, StructureInfo> infos,
                                              HashSet<BlockPos> structures,
                                              BlockPos pos,
                                              int minBlockX,
                                              int minBlockZ,
                                              int maxBlockX,
                                              int maxBlockZ) {
        if (pos == null) return;
        int x = pos.getX();
        int z = pos.getZ();
        if (x < minBlockX || x > maxBlockX || z < minBlockZ || z > maxBlockZ) return;
        BlockPos normalized = new BlockPos(x, 0, z);
        structures.add(normalized);
        infos.putIfAbsent((((long) x) << 32) ^ (z & 0xffffffffL), new StructureInfo(normalized, "unknown"));
    }

    private static void mergeBestStructureInfo(java.util.Map<Long, StructureInfo> out, StructureInfo info) {
        if (out == null || info == null || info.pos() == null) return;
        BlockPos p = info.pos();
        int x = p.getX();
        int z = p.getZ();
        long key = (((long) x) << 32) ^ (z & 0xffffffffL);
        StructureInfo prev = out.get(key);
        if (prev == null) {
            out.put(key, new StructureInfo(new BlockPos(x, 0, z), info.structureId()));
            return;
        }
        String prevId = prev.structureId();
        String nextId = info.structureId();
        if (prevId == null) prevId = "unknown";
        if (nextId == null) nextId = "unknown";
        if ("unknown".equals(prevId) && !"unknown".equals(nextId)) {
            out.put(key, new StructureInfo(new BlockPos(x, 0, z), nextId));
        }
    }
}
