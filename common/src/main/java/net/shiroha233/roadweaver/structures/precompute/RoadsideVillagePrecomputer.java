package net.shiroha233.roadweaver.structures.precompute;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.core.Holder;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.sub.RoadsideVillageConfig;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.structures.data.BiomeCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 路边村庄预计算器
 */
public final class RoadsideVillagePrecomputer {
    private RoadsideVillagePrecomputer() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("RoadWeaver/RoadsideVillagePrecomputer");
    private static final String MOD_ID = "roadweaver";
    private static final ResourceLocation STRUCTURE_ID = new ResourceLocation(MOD_ID, "roadside_village");

    public static int precomputeVillages(ServerLevel level,
                                         List<RoadSegmentPlacement> segments,
                                         List<RoadSpan> spans,
                                         int width,
                                         TerrainSamplingCache cache,
                                         RandomSource random,
                                         List<Integer> targetY) {
        if (segments == null || targetY == null || segments.size() != targetY.size()) {
            return 0;
        }

        ModConfig cfg = ConfigService.get();
        String dimId = level.dimension().location().toString();
        if (!cfg.roadsideStructuresEnabledForDimension(dimId)) {
            return 0;
        }

        RoadsideVillageConfig villageCfg = cfg.roadsideVillage();
        if (!villageCfg.enabled() || villageCfg.maxVillagesPerRoad() <= 0) {
            return 0;
        }

        if (segments.size() < villageCfg.minRoadSegments()) {
            return 0;
        }

        Set<Integer> bridgeIndices = collectBridgeIndices(segments, spans);
        WindowCandidate best = chooseBestWindow(segments, targetY, bridgeIndices, villageCfg);
        if (best == null) {
            return 0;
        }

        BlockPos center = segments.get(best.centerIndex()).middlePos();
        Holder<Biome> biome = cache.getBiome(level, center.getX(), center.getZ());
        ResourceLocation style = styleForBiome(BiomeCategory.fromBiome(biome));
        int nodeCount = randomNodeCount(villageCfg, random);
        List<PendingRoadsideVillageSlot> slots = createSlots(level, segments, targetY, width, cache, villageCfg, best, nodeCount, style, random);
        if (slots.isEmpty()) {
            return 0;
        }

        BoundingBox bounds = estimateBounds(slots, villageCfg.maxDistanceFromCenter());
        ChunkPos originChunk = new ChunkPos(center);
        ResourceLocation placementId = new ResourceLocation(
            MOD_ID,
            "roadside_village/" + level.dimension().location().getPath() + "/" + originChunk.x + "_" + originChunk.z + "_" + best.startIndex()
        );

        PendingRoadsideVillage village = new PendingRoadsideVillage(
            placementId,
            STRUCTURE_ID,
            originChunk,
            best.startIndex(),
            best.endIndex(),
            style,
            random.nextLong(),
            bounds,
            List.copyOf(slots)
        );

        if (PendingRoadsideVillageStorage.addPendingVillage(level, village)) {
            LOGGER.debug("Precomputed roadside village {} style={} slots={} window=[{}, {}] chunk=[{}, {}]",
                placementId, style, slots.size(), best.startIndex(), best.endIndex(), originChunk.x, originChunk.z);
            return 1;
        }

        return 0;
    }

    private static Set<Integer> collectBridgeIndices(List<RoadSegmentPlacement> segments, List<RoadSpan> spans) {
        Set<Integer> result = new HashSet<>();
        if (spans == null || spans.isEmpty()) {
            return result;
        }

        for (RoadSpan span : spans) {
            if (span.type() != SpanType.BRIDGE) {
                continue;
            }
            for (int i = 0; i < segments.size(); i++) {
                if (isInSpan(segments.get(i).middlePos(), span)) {
                    result.add(i);
                }
            }
        }
        return result;
    }

