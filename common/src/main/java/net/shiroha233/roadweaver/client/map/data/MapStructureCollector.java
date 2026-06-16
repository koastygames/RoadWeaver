package net.shiroha233.roadweaver.client.map.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.persistence.sqlite.StructureSqliteStorage;

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
                ? new int[]{StructureSqliteStorage.SOURCE_MANUAL, StructureSqliteStorage.SOURCE_PREDICTED}
                : new int[]{StructureSqliteStorage.SOURCE_MANUAL};

        List<StructureInfo> cached = StructureSqliteStorage.queryRect(level, minBlockX, minBlockZ, maxBlockX, maxBlockZ, sources);
        HashMap<Long, StructureInfo> bestInfoByPos = new HashMap<>();
        HashSet<BlockPos> structuresSet = new HashSet<>();
        for (StructureInfo info : cached) {
            if (info == null || info.pos() == null) continue;
            mergeBestStructureInfo(bestInfoByPos, info);
            BlockPos p = info.pos();
            structuresSet.add(new BlockPos(p.getX(), 0, p.getZ()));
        }
        return new Result(new ArrayList<>(structuresSet), new ArrayList<>(bestInfoByPos.values()));
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