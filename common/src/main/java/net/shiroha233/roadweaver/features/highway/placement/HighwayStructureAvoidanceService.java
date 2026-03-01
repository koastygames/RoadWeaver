package net.shiroha233.roadweaver.features.highway.placement;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.shiroha233.roadweaver.config.ConfigService;

import java.util.Map;

/**
 * Highway 结构避让服务
 */
public final class HighwayStructureAvoidanceService {
    private HighwayStructureAvoidanceService() {}

    private static final int SEA_LEVEL = 63;

    public static boolean shouldAvoid(WorldGenLevel world, BlockPos pos) {
        if (!ConfigService.get().structureAvoidanceEnabled()) {
            return false;
        }

        ServerLevel level = world.getLevel();
        if (level == null) return false;

        StructureManager sm = level.structureManager();
        if (!sm.hasAnyStructureAt(pos)) {
            return false;
        }

        Map<Structure, LongSet> allStructures = sm.getAllStructuresAt(pos);
        if (allStructures.isEmpty()) {
            return false;
        }

        for (Map.Entry<Structure, LongSet> entry : allStructures.entrySet()) {
            Structure structure = entry.getKey();
            LongSet chunkRefs = entry.getValue();

            for (long chunkLong : chunkRefs) {
                ChunkPos chunkPos = new ChunkPos(chunkLong);
                SectionPos sectionPos = SectionPos.of(chunkPos, level.getMinSection());

                StructureStart start = sm.getStartForStructure(
                        sectionPos,
                        structure,
                        level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS)
                );

                if (start == null || !start.isValid()) continue;

                for (StructurePiece piece : start.getPieces()) {
                    BoundingBox bb = piece.getBoundingBox();
                    if (bb.minY() < SEA_LEVEL) {
                        continue;
                    }
                    if (pos.getX() >= bb.minX() && pos.getX() <= bb.maxX()
                            && pos.getZ() >= bb.minZ() && pos.getZ() <= bb.maxZ()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
