/* 文件职责：验证规划路径共享精确区域的租约与最终释放。 */
package net.shiroha233.roadweaver.pathfinding.terrain.region;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannedPathCacheTest {
    @AfterEach
    void clearCache() {
        PlannedPathCache.clearAll();
    }

    @Test
    void regionIsReleasedAfterLastRoadLeaseCloses() {
        AccurateTerrainRegion region = region();
        PlannedPathCache.register(region, Map.of(
                11L, List.of(new BlockPos(0, 70, 0), new BlockPos(8, 71, 0)),
                22L, List.of(new BlockPos(0, 70, 8), new BlockPos(8, 71, 8))));

        PlannedPathCache.Lease first = PlannedPathCache.take(11L);
        PlannedPathCache.Lease second = PlannedPathCache.take(22L);
        assertNotNull(first);
        assertNotNull(second);
        assertFalse(region.isDisposed());

        first.close();
        assertFalse(region.isDisposed());
        second.close();
        assertTrue(region.isDisposed());
        assertNull(PlannedPathCache.take(11L));
    }

    @Test
    void clearingCachedPathsReleasesTheirRegion() {
        AccurateTerrainRegion region = region();
        PlannedPathCache.register(region, Map.of(
                33L, List.of(new BlockPos(0, 70, 0), new BlockPos(8, 71, 0))));

        PlannedPathCache.clearAll();

        assertTrue(region.isDisposed());
    }

    @Test
    void discardingDisabledRoadReleasesItsLease() {
        AccurateTerrainRegion region = region();
        PlannedPathCache.register(region, Map.of(
                44L, List.of(new BlockPos(0, 70, 0), new BlockPos(8, 71, 0))));

        PlannedPathCache.discard(44L);

        assertTrue(region.isDisposed());
        assertNull(PlannedPathCache.take(44L));
    }

    @SuppressWarnings("unchecked")
    private static AccurateTerrainRegion region() {
        AccurateRegionBounds bounds = AccurateRegionBounds.aligned(0, 0, 8, 8, 8);
        int size = Math.toIntExact(bounds.sampleCount());
        return new AccurateTerrainRegion(
                bounds,
                63,
                new short[size],
                new short[size],
                new byte[size],
                (Holder<Biome>[]) new Holder<?>[size]);
    }
}
