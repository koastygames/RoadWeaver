package net.shiroha233.roadweaver.features.highway;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.core.model.RoadData;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;
import net.shiroha233.roadweaver.features.highway.placement.HighwaySegmentPaver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HighwayFeature implements Feature {
    public static final MapCodec<HighwayFeature> CODEC = MapCodec.unit(new HighwayFeature());

    @Override
    public MapCodec<HighwayFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel world, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        Level lvl = world.getLevel();
        if (!(lvl instanceof ServerLevel server)) return false;

        ChunkPos currentChunk = ChunkPos.containing(origin);
        int minX = currentChunk.getMinBlockX();
        int minZ = currentChunk.getMinBlockZ();
        int maxX = currentChunk.getMaxBlockX();
        int maxZ = currentChunk.getMaxBlockZ();

        List<RoadData> roadDataList = RoadShardStorage.queryRect(server, minX, minZ, maxX, maxZ);
        if (roadDataList == null || roadDataList.isEmpty()) return false;

        ModConfig cfg = ConfigService.get();
        Set<BlockPos> processedMiddle = new HashSet<>();
        boolean didPlaceAny = false;
        for (RoadData data : roadDataList) {
            if (data == null || data.roadType() != HighwayRoadTypes.HIGHWAY) continue;
            didPlaceAny |= processRoadDataInChunk(world, currentChunk, data, processedMiddle, random, cfg);
        }
        return didPlaceAny;
    }

    private static boolean processRoadDataInChunk(WorldGenLevel world, ChunkPos currentChunk, RoadData data,
            Set<BlockPos> processedMiddle, RandomSource random, ModConfig cfg) {
        List<RoadSegmentPlacement> segments = data.roadSegmentList();
        if (segments == null || segments.size() < 3) return false;

        List<BlockPos> centers = segments.stream().map(RoadSegmentPlacement::middlePos).toList();
        int[] targetYArr = buildTargetY(world, data, centers);

        boolean didAny = false;
        for (int i = 1; i < segments.size() - 1; i++) {
            RoadSegmentPlacement seg = segments.get(i);
            BlockPos middle = seg.middlePos();
            if (!processedMiddle.add(middle)) continue;

            ChunkPos middleChunk = ChunkPos.containing(middle);
            if (!middleChunk.equals(currentChunk)) continue;

            HighwaySegmentPaver.paveSegment(world, seg, i, centers, targetYArr, random, cfg);
            didAny = true;
        }
        return didAny;
    }

    private static int[] buildTargetY(WorldGenLevel world, RoadData data, List<BlockPos> centers) {
        if (data.targetY() != null && data.targetY().size() == centers.size()) {
            return data.targetY().stream().mapToInt(Integer::intValue).toArray();
        }

        int[] arr = new int[centers.size()];
        for (int i = 0; i < centers.size(); i++) {
            BlockPos c = centers.get(i);
            arr[i] = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        }
        return arr;
    }
}
