package net.shiroha233.roadweaver.structures.precompute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.TerrainSamplingCache;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.structures.data.BiomeCategory;
import net.shiroha233.roadweaver.structures.data.StructureScale;
import net.shiroha233.roadweaver.structures.registry.RoadsideStructureRegistry;
import net.shiroha233.roadweaver.structures.registry.RoadsideStructureRegistry.RoadsideStructureEntry;
import net.shiroha233.roadweaver.structures.types.RoadsideStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 路边结构预计算器
 * 
 * 在道路寻路完成后，预计算结构放置位置并存储到 PendingStructureStorage。
 * 这样在区块 STRUCTURE_STARTS 阶段可以注入结构，让 Beardifier 自动处理地形适应。
 */
public final class RoadsideStructurePrecomputer {
    private RoadsideStructurePrecomputer() {}
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/StructurePrecomputer");
    
    /**
     * 预计算道路沿线的结构放置位置
     * 
     * @param level    服务端世界
     * @param segments 道路路径段
     * @param spans    道路跨度（桥梁等）
     * @param width    道路宽度
     * @param cache    地形采样缓存
     * @param random   随机源
     * @return 预计算的结构数量
     */
    public static int precomputeStructures(ServerLevel level,
                                           List<Records.RoadSegmentPlacement> segments,
                                           List<Records.RoadSpan> spans,
                                           int width,
                                           TerrainSamplingCache cache,
                                           RandomSource random) {
        if (segments == null || segments.size() < 10) {
            return 0;
        }
        
        ModConfig cfg = ConfigService.get();
        
        // 检查是否启用路边结构
        if (!cfg.roadsideStructuresEnabled()) {
            return 0;
        }
        
        int maxStructures = cfg.maxStructuresPerRoad();
        if (maxStructures <= 0) {
            return 0;
        }
        
        // 获取可用的路边结构
        List<RoadsideStructureEntry> allStructures = RoadsideStructureRegistry.getAll(level);
        if (allStructures.isEmpty()) {
            return 0;
        }
        
        // 标记桥梁段
        Set<Integer> bridgeIndices = new HashSet<>();
        if (spans != null) {
            for (Records.RoadSpan span : spans) {
                if (span.type() == Records.SpanType.BRIDGE) {
                    for (int i = 0; i < segments.size(); i++) {
                        BlockPos pos = segments.get(i).middlePos();
                        if (isInSpan(pos, span)) {
                            bridgeIndices.add(i);
                        }
                    }
                }
            }
        }
        
        // 计算检查间隔
        int roadLength = segments.size();
        int checkInterval = Math.max(1, roadLength / (maxStructures + 1));
        
        // 记录已放置的位置（避免重叠）
        Set<Long> placedChunks = new HashSet<>();
        int placedCount = 0;
        
        for (int i = checkInterval; i < roadLength - checkInterval && placedCount < maxStructures; i += checkInterval) {
            // 跳过桥梁段
            if (bridgeIndices.contains(i)) {
                continue;
            }
            
            BlockPos middle = segments.get(i).middlePos();
            
            // 使用较大窗口（±10 segments，约10格）计算稳定的切线方向
            // segment 间距约1格，所以 ±10 覆盖约20格，足以平滑局部抖动
            int windowSize = 10;
            BlockPos prev = segments.get(Math.max(0, i - windowSize)).middlePos();
            BlockPos next = segments.get(Math.min(roadLength - 1, i + windowSize)).middlePos();
            
            // 计算道路方向（切线）
            double dirX = next.getX() - prev.getX();
            double dirZ = next.getZ() - prev.getZ();
            double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (len < 0.01) continue;
            dirX /= len;
            dirZ /= len;
            
            // 获取群系（使用噪声采样，不触发区块加载）
            Holder<Biome> biomeHolder = cache.getBiome(level, middle.getX(), middle.getZ());
            BiomeCategory category = BiomeCategory.fromBiome(biomeHolder);
            
            // 选择合适的结构
            RoadsideStructureEntry entry = selectStructure(allStructures, category, roadLength, random);
            if (entry == null) {
                continue;
            }
            
            RoadsideStructure structure = entry.structure();
            Vec3i sizeHint = structure.sizeHint();
            
            // 计算放置位置（道路两侧）
            boolean leftSide = random.nextBoolean();
            int offset = getOffsetForScale(structure.scale(), cfg);

            // 先计算旋转（需要知道旋转才能正确补偿锚点偏移）
            Rotation rotation = calculateRotation(dirX, dirZ, leftSide, structure.faceRoad());
            
            // 使用道路方向的法线（perpendicular）进行侧向偏移
            // 2D 向量旋转90°：左侧 (-dirZ, dirX)，右侧 (dirZ, -dirX)
            double perpX = leftSide ? -dirZ : dirZ;
            double perpZ = leftSide ? dirX : -dirX;

            // 计算结构中心点应该在的位置
            // offset 是结构边缘到道路中心的距离，所以结构中心需要再偏移半个结构尺寸
            // 根据旋转，结构在法线方向上的"半径"不同
            int sizeX = sizeHint.getX();
            int sizeZ = sizeHint.getZ();
            
            // 计算旋转后结构在法线方向上的半尺寸
            // 法线方向 (perpX, perpZ) 是单位向量
            double halfExtentInPerpDir;
            switch (rotation) {
                case NONE, CLOCKWISE_180 -> {
                    // 结构沿原始 X/Z 轴
                    halfExtentInPerpDir = (Math.abs(perpX) * sizeX + Math.abs(perpZ) * sizeZ) / 2.0;
                }
                case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> {
                    // 结构旋转90°，X/Z 互换
                    halfExtentInPerpDir = (Math.abs(perpX) * sizeZ + Math.abs(perpZ) * sizeX) / 2.0;
                }
                default -> halfExtentInPerpDir = Math.max(sizeX, sizeZ) / 2.0;
            }
            
            // 结构中心到道路中心的距离 = 边缘距离 + 结构半尺寸
            double centerOffset = offset + halfExtentInPerpDir;
            
            int placeX = middle.getX() + (int) Math.round(perpX * centerOffset);
            int placeZ = middle.getZ() + (int) Math.round(perpZ * centerOffset);
            int placeY = cache.height(level, placeX, placeZ);
            
            // 锚点在结构角落，需要从中心点反推锚点位置
            // 旋转后结构的延伸方向不同，需要相应调整
            int anchorX = placeX;
            int anchorZ = placeZ;
            switch (rotation) {
                case NONE -> {
                    // 结构向 +X, +Z 延伸，锚点在西北角（中心偏移 -halfX, -halfZ）
                    anchorX -= sizeX / 2;
                    anchorZ -= sizeZ / 2;
                }
                case CLOCKWISE_90 -> {
                    // 结构向 +Z, -X 延伸（原 X 变 Z，原 Z 变 -X）
                    anchorX += sizeZ / 2;
                    anchorZ -= sizeX / 2;
                }
                case CLOCKWISE_180 -> {
                    // 结构向 -X, -Z 延伸
                    anchorX += sizeX / 2;
                    anchorZ += sizeZ / 2;
                }
                case COUNTERCLOCKWISE_90 -> {
                    // 结构向 -Z, +X 延伸
                    anchorX -= sizeZ / 2;
                    anchorZ += sizeX / 2;
                }
            }
            
            BlockPos placePos = new BlockPos(anchorX, placeY, anchorZ);
            
            // 检查区块是否已有结构
            ChunkPos chunkPos = new ChunkPos(placePos);
            long chunkKey = chunkPos.toLong();
            if (placedChunks.contains(chunkKey)) {
                continue;
            }
            
            // 检查地形条件
            if (!checkTerrainConditions(cache, level, placePos, sizeHint)) {
                continue;
            }
            
            // 添加到待放置存储（rotation 已在前面计算）
            PendingStructureStorage.addPendingStructure(
                level,
                entry.id(),
                placePos,
                rotation,
                sizeHint.getX(),
                sizeHint.getY(),
                sizeHint.getZ()
            );
            
            placedChunks.add(chunkKey);
            placedCount++;
            
            LOGGER.debug("Precomputed structure {} at {} for chunk [{}, {}]",
                entry.id(), placePos, chunkPos.x, chunkPos.z);
        }
        
        return placedCount;
    }
    
