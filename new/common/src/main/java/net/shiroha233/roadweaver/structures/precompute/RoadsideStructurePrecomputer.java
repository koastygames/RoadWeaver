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
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
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
 * 职责：在道路寻路完成后预计算结构放置位置，存储到 PendingStructureStorage
 */
public final class RoadsideStructurePrecomputer {
    private RoadsideStructurePrecomputer() {}
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/StructurePrecomputer");
    
    public static int precomputeStructures(ServerLevel level,
                                           List<RoadSegmentPlacement> segments,
                                           List<RoadSpan> spans,
                                           int width,
                                           TerrainSamplingCache cache,
                                           RandomSource random,
                                           List<Integer> targetY) {
        if (segments == null || segments.size() < RoadConstants.MIN_ROAD_SEGMENTS_FOR_STRUCTURE) {
            return 0;
        }
        
        ModConfig cfg = ConfigService.get();

        String dimId = level.dimension().location().toString();
        if (!cfg.roadsideStructuresEnabledForDimension(dimId)) {
            return 0;
        }
        
        int maxStructures = cfg.roadsideStructure().maxStructuresPerRoad();
        if (maxStructures <= 0) {
            return 0;
        }
        
        List<RoadsideStructureEntry> allStructures = RoadsideStructureRegistry.getAll(level);
        if (allStructures.isEmpty()) {
            return 0;
        }
        
        Set<Integer> bridgeIndices = new HashSet<>();
        if (spans != null) {
            for (RoadSpan span : spans) {
                if (span.type() == SpanType.BRIDGE) {
                    for (int i = 0; i < segments.size(); i++) {
                        BlockPos pos = segments.get(i).middlePos();
                        if (isInSpan(pos, span)) {
                            bridgeIndices.add(i);
                        }
                    }
                }
            }
        }
        
        int roadLength = segments.size();
        int checkInterval = Math.max(1, roadLength / (maxStructures + 1));
        
        Set<Long> placedChunks = new HashSet<>();
        int placedCount = 0;
        
        for (int i = checkInterval; i < roadLength - checkInterval && placedCount < maxStructures; i += checkInterval) {
            if (bridgeIndices.contains(i)) {
                continue;
            }
            
            BlockPos middle = segments.get(i).middlePos();
            
            int windowSize = RoadConstants.STRUCTURE_PLACEMENT_WINDOW_SIZE;
            BlockPos prev = segments.get(Math.max(0, i - windowSize)).middlePos();
            BlockPos next = segments.get(Math.min(roadLength - 1, i + windowSize)).middlePos();
            
            double dirX = next.getX() - prev.getX();
            double dirZ = next.getZ() - prev.getZ();
            double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (len < 0.01) continue;
            dirX /= len;
            dirZ /= len;
            
            Holder<Biome> biomeHolder = cache.getBiome(level, middle.getX(), middle.getZ());
            BiomeCategory category = BiomeCategory.fromBiome(biomeHolder);
            
            RoadsideStructureEntry entry = selectStructure(allStructures, category, roadLength, random);
            if (entry == null) {
                continue;
            }
            
            RoadsideStructure structure = entry.structure();
            Vec3i sizeHint = structure.sizeHint();
            
            boolean leftSide = random.nextBoolean();
            int offset = getOffsetForScale(structure.scale(), cfg);

            Rotation rotation = calculateRotation(dirX, dirZ, leftSide, structure.faceRoad());
            
            double perpX = leftSide ? -dirZ : dirZ;
            double perpZ = leftSide ? dirX : -dirX;

            int sizeX = sizeHint.getX();
            int sizeZ = sizeHint.getZ();
            
            double halfExtentInPerpDir;
            switch (rotation) {
                case NONE, CLOCKWISE_180 -> {
                    halfExtentInPerpDir = (Math.abs(perpX) * sizeX + Math.abs(perpZ) * sizeZ) / 2.0;
                }
                case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> {
                    halfExtentInPerpDir = (Math.abs(perpX) * sizeZ + Math.abs(perpZ) * sizeX) / 2.0;
                }
                default -> halfExtentInPerpDir = Math.max(sizeX, sizeZ) / 2.0;
            }
            
            double centerOffset = offset + halfExtentInPerpDir;
            
            int placeX = middle.getX() + (int) Math.round(perpX * centerOffset);
            int placeZ = middle.getZ() + (int) Math.round(perpZ * centerOffset);
            int placeY = (targetY != null && i < targetY.size()) ? targetY.get(i) : cache.height(level, placeX, placeZ);
            
            int anchorX = placeX;
            int anchorZ = placeZ;
            switch (rotation) {
                case NONE -> {
                    anchorX -= sizeX / 2;
                    anchorZ -= sizeZ / 2;
                }
                case CLOCKWISE_90 -> {
                    anchorX += sizeZ / 2;
                    anchorZ -= sizeX / 2;
                }
                case CLOCKWISE_180 -> {
                    anchorX += sizeX / 2;
                    anchorZ += sizeZ / 2;
                }
                case COUNTERCLOCKWISE_90 -> {
                    anchorX -= sizeZ / 2;
                    anchorZ += sizeX / 2;
                }
            }
            
            BlockPos placePos = new BlockPos(anchorX, placeY, anchorZ);
            
            ChunkPos chunkPos = new ChunkPos(placePos);
            long chunkKey = chunkPos.toLong();
            if (placedChunks.contains(chunkKey)) {
                continue;
            }
            
            if (!checkTerrainConditions(cache, level, placePos, sizeHint)) {
                continue;
            }
            
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
    
    private static boolean isInSpan(BlockPos pos, RoadSpan span) {
        int minX = Math.min(span.start().getX(), span.end().getX());
        int maxX = Math.max(span.start().getX(), span.end().getX());
        int minZ = Math.min(span.start().getZ(), span.end().getZ());
        int maxZ = Math.max(span.start().getZ(), span.end().getZ());
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
    
    private static RoadsideStructureEntry selectStructure(List<RoadsideStructureEntry> structures,
                                                          BiomeCategory category,
                                                          int roadLength,
                                                          RandomSource random) {
        List<RoadsideStructureEntry> candidates = new ArrayList<>();
        int totalWeight = 0;
        
        for (RoadsideStructureEntry entry : structures) {
            RoadsideStructure structure = entry.structure();
            
            if (!structure.placementRule().isBiomeAllowed(category)) {
                continue;
            }
            
            if (roadLength < structure.placementRule().minRoadLength()) {
                continue;
            }
            
            candidates.add(entry);
            totalWeight += structure.weight();
        }
        
        if (candidates.isEmpty() || totalWeight <= 0) {
            return null;
        }
        
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
    
    private static boolean checkTerrainConditions(TerrainSamplingCache cache,
                                                  ServerLevel level,
                                                  BlockPos pos,
                                                  Vec3i size) {
        if (cache.isColumnWater(level, pos.getX(), pos.getZ())) {
            return false;
        }
        
        int centerY = cache.height(level, pos.getX(), pos.getZ());
        int halfX = size.getX() / 2;
        int halfZ = size.getZ() / 2;
        
        int maxSlope = RoadConstants.MAX_STRUCTURE_SLOPE;
        int y1 = cache.height(level, pos.getX() - halfX, pos.getZ());
        int y2 = cache.height(level, pos.getX() + halfX, pos.getZ());
        int y3 = cache.height(level, pos.getX(), pos.getZ() - halfZ);
        int y4 = cache.height(level, pos.getX(), pos.getZ() + halfZ);
        
        return Math.abs(y1 - centerY) <= maxSlope &&
               Math.abs(y2 - centerY) <= maxSlope &&
               Math.abs(y3 - centerY) <= maxSlope &&
               Math.abs(y4 - centerY) <= maxSlope;
    }
    
    private static int getOffsetForScale(StructureScale scale, ModConfig cfg) {
        return switch (scale) {
            case SMALL -> cfg.roadsideStructure().smallStructureOffset();
            case MEDIUM -> cfg.roadsideStructure().mediumStructureOffset();
            case LARGE -> cfg.roadsideStructure().largeStructureOffset();
        };
    }
    
    private static Rotation calculateRotation(double dirX, double dirZ, boolean leftSide, boolean faceRoad) {
        if (!faceRoad) {
            return Rotation.NONE;
        }
        
        double absX = Math.abs(dirX);
        double absZ = Math.abs(dirZ);
        
        if (absX > absZ) {
            if (leftSide) {
                return dirX > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE;
            } else {
                return dirX > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
            }
        } else {
            if (leftSide) {
                return dirZ > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90;
            } else {
                return dirZ > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
            }
        }
    }
}
