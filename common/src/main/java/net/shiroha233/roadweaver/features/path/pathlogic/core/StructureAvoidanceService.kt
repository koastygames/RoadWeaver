package net.shiroha233.roadweaver.features.path.pathlogic.core

import it.unimi.dsi.fastutil.longs.LongSet
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructurePiece
import net.minecraft.world.level.levelgen.structure.StructureStart
import net.shiroha233.roadweaver.config.ConfigService

object StructureAvoidanceService {
    private const val SEA_LEVEL = 63

    @JvmStatic
    fun shouldAvoid(world: WorldGenLevel, pos: BlockPos): Boolean {
        if (!ConfigService.get().structureAvoidanceEnabled()) {
            return false
        }

        val level: ServerLevel = world.level ?: return false
        val sm: StructureManager = level.structureManager()

        if (!sm.hasAnyStructureAt(pos)) {
            return false
        }

        val allStructures: Map<Structure, LongSet> = sm.getAllStructuresAt(pos)
        if (allStructures.isEmpty()) {
            return false
        }

        for ((structure, chunkRefs) in allStructures.entries) {
            for (chunkLong in chunkRefs) {
                val chunkPos = ChunkPos(chunkLong)
                val sectionPos = SectionPos.of(chunkPos, level.minSection)

                val start: StructureStart? = sm.getStartForStructure(
                    sectionPos,
                    structure,
                    level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS)
                )

                if (start == null || !start.isValid) continue

                for (piece: StructurePiece in start.pieces) {
                    val bb = piece.boundingBox

                    if (bb.minY() < SEA_LEVEL) {
                        continue
                    }

                    if (pos.x >= bb.minX() && pos.x <= bb.maxX() && pos.z >= bb.minZ() && pos.z <= bb.maxZ()) {
                        return true
                    }
                }
            }
        }

        return false
    }

    @JvmStatic
    fun shouldAvoidAny(world: WorldGenLevel, positions: Iterable<BlockPos>): Boolean {
        for (pos in positions) {
            if (shouldAvoid(world, pos)) {
                return true
            }
        }
        return false
    }
}
