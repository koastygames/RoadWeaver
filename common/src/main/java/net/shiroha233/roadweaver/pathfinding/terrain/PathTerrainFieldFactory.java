/* 文件职责：创建普通道路搜索阶段所需的地形场实现。 */
package net.shiroha233.roadweaver.pathfinding.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.shiroha233.roadweaver.core.constants.RoadConstants;
import net.shiroha233.roadweaver.pathfinding.cache.AccurateHeightSampler;
import net.shiroha233.roadweaver.pathfinding.cache.TerrainSamplingCache;

import java.util.List;

/**
 * 普通道路搜索地形场工厂。
 */
public final class PathTerrainFieldFactory {
    private PathTerrainFieldFactory() {}

    public static PathTerrainField cached(ServerLevel level, TerrainSamplingCache cache, int step) {
        return new CachedTerrainField(level, cache, step);
    }

    public static PathTerrainField quantized(ServerLevel level,
                                             TerrainSamplingCache cache,
                                             List<BlockPos> coarsePath,
                                             int step,
                                             int chunkRadius) {
        AccurateHeightSampler accurate = cache.getAccurateSampler(level);
        return QuantizedChunkTerrainField.build(
                level,
                cache,
                accurate,
                coarsePath,
                Math.max(RoadConstants.ASTAR_STEP_MIN, step),
                Math.max(0, Math.min(RoadConstants.QUANTIZED_SAMPLING_CHUNK_RADIUS_MAX, chunkRadius)));
    }
}
