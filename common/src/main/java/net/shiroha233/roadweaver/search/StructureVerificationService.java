package net.shiroha233.roadweaver.search;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import net.shiroha233.roadweaver.helpers.Records;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构预测点验证服务：
 * 利用 vanilla 的 StructureCheck + ChunkScanAccess，对基于种子/噪声预测出的结构点做轻量验证，
 * 尽量剔除"伪结构点"，只保留真实存在结构起点的区块。
 *
 * 注意：
 * - 仅在后台线程（ComputeService / 规划线程等）调用，避免在主线程上进行磁盘 IO。
 * - 若底层环境不支持 chunkScanner 或出现异常，将直接返回原预测列表，不影响主流程。
 */
public final class StructureVerificationService {
    private StructureVerificationService() {}

    /**
     * 对一批预测结构点进行验证，返回"通过验证"的子集。
     *
     * 调用约定：只能在后台线程调用（例如 ComputeService 的线程池中），不要在主线程直接调用，
     * 否则 scanChunk 的 join 可能造成卡顿。
     */
    public static List<Records.StructureInfo> verifyPredictedStructures(ServerLevel level,
                                                                        List<Records.StructureInfo> predicted) {
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

        // 获取 ChunkScanAccess
        ChunkScanAccess scanAccess;
        try {
            scanAccess = chunkCache.chunkScanner();
        } catch (Throwable t) {
            // 某些环境若缺失该接口，直接退化为"不做验证"
            return new ArrayList<>(predicted);
        }

        var registryAccess = level.registryAccess();
        ChunkGenerator generator = chunkCache.getGenerator();
        RandomState randomState = chunkCache.randomState();
        BiomeSource biomeSource = generator.getBiomeSource();
        long seed = level.getSeed();

        // 构建 StructureCheck 实例
        StructureCheck checker = new StructureCheck(
                scanAccess,
                registryAccess,
                server.getStructureManager(),
                level.dimension(),
                generator,
                randomState,
                level,
                biomeSource,
                seed,
                server.getFixerUpper()
        );

        HolderLookup.RegistryLookup<Structure> structureLookup = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        HolderLookup.RegistryLookup<StructureSet> structureSetLookup = registryAccess.lookupOrThrow(Registries.STRUCTURE_SET);

        // 构建 Structure -> StructurePlacement 的映射缓存
        Map<Structure, StructurePlacement> placementCache = new HashMap<>();
        structureSetLookup.listElements().forEach(setHolder -> {
            StructureSet set = setHolder.value();
            StructurePlacement placement = set.placement();
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                Structure structure = entry.structure().value();
                // 一个 Structure 可能属于多个 Set，这里取第一个找到的 placement
                placementCache.putIfAbsent(structure, placement);
            }
        });

        ArrayList<Records.StructureInfo> result = new ArrayList<>();

        for (Records.StructureInfo info : predicted) {
            String idStr = info.structureId();
            if (idStr == null || idStr.isEmpty()) {
                result.add(info);
                continue;
            }

            ResourceLocation rl = ResourceLocation.tryParse(idStr);
            if (rl == null) {
                result.add(info);
                continue;
            }

            ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, rl);
            Holder<Structure> structureHolder = structureLookup.get(structureKey).orElse(null);
            if (structureHolder == null) {
                result.add(info);
                continue;
            }
            Structure structure = structureHolder.value();

            // 获取对应的 StructurePlacement
            StructurePlacement placement = placementCache.get(structure);
            if (placement == null) {
                // 找不到 placement，保留该点
                result.add(info);
                continue;
            }

            ChunkPos chunkPos = new ChunkPos(info.pos().getX() >> 4, info.pos().getZ() >> 4);

            StructureCheckResult checkResult;
            try {
                checkResult = checker.checkStart(chunkPos, structure, placement, false);
            } catch (Throwable t) {
                result.add(info);
                continue;
            }

            if (checkResult == StructureCheckResult.START_PRESENT) {
                result.add(info);
            } else if (checkResult == StructureCheckResult.CHUNK_LOAD_NEEDED) {
                // 需要加载区块才能确认，保守保留
                result.add(info);
            }
            // START_NOT_PRESENT：丢弃
        }

        return result;
    }
}
