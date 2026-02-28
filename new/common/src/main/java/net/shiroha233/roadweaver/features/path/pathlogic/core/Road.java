package net.shiroha233.roadweaver.features.path.pathlogic.core;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.PresetService;
import net.shiroha233.roadweaver.config.sub.RoadGenerationConfig;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.core.model.RoadSpan;
import net.shiroha233.roadweaver.core.model.SpanType;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import net.shiroha233.roadweaver.features.path.config.PathFeatureConfig;
import net.shiroha233.roadweaver.features.path.pathlogic.pathfinding.RoadPathCalculator;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.structures.precompute.RoadsideStructurePrecomputer;

import java.util.*;

/**
 * 道路生成器
 * 
 * 职责：根据结构连接生成一条道路，包括寻路、路面生成、路边结构预计算
 */
public final class Road {
    private final ServerLevel level;
    private final StructureConnection connection;
    private final PathFeatureConfig featureConfig;
    private final RoadGenerationConfig genConfig;

    public Road(ServerLevel level, StructureConnection connection, 
                PathFeatureConfig featureConfig, RoadGenerationConfig genConfig) {
        this.level = level;
        this.connection = connection;
        this.featureConfig = featureConfig;
        this.genConfig = genConfig;
    }
    
    @Deprecated
    public Road(ServerLevel level, StructureConnection connection, PathFeatureConfig config) {
        this(level, connection, config, RoadGenerationConfig.from(ConfigService.get()));
    }

    public void generateRoad(int maxSteps) {
        RandomSource random = RandomSource.create();
        int width = genConfig.effectiveRoadWidth(getRandomWidth(random, featureConfig));
        boolean allowA = genConfig.allowArtificial();
        boolean allowN = genConfig.allowNatural();
        if (!allowA && !allowN) return;
        
        int type = allowA && allowN ? (random.nextBoolean() ? 0 : 1) : (allowA ? 0 : 1);
        List<BlockState> materials;
        List<BlockState> slabMaterials;
        PresetService.RoadType presetType = (type == 0) ? PresetService.RoadType.ARTIFICIAL : PresetService.RoadType.NATURAL;
        ResourceLocation dimId = level.dimension().location();

        if (presetType == PresetService.RoadType.ARTIFICIAL) {
            PresetService.PresetDef preset = PresetService.choosePreset(random, dimId, presetType);
            materials = PresetService.toBlockStatesFromIds(preset.materials());
            slabMaterials = PresetService.toBlockStatesFromIds(preset.slabMaterials());
        } else {
            materials = List.of();
            slabMaterials = List.of();
        }

        BlockPos rawStart = connection.from();
        BlockPos rawEnd = connection.to();
        
        TerrainSamplingCache cache = new TerrainSamplingCache();
        try {
            List<RoadSegmentPlacement> rawSegments = RoadPathCalculator.calculateAStarRoadPath(
                    rawStart, rawEnd, width, level, maxSteps, cache, genConfig);
            if (rawSegments == null || rawSegments.size() < 5) return;
            
            List<RoadSegmentPlacement> segments = StructureRoadOffsetService.trimPathNearStructure(
                    level, rawSegments, rawStart, rawEnd);
            if (segments == null || segments.size() < 5) return;
            
            List<RoadSpan> spans = RoadPathCalculator.extractSpans(segments, level, cache, genConfig.pathfinding());
            List<Integer> targetY = computeTargetY(level, segments, spans, cache, genConfig);

            RoadData rd = new RoadData(width, type, materials, slabMaterials, segments, spans, targetY);
            RoadShardStorage.addRoad(level, rd);
            
            RoadsideStructurePrecomputer.precomputeStructures(level, segments, spans, width, cache, random, targetY);
        } finally {
            cache.clear();
        }
    }

    private static int getRandomWidth(RandomSource rnd, PathFeatureConfig cfg) {
        return 3;
    }
    
