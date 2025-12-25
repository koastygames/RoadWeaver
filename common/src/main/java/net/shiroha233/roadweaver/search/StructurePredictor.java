package net.shiroha233.roadweaver.search;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.shiroha233.roadweaver.helpers.Records;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;

public final class StructurePredictor {
    private StructurePredictor() {}

    public static List<Records.StructureInfo> predictStructuresInRect(ServerLevel level,
                                                                      int minChunkX,
                                                                      int minChunkZ,
                                                                      int maxChunkX,
                                                                      int maxChunkZ,
                                                                      boolean biomePrefilter,
                                                                      List<String> whitelist,
                                                                      List<String> blacklist) {
        // 说明：旧实现命名为 Overworld，但其核心逻辑是读取当前维度的 StructureSet/placement 并做候选区块推导，
        // 因此对下界/末地同样适用（只要对应结构使用 RandomSpreadStructurePlacement）。
        return predictOverworldStructuresInRect(level, minChunkX, minChunkZ, maxChunkX, maxChunkZ, biomePrefilter, whitelist, blacklist);
    }

    public static List<Records.StructureInfo> predictOverworldVillagesAroundSpawn(ServerLevel level, int radiusChunks, boolean biomePrefilter) {
        RegistryAccess registryAccess = level.registryAccess();
        Registry<StructureSet> setRegistry = registryAccess.registryOrThrow(Registries.STRUCTURE_SET);
        Optional<Holder.Reference<StructureSet>> optVillages = setRegistry.getHolder(BuiltinStructureSets.VILLAGES);
        if (optVillages.isEmpty()) return List.of();
        StructureSet set = optVillages.get().value();
        StructurePlacement placement = set.placement();
        if (!(placement instanceof RandomSpreadStructurePlacement rssp)) return List.of();

        BlockPos spawn = level.getSharedSpawnPos();
        int cx = spawn.getX() >> 4;
        int cz = spawn.getZ() >> 4;
        int minX = cx - radiusChunks;
        int maxX = cx + radiusChunks;
        int minZ = cz - radiusChunks;
        int maxZ = cz + radiusChunks;

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        RandomState randomState = state.randomState();
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();

        Set<Holder<Biome>> allowedBiomes = null;
        if (biomePrefilter) {
            allowedBiomes = new HashSet<>();
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                Structure structure = entry.structure().value();
                for (Holder<Biome> b : structure.biomes()) {
                    allowedBiomes.add(b);
                }
            }
        }

        int spacing = rssp.spacing();
        int startI = Math.floorDiv(minX, spacing);
        int endI = Math.floorDiv(maxX, spacing);
        int startJ = Math.floorDiv(minZ, spacing);
        int endJ = Math.floorDiv(maxZ, spacing);

        long seed = level.getSeed();
        List<Records.StructureInfo> result = new ArrayList<>();

