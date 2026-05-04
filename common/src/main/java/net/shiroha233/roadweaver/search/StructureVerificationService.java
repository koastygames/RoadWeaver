package net.shiroha233.roadweaver.search;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.shiroha233.roadweaver.core.model.StructureInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责校验预测结构点是否真的可能生成结构。
 */
public final class StructureVerificationService {
    private StructureVerificationService() {
    }

    /**
     * 对一批预测结构点进行验证，返回通过验证的子集。
     */
    public static List<StructureInfo> verifyPredictedStructures(ServerLevel level, List<StructureInfo> predicted) {
        if (predicted == null || predicted.isEmpty()) {
            return List.of();
        }

        var source = level.getChunkSource();
        if (!(source instanceof ServerChunkCache chunkCache)) {
            return new ArrayList<>(predicted);
        }

        var server = level.getServer();
        if (server == null) {
            return new ArrayList<>(predicted);
        }

        ChunkScanAccess scanAccess;
        try {
            scanAccess = chunkCache.chunkScanner();
        } catch (Throwable ignored) {
            return new ArrayList<>(predicted);
        }

        var registryAccess = level.registryAccess();
        ChunkGenerator generator = chunkCache.getGenerator();
        ChunkGeneratorStructureState generatorState = chunkCache.getGeneratorState();
        RandomState randomState = chunkCache.randomState();
        BiomeSource biomeSource = generator.getBiomeSource();

        StructureCheck checker = new StructureCheck(
                scanAccess,
                registryAccess,
                server.getStructureManager(),
                level.dimension(),
                generator,
                randomState,
                level,
                biomeSource,
                level.getSeed(),
                server.getFixerUpper());

        Registry<Structure> structureRegistry = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        List<StructureInfo> result = new ArrayList<>();

        for (StructureInfo info : predicted) {
            StructureMatch structureMatch = resolveStructure(structureRegistry, info.structureId());
            if (structureMatch == null) {
                result.add(info);
                continue;
            }

            List<StructurePlacement> placements = generatorState.getPlacementsForStructure(structureMatch.holder());
            if (placements.isEmpty()) {
                result.add(info);
                continue;
            }

            ChunkPos chunkPos = new ChunkPos(info.pos().getX() >> 4, info.pos().getZ() >> 4);
            if (isAnyPlacementVerified(checker, chunkPos, structureMatch.structure(), placements)) {
                result.add(info);
            }
        }

        return result;
    }

    private static StructureMatch resolveStructure(Registry<Structure> structureRegistry, String structureId) {
        if (structureId == null || structureId.isEmpty()) {
            return null;
        }

        Identifier identifier = Identifier.tryParse(structureId);
        if (identifier == null) {
            return null;
        }

        Structure structure = structureRegistry.getValue(identifier);
        if (structure == null) {
            return null;
        }

        var holder = structureRegistry.get(ResourceKey.create(Registries.STRUCTURE, identifier));
        if (holder.isEmpty()) {
            return null;
        }

        return new StructureMatch(structure, holder.get());
    }

    private static boolean isAnyPlacementVerified(StructureCheck checker,
            ChunkPos chunkPos,
            Structure structure,
            List<StructurePlacement> placements) {
        for (StructurePlacement placement : placements) {
            try {
                StructureCheckResult checkResult = checker.checkStart(chunkPos, structure, placement, false);
                if (checkResult == StructureCheckResult.START_PRESENT
                        || checkResult == StructureCheckResult.CHUNK_LOAD_NEEDED) {
                    return true;
                }
            } catch (Throwable ignored) {
                // 某些结构检查在不同平台实现下可能抛出异常，单条失败不应中断整批验证。
            }
        }
        return false;
    }

    private record StructureMatch(Structure structure, net.minecraft.core.Holder.Reference<Structure> holder) {
    }
}