    private static List<Integer> computeTargetY(ServerLevel level, List<RoadSegmentPlacement> segments, 
                                                   List<RoadSpan> spans, TerrainSamplingCache cache,
                                                   RoadGenerationConfig cfg) {
        int n = segments.size();
        List<BlockPos> centers = new ArrayList<>(n);
        for (RoadSegmentPlacement s : segments) centers.add(s.middlePos());

        boolean[] isBridge = new boolean[n];
        if (spans != null && !spans.isEmpty()) {
            Map<Long, Integer> indexMap = new HashMap<>();
            for (int i = 0; i < centers.size(); i++) indexMap.put(centers.get(i).asLong(), i);
            for (RoadSpan sp : spans) {
                if (sp.type() != SpanType.BRIDGE) continue;
                Integer si = indexMap.get(sp.start().asLong());
                Integer ei = indexMap.get(sp.end().asLong());
                if (si == null || ei == null) continue;
                int a = Math.max(0, Math.min(si, ei));
                int b = Math.min(n - 1, Math.max(si, ei));
                for (int k = a; k <= b; k++) isBridge[k] = true;
            }
        }

        int avg = Math.max(0, cfg.averagingRadius());
        int[] base = new int[n];
        for (int i = 0; i < n; i++) {
            int sum = 0, cnt = 0;
            int lo = Math.max(0, i - avg);
            int hi = Math.min(n - 1, i + avg);
            for (int j = lo; j <= hi; j++) {
                BlockPos sp = centers.get(j);
                int yTop = sp.getY();
                sum += yTop; cnt++;
            }
            base[i] = cnt > 0 ? (int) Math.round(sum / (double) cnt) : centers.get(i).getY();
        }

        if (!cfg.slopeLimitEnabled()) {
            List<Integer> out = new ArrayList<>(n);
            for (int v : base) out.add(v);
            return out;
        }

        int[] smoothed = base.clone();
        int i = 0;
        while (i < n) {
            while (i < n && isBridge[i]) i++;
            int s = i;
            while (i < n && !isBridge[i]) i++;
            int e = i - 1;
            if (s <= e) {
                int step2 = Math.max(0, Math.min(8, cfg.maxSlopeStepPerTwoSegments()));
                int halfLow = Math.max(0, step2 / 2);
                int halfHigh = Math.max(0, (step2 + 1) / 2);
                for (int ii = s + 1; ii <= e; ii++) {
                    int y = smoothed[ii];
                    if (ii == s + 1) {
                        int py = smoothed[ii - 1];
                        if (y > py + halfLow) y = py + halfLow;
                        if (y < py - halfLow) y = py - halfLow;
                    } else {
                        int py = smoothed[ii - 1];
                        if (y > py + halfHigh) y = py + halfHigh;
                        if (y < py - halfHigh) y = py - halfHigh;
                        int p2 = smoothed[ii - 2];
                        int hi = p2 + step2;
                        int lo = p2 - step2;
                        if (y > hi) y = hi;
                        if (y < lo) y = lo;
                    }
                    smoothed[ii] = y;
                }
                for (int ii = e - 1; ii >= s; ii--) {
                    int y = smoothed[ii];
                    if (ii == e - 1) {
                        int ny = smoothed[ii + 1];
                        if (y > ny + halfLow) y = ny + halfLow;
                        if (y < ny - halfLow) y = ny - halfLow;
                    } else {
                        int ny = smoothed[ii + 1];
                        if (y > ny + halfHigh) y = ny + halfHigh;
                        if (y < ny - halfHigh) y = ny - halfHigh;
                        int n2 = smoothed[ii + 2];
                        int hi = n2 + step2;
                        int lo = n2 - step2;
                        if (y > hi) y = hi;
                        if (y < lo) y = lo;
                    }
                    smoothed[ii] = y;
                }
            }
        }

        List<Integer> out = new ArrayList<>(n);
        for (int v : smoothed) out.add(v);
        return out;
    }
}