    /**
     * 检查位置是否在跨度范围内
     */
    private static boolean isInSpan(BlockPos pos, Records.RoadSpan span) {
        int minX = Math.min(span.start().getX(), span.end().getX());
        int maxX = Math.max(span.start().getX(), span.end().getX());
        int minZ = Math.min(span.start().getZ(), span.end().getZ());
        int maxZ = Math.max(span.start().getZ(), span.end().getZ());
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
    
    /**
     * 选择合适的结构
     */
    private static RoadsideStructureEntry selectStructure(List<RoadsideStructureEntry> structures,
                                                          BiomeCategory category,
                                                          int roadLength,
                                                          RandomSource random) {
        List<RoadsideStructureEntry> candidates = new ArrayList<>();
        int totalWeight = 0;
        
        for (RoadsideStructureEntry entry : structures) {
            RoadsideStructure structure = entry.structure();
            
            // 检查群系匹配
            if (!structure.placementRule().isBiomeAllowed(category)) {
                continue;
            }
            
            // 检查道路长度
            if (roadLength < structure.placementRule().minRoadLength()) {
                continue;
            }
            
            candidates.add(entry);
            totalWeight += structure.weight();
        }
        
        if (candidates.isEmpty() || totalWeight <= 0) {
            return null;
        }
        
        // 加权随机选择
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (RoadsideStructureEntry entry : candidates) {
            cumulative += entry.structure().weight();
            if (roll < cumulative) {
                return entry;
            }
        }
        
        return candidates.get(candidates.size() - 1);
    }
    
    /**
     * 检查地形条件
     * 注意：使用 cache 的噪声采样方法，避免触发区块加载
     */
    private static boolean checkTerrainConditions(TerrainSamplingCache cache,
                                                  ServerLevel level,
                                                  BlockPos pos,
                                                  Vec3i size) {
        // 使用 cache 检查是否在水中，避免触发区块加载
        if (cache.isColumnWater(level, pos.getX(), pos.getZ())) {
            return false;
        }
        
        // 检查坡度
        int centerY = cache.height(level, pos.getX(), pos.getZ());
        int halfX = size.getX() / 2;
        int halfZ = size.getZ() / 2;
        
        int maxSlope = 3;
        int y1 = cache.height(level, pos.getX() - halfX, pos.getZ());
        int y2 = cache.height(level, pos.getX() + halfX, pos.getZ());
        int y3 = cache.height(level, pos.getX(), pos.getZ() - halfZ);
        int y4 = cache.height(level, pos.getX(), pos.getZ() + halfZ);
        
        return Math.abs(y1 - centerY) <= maxSlope &&
               Math.abs(y2 - centerY) <= maxSlope &&
               Math.abs(y3 - centerY) <= maxSlope &&
               Math.abs(y4 - centerY) <= maxSlope;
    }
    
    /**
     * 根据结构规模获取偏移距离
     */
    private static int getOffsetForScale(StructureScale scale, ModConfig cfg) {
        return switch (scale) {
            case SMALL -> cfg.smallStructureOffset();
            case MEDIUM -> cfg.mediumStructureOffset();
            case LARGE -> cfg.largeStructureOffset();
        };
    }
    
    /**
     * 计算结构旋转
     */
    private static Rotation calculateRotation(double dirX, double dirZ, boolean leftSide, boolean faceRoad) {
        if (!faceRoad) {
            return Rotation.NONE;
        }
        
        double absX = Math.abs(dirX);
        double absZ = Math.abs(dirZ);
        
        if (absX > absZ) {
            // 道路主要沿 X 轴
            if (leftSide) {
                return dirX > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE;
            } else {
                return dirX > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
            }
        } else {
            // 道路主要沿 Z 轴
            if (leftSide) {
                return dirZ > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
            } else {
                return dirZ > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
            }
        }
    }
}