    private static boolean isInSpan(BlockPos pos, RoadSpan span) {
        int minX = Math.min(span.start().getX(), span.end().getX());
        int maxX = Math.max(span.start().getX(), span.end().getX());
        int minZ = Math.min(span.start().getZ(), span.end().getZ());
        int maxZ = Math.max(span.start().getZ(), span.end().getZ());
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static WindowCandidate chooseBestWindow(List<RoadSegmentPlacement> segments,
                                                    List<Integer> targetY,
                                                    Set<Integer> bridgeIndices,
                                                    RoadsideVillageConfig cfg) {
        int window = Math.min(cfg.windowSegments(), segments.size());
        if (window < 3) {
            return null;
        }

        WindowCandidate best = null;
        for (int start = 0; start + window < segments.size(); start += Math.max(1, window / 4)) {
            int end = start + window - 1;
            WindowCandidate candidate = evaluateWindow(segments, targetY, bridgeIndices, cfg, start, end);
            if (candidate != null && (best == null || candidate.score() > best.score())) {
                best = candidate;
            }
        }
        return best;
    }

    private static WindowCandidate evaluateWindow(List<RoadSegmentPlacement> segments,
                                                  List<Integer> targetY,
                                                  Set<Integer> bridgeIndices,
                                                  RoadsideVillageConfig cfg,
                                                  int start,
                                                  int end) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = start; i <= end; i++) {
            if (bridgeIndices.contains(i)) {
                return null;
            }
            int y = targetY.get(i);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        if (maxY - minY > cfg.maxHeightDiff()) {
            return null;
        }

        double curve = curveAngle(segments.get(start).middlePos(), segments.get((start + end) / 2).middlePos(), segments.get(end).middlePos());
        if (curve < cfg.minCurveAngle() || curve > cfg.maxCurveAngle()) {
            return null;
        }

        double flatnessScore = 1.0 / (1.0 + (maxY - minY));
        double curveScore = 1.0 / (1.0 + Math.abs(curve - ((cfg.minCurveAngle() + cfg.maxCurveAngle()) * 0.5)));
        return new WindowCandidate(start, end, (start + end) / 2, flatnessScore + curveScore);
    }

    private static double curveAngle(BlockPos start, BlockPos center, BlockPos end) {
        double ax = center.getX() - start.getX();
        double az = center.getZ() - start.getZ();
        double bx = end.getX() - center.getX();
        double bz = end.getZ() - center.getZ();
        double al = Math.sqrt(ax * ax + az * az);
        double bl = Math.sqrt(bx * bx + bz * bz);
        if (al < 0.01 || bl < 0.01) {
            return 0.0;
        }
        double dot = (ax * bx + az * bz) / (al * bl);
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(Math.acos(dot));
    }

    private static int randomNodeCount(RoadsideVillageConfig cfg, RandomSource random) {
        int min = cfg.targetNodeCountMin();
        int max = Math.max(min, cfg.targetNodeCountMax());
        return min + random.nextInt(max - min + 1);
    }

    private static List<PendingRoadsideVillageSlot> createSlots(ServerLevel level,
                                                                List<RoadSegmentPlacement> segments,
                                                                List<Integer> targetY,
                                                                int width,
                                                                TerrainSamplingCache cache,
                                                                RoadsideVillageConfig cfg,
                                                                WindowCandidate window,
                                                                int nodeCount,
                                                                ResourceLocation style,
                                                                RandomSource random) {
        List<PendingRoadsideVillageSlot> result = new ArrayList<>();
        int stride = Math.max(1, (window.endIndex() - window.startIndex() + 1) / Math.max(1, nodeCount));
        int offset = Math.max(width / 2 + cfg.roadBufferBlocks(), cfg.roadBufferBlocks());

        for (int n = 0; n < nodeCount; n++) {
            int index = Math.min(window.endIndex(), window.startIndex() + n * stride + stride / 2);
            PendingRoadsideVillageSlot.Side side = ((n + random.nextInt(2)) & 1) == 0
                ? PendingRoadsideVillageSlot.Side.LEFT
                : PendingRoadsideVillageSlot.Side.RIGHT;
            PendingRoadsideVillageSlot.SlotKind kind = slotKind(n, nodeCount, style, random);
            PendingRoadsideVillageSlot slot = createSlot(level, segments, targetY, cache, cfg, index, side, kind, offset);
            if (slot != null) {
                result.add(slot);
            }
        }

        return result;
    }

    private static PendingRoadsideVillageSlot.SlotKind slotKind(int ordinal, int total, ResourceLocation style, RandomSource random) {
        if (style.getPath().equals("desert") && ordinal == total - 1 && random.nextBoolean()) {
            return PendingRoadsideVillageSlot.SlotKind.CAMEL;
        }
        if (ordinal % 7 == 5) {
            return PendingRoadsideVillageSlot.SlotKind.VILLAGER;
        }
        if (ordinal % 5 == 3) {
            return PendingRoadsideVillageSlot.SlotKind.DECOR;
        }
        if (ordinal % 6 == 4) {
            return PendingRoadsideVillageSlot.SlotKind.ANIMAL;
        }
        return PendingRoadsideVillageSlot.SlotKind.HOUSE;
    }

