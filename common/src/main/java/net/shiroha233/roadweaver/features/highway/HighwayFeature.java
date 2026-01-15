package net.shiroha233.roadweaver.features.highway;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.features.highway.config.HighwayFeatureConfig;
import net.shiroha233.roadweaver.features.highway.placement.HighwaySegmentPaver;
import net.shiroha233.roadweaver.helpers.Records;
import net.shiroha233.roadweaver.persistence.sharded.RoadShardStorage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Highway 世界生成 Feature：仅负责放置阶段。
 * 
 * 数据来源：SQLite 中 road_type == {@link HighwayRoadTypes#HIGHWAY} 的 Roads。
 */
public final class HighwayFeature extends Feature<HighwayFeatureConfig> {
    public HighwayFeature(Codec<HighwayFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<HighwayFeatureConfig> ctx) {
        WorldGenLevel world = ctx.level();
        Level lvl = world.getLevel();
        if (!(lvl instanceof ServerLevel server))
            return false;

        ChunkPos currentChunk = new ChunkPos(ctx.origin());
        int minX = currentChunk.getMinBlockX();
        int minZ = currentChunk.getMinBlockZ();
        int maxX = currentChunk.getMaxBlockX();
        int maxZ = currentChunk.getMaxBlockZ();

        List<Records.RoadData> roadDataList = RoadShardStorage.queryRect(server, minX, minZ, maxX, maxZ);
        if (roadDataList == null || roadDataList.isEmpty())
            return false;

        RandomSource random = ctx.random();
        ModConfig cfg = ConfigService.get();

        Set<BlockPos> processedMiddle = new HashSet<>();
        boolean didPlaceAny = false;
        for (Records.RoadData data : roadDataList) {
            if (data == null || data.roadType() != HighwayRoadTypes.HIGHWAY)
                continue;
            didPlaceAny |= processRoadDataInChunk(world, currentChunk, data, processedMiddle, random, cfg);
        }
        return didPlaceAny;
    }

    private static boolean processRoadDataInChunk(WorldGenLevel world,
            ChunkPos currentChunk,
            Records.RoadData data,
            Set<BlockPos> processedMiddle,
            RandomSource random,
            ModConfig cfg) {
        List<Records.RoadSegmentPlacement> segments = data.roadSegmentList();
        if (segments == null || segments.size() < 3)
            return false;

        List<BlockPos> centers = segments.stream().map(Records.RoadSegmentPlacement::middlePos).toList();

        int[] targetYArr = buildTargetY(world, data, centers);

        boolean didAny = false;
        for (int i = 1; i < segments.size() - 1; i++) {
            Records.RoadSegmentPlacement seg = segments.get(i);
            BlockPos middle = seg.middlePos();
            if (!processedMiddle.add(middle))
                continue;

            ChunkPos middleChunk = new ChunkPos(middle);
            if (!middleChunk.equals(currentChunk))
                continue;

            // 移除路基填充逻辑：Highway 仅铺设路面方块，不再进行地形适配填充。

            // 保持路面更齐平：这里不做半砖过渡（Highway 不需要）
            HighwaySegmentPaver.paveSegment(world, seg, i, centers, targetYArr, random, cfg);
            didAny = true;
        }

        return didAny;
    }

    private static int[] buildTargetY(WorldGenLevel world, Records.RoadData data, List<BlockPos> centers) {
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
