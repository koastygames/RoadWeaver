package net.countered.settlementroads.features.structure;

import net.countered.settlementroads.config.ConfigProvider;
import net.countered.settlementroads.config.IModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 道路旁结构生成器
 * 在道路生成时触发原版结构生成系统
 */
public class RoadsideStructureSpawner {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    
    // 记录已生成结构的位置，避免过于密集
    private static final Map<String, Set<ChunkPos>> generatedStructures = new ConcurrentHashMap<>();
    
    /**
     * 尝试在道路旁生成结构
     * 
     * @param level 服务器世界
     * @param roadPosition 道路位置
     * @param offsetX X偏移量
     * @param offsetZ Z偏移量
     * @param random 随机源
     * @return 是否成功生成
     */
    public static boolean trySpawnStructure(ServerLevel level, BlockPos roadPosition, 
                                           int offsetX, int offsetZ, RandomSource random) {
        try {
            // 空值安全检查
            if (level == null || roadPosition == null || random == null) {
                LOGGER.warn("❌ 参数为空，跳过结构生成");
                return false;
            }
            
            IModConfig config = ConfigProvider.get();
            if (config == null) {
                LOGGER.error("❌ 配置为空，跳过结构生成");
                return false;
            }
            
            LOGGER.info("🎯 道路旁结构生成触发 @ {}", roadPosition);
            
            // 检查功能是否启用
            if (!config.enableRoadsideStructures()) {
                LOGGER.info("❌ 功能未启用 (enableRoadsideStructures=false)");
                return false;
            }
            
            // 检查生成概率
            float chance = random.nextFloat();
            float configChance = config.roadsideStructureSpawnChance();
            LOGGER.info("🎲 概率检查: {} <= {} ?", chance, configChance);
            if (chance > configChance) {
                LOGGER.info("❌ 概率检查失败，跳过生成");
                return false;
            }
            
            // 计算生成位置
            BlockPos spawnPos = roadPosition.offset(offsetX, 0, offsetZ);
            ChunkPos chunkPos = new ChunkPos(spawnPos);
            
            // 检查距离限制
            if (!checkMinDistance(level, chunkPos, config.minDistanceBetweenRoadsideStructures())) {
                LOGGER.debug("跳过结构生成：距离上次生成过近 (位置: {})", chunkPos);
                return false;
            }
            
            // 获取配置的结构标签列表
            List<String> structureTags = config.roadsideStructureTags();
            if (structureTags == null || structureTags.isEmpty()) {
                LOGGER.warn("❌ 结构标签列表为空！请在配置中添加标签");
                return false;
            }
            
            LOGGER.info("📋 配置的结构标签列表: {}", structureTags);
            
            // 随机选择一个标签
            String selectedTag = structureTags.get(random.nextInt(structureTags.size()));
            if (selectedTag == null || selectedTag.isEmpty()) {
                LOGGER.warn("❌ 选中的标签为空");
                return false;
            }
            
            LOGGER.info("🎯 选中的结构标签: {}", selectedTag);
            
            // 尝试生成结构
            boolean success = spawnStructureFromTag(level, spawnPos, selectedTag, random);
            
            if (success) {
                // 记录生成位置
                String worldKey = level.dimension().location().toString();
                generatedStructures.computeIfAbsent(worldKey, k -> ConcurrentHashMap.newKeySet())
                                  .add(chunkPos);
                LOGGER.info("✅ 道路旁结构生成成功: {} at {}", selectedTag, spawnPos);
            }
            
            return success;
        } catch (Exception e) {
            LOGGER.error("❌ 道路旁结构生成发生异常", e);
            return false;
        }
    }
    
