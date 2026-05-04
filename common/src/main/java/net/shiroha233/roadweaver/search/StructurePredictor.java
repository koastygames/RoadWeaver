package net.shiroha233.roadweaver.search;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.shiroha233.roadweaver.core.model.StructureInfo;
import net.shiroha233.roadweaver.helpers.LevelCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 负责按结构集规则预测候选结构位置。
 */
public final class StructurePredictor {
    private StructurePredictor() {
    }

    public static List<StructureInfo> predictStructuresInRect(ServerLevel level,
            int minChunkX,
            int minChunkZ,
            int maxChunkX,
            int maxChunkZ,
            boolean biomePrefilter,
            List<String> whitelist,
            List<String> blacklist) {
        return predictOverworldStructuresInRect(
                level,
                minChunkX,
                minChunkZ,
                maxChunkX,
                maxChunkZ,
                biomePrefilter,
                whitelist,
                blacklist);
    }

    public static List<StructureInfo> predictOverworldVillagesAroundSpawn(ServerLevel level,
            int radiusChunks,
            boolean biomePrefilter) {
        RegistryAccess registryAccess = level.registryAccess();
        Registry<StructureSet> setRegistry = registryAccess.lookupOrThrow(Registries.STRUCTURE_SET);
        Optional<Holder.Reference<StructureSet>> optVillages = setRegistry.get(BuiltinStructureSets.VILLAGES);
        if (optVillages.isEmpty()) {
            return List.of();
        }

        StructureSet set = optVillages.get().value();
        StructurePlacement placement = set.placement();
        if (!(placement instanceof RandomSpreadStructurePlacement randomPlacement)) {
            return List.of();
        }

        BlockPos spawn = LevelCompat.getWorldSpawnPos(level);
        int centerChunkX = spawn.getX() >> 4;
        int centerChunkZ = spawn.getZ() >> 4;
        int minChunkX = centerChunkX - radiusChunks;
        int maxChunkX = centerChunkX + radiusChunks;
        int minChunkZ = centerChunkZ - radiusChunks;
        int maxChunkZ = centerChunkZ + radiusChunks;

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        RandomState randomState = state.randomState();
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();

        Set<Holder<Biome>> allowedBiomes = biomePrefilter ? collectAllowedBiomesFromSets(List.of(set)) : null;
        int spacing = randomPlacement.spacing();
        int startRegionX = Math.floorDiv(minChunkX, spacing);
        int endRegionX = Math.floorDiv(maxChunkX, spacing);
        int startRegionZ = Math.floorDiv(minChunkZ, spacing);
        int endRegionZ = Math.floorDiv(maxChunkZ, spacing);

        List<StructureInfo> result = new ArrayList<>();
        long seed = level.getSeed();

        for (int regionX = startRegionX; regionX <= endRegionX; regionX++) {
            for (int regionZ = startRegionZ; regionZ <= endRegionZ; regionZ++) {
                ChunkPos candidate = randomPlacement.getPotentialStructureChunk(
                        seed,
                        regionX * spacing,
                        regionZ * spacing);
                if (!isChunkInRange(candidate, minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
                    continue;
                }
                if (!placement.isStructureChunk(state, candidate.x, candidate.z)) {
                    continue;
                }

                BlockPos locatePos = placement.getLocatePos(candidate);
                if (allowedBiomes != null && !matchesBiome(biomeSource, randomState, locatePos, allowedBiomes)) {
                    continue;
                }

                result.add(new StructureInfo(locatePos, "village"));
            }
        }

        return result;
    }

    public static List<StructureInfo> predictOverworldStructuresInRect(ServerLevel level,
            int minChunkX,
            int minChunkZ,
            int maxChunkX,
            int maxChunkZ,
            boolean biomePrefilter,
            List<String> whitelist,
            List<String> blacklist) {
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        RandomState randomState = state.randomState();
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        Filters filters = Filters.of(whitelist, blacklist);

        List<StructureInfo> result = new ArrayList<>();
        for (Holder<StructureSet> structureSetHolder : state.possibleStructureSets()) {
            StructureSet set = structureSetHolder.value();
            List<Holder<Structure>> matchedStructures = selectMatchedStructures(set, filters);
            if (matchedStructures.isEmpty()) {
                continue;
            }

            StructurePlacement placement = set.placement();
            String labelId = firstStructureId(matchedStructures, "structure");

            if (placement instanceof ConcentricRingsStructurePlacement concentricPlacement) {
                List<ChunkPos> ringPositions = state.getRingPositionsFor(concentricPlacement);
                if (ringPositions == null || ringPositions.isEmpty()) {
                    continue;
                }
                for (ChunkPos candidate : ringPositions) {
                    if (!isChunkInRange(candidate, minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
                        continue;
                    }
                    if (!placement.isStructureChunk(state, candidate.x, candidate.z)) {
                        continue;
                    }
                    result.add(new StructureInfo(placement.getLocatePos(candidate), labelId));
                }
                continue;
            }

            if (!(placement instanceof RandomSpreadStructurePlacement randomPlacement)) {
                continue;
            }

            Set<Holder<Biome>> allowedBiomes = biomePrefilter ? collectAllowedBiomes(matchedStructures) : null;
            int spacing = randomPlacement.spacing();
            int startRegionX = Math.floorDiv(minChunkX, spacing);
            int endRegionX = Math.floorDiv(maxChunkX, spacing);
            int startRegionZ = Math.floorDiv(minChunkZ, spacing);
            int endRegionZ = Math.floorDiv(maxChunkZ, spacing);

            for (int regionX = startRegionX; regionX <= endRegionX; regionX++) {
                for (int regionZ = startRegionZ; regionZ <= endRegionZ; regionZ++) {
                    ChunkPos candidate = randomPlacement.getPotentialStructureChunk(
                            level.getSeed(),
                            regionX * spacing,
                            regionZ * spacing);
                    if (!isChunkInRange(candidate, minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
                        continue;
                    }
                    if (!placement.isStructureChunk(state, candidate.x, candidate.z)) {
                        continue;
                    }

                    BlockPos locatePos = placement.getLocatePos(candidate);
                    Holder<Biome> sampledBiome = sampleBiome(biomeSource, randomState, locatePos);
                    if (allowedBiomes != null && !allowedBiomes.contains(sampledBiome)) {
                        continue;
                    }

                    result.add(new StructureInfo(
                            locatePos,
                            resolveMatchingStructureId(matchedStructures, sampledBiome, labelId)));
                }
            }
        }

        return result;
    }

    public static List<StructureInfo> predictOverworldStructuresAroundSpawn(ServerLevel level,
            int radiusChunks,
            boolean biomePrefilter,
            List<String> whitelist,
            List<String> blacklist) {
        RegistryAccess registryAccess = level.registryAccess();
        Registry<StructureSet> setRegistry = registryAccess.lookupOrThrow(Registries.STRUCTURE_SET);
        BlockPos spawn = LevelCompat.getWorldSpawnPos(level);

        int centerChunkX = spawn.getX() >> 4;
        int centerChunkZ = spawn.getZ() >> 4;
        int minChunkX = centerChunkX - radiusChunks;
        int maxChunkX = centerChunkX + radiusChunks;
        int minChunkZ = centerChunkZ - radiusChunks;
        int maxChunkZ = centerChunkZ + radiusChunks;

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        RandomState randomState = state.randomState();
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        Filters filters = Filters.of(whitelist, blacklist);

        List<StructureInfo> result = new ArrayList<>();
        for (Holder.Reference<StructureSet> structureSetHolder : setRegistry.listElements().toList()) {
            StructureSet set = structureSetHolder.value();
            StructurePlacement placement = set.placement();
            if (!(placement instanceof RandomSpreadStructurePlacement randomPlacement)) {
                continue;
            }

            List<Holder<Structure>> matchedStructures = selectMatchedStructures(set, filters);
            if (matchedStructures.isEmpty()) {
                continue;
            }

            Set<Holder<Biome>> allowedBiomes = biomePrefilter ? collectAllowedBiomes(matchedStructures) : null;
            String labelId = firstStructureId(matchedStructures, "structure");
            int spacing = randomPlacement.spacing();
            int startRegionX = Math.floorDiv(minChunkX, spacing);
            int endRegionX = Math.floorDiv(maxChunkX, spacing);
            int startRegionZ = Math.floorDiv(minChunkZ, spacing);
            int endRegionZ = Math.floorDiv(maxChunkZ, spacing);

            for (int regionX = startRegionX; regionX <= endRegionX; regionX++) {
                for (int regionZ = startRegionZ; regionZ <= endRegionZ; regionZ++) {
                    ChunkPos candidate = randomPlacement.getPotentialStructureChunk(
                            level.getSeed(),
                            regionX * spacing,
                            regionZ * spacing);
                    if (!isChunkInRange(candidate, minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
                        continue;
                    }
                    if (!placement.isStructureChunk(state, candidate.x, candidate.z)) {
                        continue;
                    }

                    BlockPos locatePos = placement.getLocatePos(candidate);
                    Holder<Biome> sampledBiome = sampleBiome(biomeSource, randomState, locatePos);
                    if (allowedBiomes != null && !allowedBiomes.contains(sampledBiome)) {
                        continue;
                    }

                    result.add(new StructureInfo(
                            locatePos,
                            resolveMatchingStructureId(matchedStructures, sampledBiome, labelId)));
                }
            }
        }

        return result;
    }

    private static List<Holder<Structure>> selectMatchedStructures(StructureSet set, Filters filters) {
        List<Holder<Structure>> matchedStructures = new ArrayList<>();
        for (StructureSet.StructureSelectionEntry entry : set.structures()) {
            Holder<Structure> structureHolder = entry.structure();
            Optional<ResourceKey<Structure>> key = structureHolder.unwrapKey();
            if (key.isEmpty()) {
                continue;
            }
            Identifier structureId = key.get().identifier();
            if (filters.matches(structureHolder, structureId)) {
                matchedStructures.add(structureHolder);
            }
        }

        if (!matchedStructures.isEmpty() || filters.hasWhitelist()) {
            return matchedStructures;
        }

        for (StructureSet.StructureSelectionEntry entry : set.structures()) {
            Holder<Structure> structureHolder = entry.structure();
            Optional<ResourceKey<Structure>> key = structureHolder.unwrapKey();
            if (key.isEmpty()) {
                continue;
            }
            Identifier structureId = key.get().identifier();
            if (!filters.isBlacklisted(structureHolder, structureId)) {
                matchedStructures.add(structureHolder);
            }
        }
        return matchedStructures;
    }

    private static Set<Holder<Biome>> collectAllowedBiomes(List<Holder<Structure>> structures) {
        Set<Holder<Biome>> allowedBiomes = new HashSet<>();
        for (Holder<Structure> structureHolder : structures) {
            for (Holder<Biome> biomeHolder : structureHolder.value().biomes()) {
                allowedBiomes.add(biomeHolder);
            }
        }
        return allowedBiomes;
    }

    private static Set<Holder<Biome>> collectAllowedBiomesFromSets(List<StructureSet> structureSets) {
        Set<Holder<Biome>> allowedBiomes = new HashSet<>();
        for (StructureSet set : structureSets) {
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                for (Holder<Biome> biomeHolder : entry.structure().value().biomes()) {
                    allowedBiomes.add(biomeHolder);
                }
            }
        }
        return allowedBiomes;
    }

    private static Holder<Biome> sampleBiome(BiomeSource biomeSource, RandomState randomState, BlockPos pos) {
        return biomeSource.getNoiseBiome(
                QuartPos.fromBlock(pos.getX()),
                QuartPos.fromBlock(64),
                QuartPos.fromBlock(pos.getZ()),
                randomState.sampler());
    }

    private static boolean matchesBiome(BiomeSource biomeSource,
            RandomState randomState,
            BlockPos pos,
            Set<Holder<Biome>> allowedBiomes) {
        return allowedBiomes.contains(sampleBiome(biomeSource, randomState, pos));
    }

    private static boolean isChunkInRange(ChunkPos candidate,
            int minChunkX,
            int minChunkZ,
            int maxChunkX,
            int maxChunkZ) {
        return candidate.x >= minChunkX
                && candidate.x <= maxChunkX
                && candidate.z >= minChunkZ
                && candidate.z <= maxChunkZ;
    }

    private static String resolveMatchingStructureId(List<Holder<Structure>> matchedStructures,
            Holder<Biome> sampledBiome,
            String fallbackId) {
        for (Holder<Structure> structureHolder : matchedStructures) {
            if (structureHolder.value().biomes().contains(sampledBiome)) {
                return structureHolder.unwrapKey()
                        .map(ResourceKey::identifier)
                        .map(Identifier::toString)
                        .orElse(fallbackId);
            }
        }
        return fallbackId;
    }

    private static String firstStructureId(List<Holder<Structure>> matchedStructures, String fallbackId) {
        return matchedStructures.stream()
                .map(structureHolder -> structureHolder.unwrapKey()
                        .map(ResourceKey::identifier)
                        .map(Identifier::toString)
                        .orElse(fallbackId))
                .findFirst()
                .orElse(fallbackId);
    }

    private static final class Filters {
        private final List<String> whitelist;
        private final List<String> blacklist;

        private Filters(List<String> whitelist, List<String> blacklist) {
            this.whitelist = normalize(whitelist);
            this.blacklist = normalize(blacklist);
        }

        static Filters of(List<String> whitelist, List<String> blacklist) {
            return new Filters(whitelist, blacklist);
        }

        boolean hasWhitelist() {
            return !whitelist.isEmpty();
        }

        boolean matches(Holder<Structure> holder, Identifier id) {
            boolean whiteOk = whitelist.isEmpty() || whitelist.stream().anyMatch(pattern -> matchesPattern(holder, id, pattern));
            boolean blackHit = blacklist.stream().anyMatch(pattern -> matchesPattern(holder, id, pattern));
            return whiteOk && !blackHit;
        }

        boolean isBlacklisted(Holder<Structure> holder, Identifier id) {
            return blacklist.stream().anyMatch(pattern -> matchesPattern(holder, id, pattern));
        }

        private boolean matchesPattern(Holder<Structure> holder, Identifier id, String pattern) {
            if (pattern == null || pattern.isEmpty()) {
                return false;
            }

            String normalizedPattern = pattern.trim().toLowerCase(Locale.ROOT);
            String structureId = id.toString().toLowerCase(Locale.ROOT);
            if (normalizedPattern.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(normalizedPattern.substring(1));
                if (tagId == null) {
                    return false;
                }
                return holder.is(TagKey.create(Registries.STRUCTURE, tagId));
            }

            if (normalizedPattern.endsWith("/*")) {
                String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 2);
                return structureId.startsWith(prefix + "/")
                        || structureId.startsWith(prefix + "_")
                        || structureId.startsWith(prefix + "-")
                        || structureId.startsWith(prefix + ".");
            }

            if (normalizedPattern.endsWith(":*")) {
                String namespace = normalizedPattern.substring(0, normalizedPattern.length() - 2);
                int colonIndex = namespace.indexOf(':');
                if (colonIndex > 0) {
                    namespace = namespace.substring(0, colonIndex);
                }
                return id.getNamespace().equalsIgnoreCase(namespace);
            }

            return structureId.equals(normalizedPattern);
        }

        private static List<String> normalize(List<String> source) {
            List<String> normalized = new ArrayList<>();
            if (source == null) {
                return normalized;
            }
            for (String value : source) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
            return normalized;
        }
    }
}