    private static PendingRoadsideVillageSlot createSlot(ServerLevel level,
                                                         List<RoadSegmentPlacement> segments,
                                                         List<Integer> targetY,
                                                         TerrainSamplingCache cache,
                                                         RoadsideVillageConfig cfg,
                                                         int index,
                                                         PendingRoadsideVillageSlot.Side side,
                                                         PendingRoadsideVillageSlot.SlotKind kind,
                                                         int offset) {
        BlockPos middle = segments.get(index).middlePos();
        BlockPos prev = segments.get(Math.max(0, index - 8)).middlePos();
        BlockPos next = segments.get(Math.min(segments.size() - 1, index + 8)).middlePos();

        double dirX = next.getX() - prev.getX();
        double dirZ = next.getZ() - prev.getZ();
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 0.01) {
            return null;
        }
        dirX /= len;
        dirZ /= len;

        double perpX = side == PendingRoadsideVillageSlot.Side.LEFT ? -dirZ : dirZ;
        double perpZ = side == PendingRoadsideVillageSlot.Side.LEFT ? dirX : -dirX;
        int x = middle.getX() + (int) Math.round(perpX * offset);
        int z = middle.getZ() + (int) Math.round(perpZ * offset);
        int y = targetY.get(index);

        if (!isFootprintUsable(level, cache, cfg, x, z, y)) {
            return null;
        }

        Direction outward = directionFromVector(perpX, perpZ);
        int radius = switch (kind) {
            case HOUSE -> 12;
            case DECOR, VILLAGER, ANIMAL, CAMEL -> 6;
        };
        return new PendingRoadsideVillageSlot(index, side, new BlockPos(x, y, z), outward, kind, radius);
    }

    private static boolean isFootprintUsable(ServerLevel level, TerrainSamplingCache cache, RoadsideVillageConfig cfg, int x, int z, int centerY) {
        if (cache.isColumnWater(level, x, z)) {
            return false;
        }

        int r = Math.max(2, cfg.roadBufferBlocks() / 2);
        int h1 = cache.height(level, x - r, z);
        int h2 = cache.height(level, x + r, z);
        int h3 = cache.height(level, x, z - r);
        int h4 = cache.height(level, x, z + r);
        return Math.abs(h1 - centerY) <= cfg.maxLocalSlope()
            && Math.abs(h2 - centerY) <= cfg.maxLocalSlope()
            && Math.abs(h3 - centerY) <= cfg.maxLocalSlope()
            && Math.abs(h4 - centerY) <= cfg.maxLocalSlope();
    }

    private static Direction directionFromVector(double x, double z) {
        if (Math.abs(x) > Math.abs(z)) {
            return x >= 0 ? Direction.EAST : Direction.WEST;
        }
        return z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static BoundingBox estimateBounds(List<PendingRoadsideVillageSlot> slots, int fallbackRadius) {
        if (slots.isEmpty()) {
            return new BoundingBox(0, 0, 0, 0, 0, 0);
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (PendingRoadsideVillageSlot slot : slots) {
            int r = Math.max(slot.footprintRadius(), fallbackRadius / 4);
            BlockPos p = slot.anchor();
            minX = Math.min(minX, p.getX() - r);
            minY = Math.min(minY, p.getY() - 4);
            minZ = Math.min(minZ, p.getZ() - r);
            maxX = Math.max(maxX, p.getX() + r);
            maxY = Math.max(maxY, p.getY() + 24);
            maxZ = Math.max(maxZ, p.getZ() + r);
        }

        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static ResourceLocation styleForBiome(BiomeCategory category) {
        return switch (category) {
            case DESERT, BADLANDS -> new ResourceLocation("minecraft", "desert");
            case SAVANNA -> new ResourceLocation("minecraft", "savanna");
            case TAIGA -> new ResourceLocation("minecraft", "taiga");
            case SNOWY -> new ResourceLocation("minecraft", "snowy");
            default -> new ResourceLocation("minecraft", "plains");
        };
    }

    private record WindowCandidate(int startIndex, int endIndex, int centerIndex, double score) {}
}