    /**
     * 从标签生成结构
     */
    private static boolean spawnStructureFromTag(ServerLevel level, BlockPos pos, 
                                                String tagOrId, RandomSource random) {
        try {
            var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            
            // 判断是标签还是直接ID还是通配符
            if (tagOrId.startsWith("#")) {
                // 标签模式
                String tagName = tagOrId.substring(1);
                TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, 
                    ResourceLocation.parse(tagName));
                
                // 获取标签中的所有结构
                List<Holder<Structure>> structures = new ArrayList<>();
                registry.getTagOrEmpty(tag).forEach(structures::add);
                
                LOGGER.info("📦 标签 {} 中找到 {} 个结构", tagName, structures.size());
                if (structures.isEmpty()) {
                    LOGGER.warn("❌ 标签 {} 中没有找到结构", tagName);
                    return false;
                }
                
                // 随机选择一个结构
                Holder<Structure> selected = structures.get(random.nextInt(structures.size()));
                return spawnStructure(level, pos, selected.value(), random);
                
            } else if (tagOrId.contains("*")) {
                // 通配符模式（例如：minecraft:village_*）
                LOGGER.info("🌟 使用通配符模式: {}", tagOrId);
                String pattern = tagOrId.replace("*", "");
                List<Holder<Structure>> matchedStructures = new ArrayList<>();
                
                for (var entry : registry.entrySet()) {
                    String structureId = entry.getKey().location().toString();
                    if (structureId.startsWith(pattern)) {
                        registry.getHolder(entry.getKey()).ifPresent(matchedStructures::add);
                    }
                }
                
                LOGGER.info("📦 通配符 {} 匹配到 {} 个结构", pattern, matchedStructures.size());
                if (matchedStructures.isEmpty()) {
                    LOGGER.warn("❌ 通配符 {} 没有匹配到任何结构", pattern);
                    return false;
                }
                
                // 随机选择一个匹配的结构
                Holder<Structure> selected = matchedStructures.get(random.nextInt(matchedStructures.size()));
                String selectedId = registry.getKey(selected.value()).toString();
                LOGGER.info("🎲 从通配符中随机选择: {}", selectedId);
                return spawnStructure(level, pos, selected.value(), random);
                
            } else {
                // 直接ID模式
                LOGGER.info("🔍 使用直接ID模式: {}", tagOrId);
                ResourceLocation structureId = ResourceLocation.parse(tagOrId);
                ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, structureId);
                
                Structure structure = registry.get(key);
                if (structure != null) {
                    LOGGER.info("✅ 找到结构: {}", structureId);
                    return spawnStructure(level, pos, structure, random);
                } else {
                    LOGGER.warn("❌ 未找到结构: {}", structureId);
                    return false;
                }
            }
        } catch (Exception e) {
            LOGGER.error("❌ 生成结构时出错: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 执行结构生成
     */
    private static boolean spawnStructure(ServerLevel level, BlockPos pos, 
                                         Structure structure, RandomSource random) {
        try {
            ChunkPos chunkPos = new ChunkPos(pos);
            ChunkGenerator chunkGen = level.getChunkSource().getGenerator();
            
            // 创建结构开始
            StructureStart start = structure.generate(
                level.registryAccess(),
                chunkGen,
                chunkGen.getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getStructureManager(),
                level.getSeed(),
                chunkPos,
                0, // height reference
                level,
                biome -> true // 接受所有生物群系
            );
            
            // 检查结构是否有效
            if (start == StructureStart.INVALID_START) {
                LOGGER.debug("结构生成失败：INVALID_START");
                return false;
            }
            
            // 在世界中放置结构
            start.placeInChunk(
                level,
                level.structureManager(),
                chunkGen,
                random,
                start.getBoundingBox(),
                chunkPos
            );
            
            return true;
            
        } catch (Exception e) {
            LOGGER.error("放置结构时出错: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 检查与最近生成结构的距离
     */
    private static boolean checkMinDistance(ServerLevel level, ChunkPos newPos, int minDistance) {
        String worldKey = level.dimension().location().toString();
        Set<ChunkPos> existingPositions = generatedStructures.get(worldKey);
        
        if (existingPositions == null || existingPositions.isEmpty()) {
            return true;
        }
        
        // 将方块距离转换为区块距离
        int minChunkDistance = minDistance / 16;
        
        for (ChunkPos existing : existingPositions) {
            int dx = Math.abs(existing.x - newPos.x);
            int dz = Math.abs(existing.z - newPos.z);
            int distance = Math.max(dx, dz); // 切比雪夫距离
            
            if (distance < minChunkDistance) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 清理指定世界的生成记录
     */
    public static void clearWorldData(ServerLevel level) {
        String worldKey = level.dimension().location().toString();
        generatedStructures.remove(worldKey);
        LOGGER.debug("已清理世界 {} 的道路旁结构记录", worldKey);
    }
    
    /**
     * 清理所有世界的生成记录
     */
    public static void clearAll() {
        generatedStructures.clear();
        LOGGER.debug("已清理所有道路旁结构记录");
    }
}