        for (int i = startI; i <= endI; i++) {
            for (int j = startJ; j <= endJ; j++) {
                int baseX = i * spacing;
                int baseZ = j * spacing;
                ChunkPos candidate = rssp.getPotentialStructureChunk(seed, baseX, baseZ);
                int x = candidate.x;
                int z = candidate.z;
                if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                if (!placement.isStructureChunk(state, x, z)) continue;

                BlockPos locatePos = placement.getLocatePos(candidate);
                if (biomePrefilter && allowedBiomes != null) {
                    int qx = QuartPos.fromBlock(locatePos.getX());
                    int qy = QuartPos.fromBlock(64);
                    int qz = QuartPos.fromBlock(locatePos.getZ());
                    Holder<Biome> sample = biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler());
                    if (!allowedBiomes.contains(sample)) continue;
                }

                result.add(new Records.StructureInfo(locatePos, "village"));
            }
        }

        return result;
    }

    private static ResourceLocation getPlacementTypeId(RegistryAccess access, StructurePlacement placement) {
        if (access == null || placement == null) return null;
        try {
            Registry<StructurePlacementType<?>> reg = access.registryOrThrow(Registries.STRUCTURE_PLACEMENT);
            return reg.getKey(placement.type());
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 暮色森林 forced_landmark 预测（不依赖暮色 mod 的编译期引用）。
     *
     * 原理：
     * - 暮色的 landmark center 以 16 chunk 为一个“region”，每个 region 会有一个中心点（chunk）。
     * - placement 内部提供了 isTFPlacementChunk(generator, state, chunkX, chunkZ) 用于按群系/种子挑选地标。
     * - 我们只枚举这些中心点，而不是扫描所有 chunk，从而保证性能。
     */
    private static void predictTwilightForestForcedLandmark(ServerLevel level,
                                                           ChunkGeneratorStructureState state,
                                                           StructurePlacement placement,
                                                           String labelId,
                                                           int minChunkX, int minChunkZ,
                                                           int maxChunkX, int maxChunkZ,
                                                           List<Records.StructureInfo> out) {
        if (level == null || state == null || placement == null || out == null) return;

        // regionX = floor((chunkX + 8) / 16)
        int regionMinX = Math.floorDiv(minChunkX + 8, 16);
        int regionMaxX = Math.floorDiv(maxChunkX + 8, 16);
        int regionMinZ = Math.floorDiv(minChunkZ + 8, 16);
        int regionMaxZ = Math.floorDiv(maxChunkZ + 8, 16);

        Method isTfPlacementChunk = null;
        try {
            // BiomeForcedLandmarkPlacement#isTFPlacementChunk(ChunkGenerator, ChunkGeneratorStructureState, int, int)
            isTfPlacementChunk = placement.getClass().getMethod(
                    "isTFPlacementChunk",
                    net.minecraft.world.level.chunk.ChunkGenerator.class,
                    ChunkGeneratorStructureState.class,
                    int.class,
                    int.class
            );
        } catch (Throwable ignored) {
            // 无该方法则回退到 placement.isStructureChunk
        }

        var generator = level.getChunkSource().getGenerator();

        for (int rx = regionMinX; rx <= regionMaxX; rx++) {
            for (int rz = regionMinZ; rz <= regionMaxZ; rz++) {
                ChunkPos center = tfLegacyLandmarkCenterChunk(rx, rz);
                int cx = center.x;
                int cz = center.z;
                if (cx < minChunkX || cx > maxChunkX || cz < minChunkZ || cz > maxChunkZ) continue;

                boolean ok;
                try {
                    if (isTfPlacementChunk != null) {
                        ok = (boolean) isTfPlacementChunk.invoke(placement, generator, state, cx, cz);
                    } else {
                        ok = placement.isStructureChunk(state, cx, cz);
                    }
                } catch (Throwable t) {
                    // 任何异常都视为“不支持预测”，避免影响主流程
                    continue;
                }

                if (!ok) continue;

                BlockPos locatePos = placement.getLocatePos(center);
                out.add(new Records.StructureInfo(locatePos, labelId));
            }
        }
    }

    // 复刻 TwilightForest LegacyLandmarkPlacements#getNearestCenterXZ 的中心点计算。
    // 输入为 region 坐标（每 16 chunk 一格），输出为该 region 的中心 chunk。
    private static ChunkPos tfLegacyLandmarkCenterChunk(int regionX, int regionZ) {
        long seed = regionX * 3129871L ^ regionZ * 116129781L;
        seed = seed * seed * 42317861L + seed * 7L;

        int num0 = (int) (seed >> 12 & 3L);
        int num1 = (int) (seed >> 15 & 3L);
        int num2 = (int) (seed >> 18 & 3L);
        int num3 = (int) (seed >> 21 & 3L);

        int centerX = 8 + num0 - num1;
        int centerZ = 8 + num2 - num3;

        int ccx;
        if (regionX >= 0) {
            ccx = (regionX * 16 + centerX - 8) * 16 + 8;
        } else {
            ccx = (regionX * 16 + (16 - centerX) - 8) * 16 + 9;
        }

        int ccz;
        if (regionZ >= 0) {
            ccz = (regionZ * 16 + centerZ - 8) * 16 + 8;
        } else {
            ccz = (regionZ * 16 + (16 - centerZ) - 8) * 16 + 9;
        }

        return new ChunkPos(ccx >> 4, ccz >> 4);
    }

    public static List<Records.StructureInfo> predictOverworldStructuresInRect(ServerLevel level,
                                                                               int minChunkX,
                                                                               int minChunkZ,
                                                                               int maxChunkX,
                                                                               int maxChunkZ,
                                                                               boolean biomePrefilter,
                                                                               List<String> whitelist,
                                                                               List<String> blacklist) {
        RegistryAccess access = level.registryAccess();

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        RandomState randomState = state.randomState();
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();

        Filters filters = Filters.of(whitelist, blacklist);

        List<Records.StructureInfo> result = new ArrayList<>();

        // 关键：只遍历“当前维度/群系可能生成的 StructureSet”。
        // 原版也会用 biomeSource.possibleBiomes 过滤 structure sets；否则在第三方维度会枚举大量不可能结构，导致卡顿。
        List<Holder<StructureSet>> possibleSets = state.possibleStructureSets();
        for (Holder<StructureSet> holder : possibleSets) {
            StructureSet set = holder.value();
            StructurePlacement placement = set.placement();

            // 计算该集合中“被允许”的结构（根据白/黑名单筛选）
            List<Holder<Structure>> matchedStructures = new ArrayList<>();
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                Holder<Structure> structureHolder = entry.structure();
                Optional<ResourceKey<Structure>> key = structureHolder.unwrapKey();
                if (key.isEmpty()) continue;
                ResourceLocation id = key.get().location();
                if (filters.matches(structureHolder, id)) {
                    matchedStructures.add(structureHolder);
                }
            }

            if (matchedStructures.isEmpty()) {
                if (filters.hasWhitelist()) continue;
                for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                    Holder<Structure> structureHolder = entry.structure();
                    Optional<ResourceKey<Structure>> key = structureHolder.unwrapKey();
                    if (key.isEmpty()) continue;
                    ResourceLocation id = key.get().location();
                    if (!filters.isBlacklisted(structureHolder, id)) {
                        matchedStructures.add(structureHolder);
                    }
                }
                if (matchedStructures.isEmpty()) continue;
            }

            // 代表性结构ID（用于标注），选择第一个匹配结构的 ID
            String labelId = matchedStructures.stream()
                    .map(h -> h.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString).orElse("structure"))
                    .findFirst().orElse("structure");

            // 1.20.1 原版 placement 类型：RandomSpread / ConcentricRings。
            // ConcentricRings 典型例子是 Stronghold（环状分布）。
            if (placement instanceof ConcentricRingsStructurePlacement crsp) {
                List<ChunkPos> ring = state.getRingPositionsFor(crsp);
                if (ring == null || ring.isEmpty()) {
                    continue;
                }
                for (ChunkPos cp : ring) {
                    int x = cp.x;
                    int z = cp.z;
                    if (x < minChunkX || x > maxChunkX || z < minChunkZ || z > maxChunkZ) continue;
                    if (!placement.isStructureChunk(state, x, z)) continue;
                    BlockPos locatePos = placement.getLocatePos(cp);
                    result.add(new Records.StructureInfo(locatePos, labelId));
                }
                continue;
            }

            // Twilight Forest 等第三方维度常用自定义 placement。
            // 目前只对暮色森林的 forced_landmark 做专门支持（避免维度内无结构点 + 兼容多维度需求）。
            ResourceLocation placementTypeId = getPlacementTypeId(access, placement);
            if (placementTypeId != null && "twilightforest".equals(placementTypeId.getNamespace())
                    && "forced_landmark".equals(placementTypeId.getPath())) {
                predictTwilightForestForcedLandmark(level, state, placement, labelId, minChunkX, minChunkZ, maxChunkX, maxChunkZ, result);
                continue;
            }

            if (!(placement instanceof RandomSpreadStructurePlacement rssp)) {
                continue;
            }

            Set<Holder<Biome>> allowedBiomes = null;
            if (biomePrefilter) {
                allowedBiomes = new HashSet<>();
                for (Holder<Structure> h : matchedStructures) {
                    for (Holder<Biome> b : h.value().biomes()) {
                        allowedBiomes.add(b);
                    }
                }
            }

            int spacing = rssp.spacing();
            int startI = Math.floorDiv(minChunkX, spacing);
            int endI = Math.floorDiv(maxChunkX, spacing);
            int startJ = Math.floorDiv(minChunkZ, spacing);
            int endJ = Math.floorDiv(maxChunkZ, spacing);

            for (int i = startI; i <= endI; i++) {
                for (int j = startJ; j <= endJ; j++) {
                    int baseX = i * spacing;
                    int baseZ = j * spacing;
                    ChunkPos candidate = rssp.getPotentialStructureChunk(level.getSeed(), baseX, baseZ);
                    int x = candidate.x;
                    int z = candidate.z;
                    if (x < minChunkX || x > maxChunkX || z < minChunkZ || z > maxChunkZ) continue;
                    if (!placement.isStructureChunk(state, x, z)) continue;

                    BlockPos locatePos = placement.getLocatePos(candidate);
                    int qx = QuartPos.fromBlock(locatePos.getX());
                    int qy = QuartPos.fromBlock(64);
                    int qz = QuartPos.fromBlock(locatePos.getZ());
                    Holder<Biome> sample = biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler());
                    if (biomePrefilter && allowedBiomes != null) {
                        if (!allowedBiomes.contains(sample)) continue;
                    }

                    String chosenId = labelId;
                    for (Holder<Structure> h : matchedStructures) {
                        if (h.value().biomes().contains(sample)) {
                            chosenId = h.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString).orElse(labelId);
                            break;
                        }
                    }
                    result.add(new Records.StructureInfo(locatePos, chosenId));
                }
            }
        }

        return result;
    }

    public static List<Records.StructureInfo> predictOverworldStructuresAroundSpawn(ServerLevel level,
                                                                                   int radiusChunks,
                                                                                   boolean biomePrefilter,
                                                                                   List<String> whitelist,
                                                                                   List<String> blacklist) {
        RegistryAccess access = level.registryAccess();
        Registry<StructureSet> setRegistry = access.registryOrThrow(Registries.STRUCTURE_SET);

        BlockPos spawn = level.getSharedSpawnPos();
        int cx = spawn.getX() >> 4;
        int cz = spawn.getZ() >> 4;
        int minX = cx - radiusChunks;
        int maxX = cx + radiusChunks;
        int minZ = cz - radiusChunks;
        int maxZ = cz + radiusChunks;

        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        RandomState randomState = state.randomState();
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();

        Filters filters = Filters.of(whitelist, blacklist);

        List<Records.StructureInfo> result = new ArrayList<>();

        for (Holder.Reference<StructureSet> holder : setRegistry.holders().toList()) {
            StructureSet set = holder.value();
            StructurePlacement placement = set.placement();
            if (!(placement instanceof RandomSpreadStructurePlacement rssp)) continue;

            List<Holder<Structure>> matchedStructures = new ArrayList<>();
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                Holder<Structure> structureHolder = entry.structure();
                Optional<ResourceKey<Structure>> key = structureHolder.unwrapKey();
                if (key.isEmpty()) continue;
                ResourceLocation id = key.get().location();
                if (filters.matches(structureHolder, id)) {
                    matchedStructures.add(structureHolder);
                }
            }

            if (matchedStructures.isEmpty()) {
                if (filters.hasWhitelist()) continue;
                for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                    Holder<Structure> structureHolder = entry.structure();
                    Optional<ResourceKey<Structure>> key = structureHolder.unwrapKey();
                    if (key.isEmpty()) continue;
                    ResourceLocation id = key.get().location();
                    if (!filters.isBlacklisted(structureHolder, id)) {
                        matchedStructures.add(structureHolder);
                    }
                }
                if (matchedStructures.isEmpty()) continue;
            }

            Set<Holder<Biome>> allowedBiomes = null;
            if (biomePrefilter) {
                allowedBiomes = new HashSet<>();
                for (Holder<Structure> h : matchedStructures) {
                    for (Holder<Biome> b : h.value().biomes()) {
                        allowedBiomes.add(b);
                    }
                }
            }

            int spacing = rssp.spacing();
            int startI = Math.floorDiv(minX, spacing);
            int endI = Math.floorDiv(maxX, spacing);
            int startJ = Math.floorDiv(minZ, spacing);
            int endJ = Math.floorDiv(maxZ, spacing);

            String labelId = matchedStructures.stream()
                    .map(h -> h.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString).orElse("structure"))
                    .findFirst().orElse("structure");

            for (int i = startI; i <= endI; i++) {
                for (int j = startJ; j <= endJ; j++) {
                    int baseX = i * spacing;
                    int baseZ = j * spacing;
                    ChunkPos candidate = rssp.getPotentialStructureChunk(level.getSeed(), baseX, baseZ);
                    int x = candidate.x;
                    int z = candidate.z;
                    if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                    if (!placement.isStructureChunk(state, x, z)) continue;
                    BlockPos locatePos = placement.getLocatePos(candidate);
                    int qx = QuartPos.fromBlock(locatePos.getX());
                    int qy = QuartPos.fromBlock(64);
                    int qz = QuartPos.fromBlock(locatePos.getZ());
                    Holder<Biome> sample = biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler());
                    if (biomePrefilter && allowedBiomes != null) {
                        if (!allowedBiomes.contains(sample)) continue;
                    }
                    String chosenId = labelId;
                    for (Holder<Structure> h : matchedStructures) {
                        if (h.value().biomes().contains(sample)) {
                            chosenId = h.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString).orElse(labelId);
                            break;
                        }
                    }
                    result.add(new Records.StructureInfo(locatePos, chosenId));
                }
            }
        }

        return result;
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

        boolean hasWhitelist() { return !whitelist.isEmpty(); }

        boolean matches(Holder<Structure> holder, ResourceLocation id) {
            boolean whiteOk = whitelist.isEmpty() || whitelist.stream().anyMatch(p -> matchesPattern(holder, id, p));
            boolean blackHit = blacklist.stream().anyMatch(p -> matchesPattern(holder, id, p));
            return whiteOk && !blackHit;
        }

        boolean isBlacklisted(Holder<Structure> holder, ResourceLocation id) {
            return blacklist.stream().anyMatch(p -> matchesPattern(holder, id, p));
        }

        private boolean matchesPattern(Holder<Structure> holder, ResourceLocation id, String pattern) {
            if (pattern == null || pattern.isEmpty()) return false;
            String p = pattern.trim().toLowerCase(Locale.ROOT);
            String idStr = id.toString().toLowerCase(Locale.ROOT);
            if (p.startsWith("#")) {
                String raw = p.substring(1);
                ResourceLocation tagId = ResourceLocation.tryParse(raw);
                if (tagId == null) return false;
                TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, tagId);
                return holder.is(tag);
            }
            if (p.endsWith("/*")) {
                String base = p.substring(0, p.length() - 2);
                if (idStr.startsWith(base + "/")) return true;
                if (idStr.startsWith(base + "_")) return true;
                if (idStr.startsWith(base + "-")) return true;
                if (idStr.startsWith(base + ".")) return true;
                return false;
            }
            if (p.endsWith(":*")) {
                String ns = p.substring(0, p.length() - 2);
                int idx = ns.indexOf(':');
                if (idx > 0) ns = ns.substring(0, idx);
                return id.getNamespace().equalsIgnoreCase(ns);
            }
            return idStr.equals(p);
        }

        private static List<String> normalize(List<String> src) {
            List<String> out = new ArrayList<>();
            if (src == null) return out;
            for (String s : src) {
                if (s == null) continue;
                String v = s.trim().toLowerCase(Locale.ROOT);
                if (!v.isEmpty()) out.add(v);
            }
            return out;
        }
    }
}
