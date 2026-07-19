/* 文件职责：验证自适应精采走廊的高差分档边界。 */
package net.shiroha233.roadweaver.planning.terrain;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoarseCorridorPlanningAdapterTest {
    @Test
    void reliefThresholdsSelectExpectedChunkRadius() {
        assertEquals(1, CoarseCorridorPlanningAdapter.radiusForRelief(0));
        assertEquals(1, CoarseCorridorPlanningAdapter.radiusForRelief(8));
        assertEquals(2, CoarseCorridorPlanningAdapter.radiusForRelief(9));
        assertEquals(2, CoarseCorridorPlanningAdapter.radiusForRelief(24));
        assertEquals(4, CoarseCorridorPlanningAdapter.radiusForRelief(25));
    }

    @Test
    void successfulPathRetriesOnlyWhenFinalPathTouchesBoundary() {
        Set<Long> boundary = Set.of(11L);
        Set<Long> exploratoryRejections = Set.of(21L, 22L, 23L);

        Set<Long> contacts = CoarseCorridorPlanningAdapter.retryContacts(
                List.of(new BlockPos(0, 64, 0)), boundary, exploratoryRejections);

        assertEquals(boundary, contacts);
    }

    @Test
    void failedPathUsesRejectedBoundaryAndSkipsUnguidedExpansion() {
        Set<Long> rejected = Set.of(21L, 22L);

        assertEquals(rejected, CoarseCorridorPlanningAdapter.retryContacts(
                List.of(), Set.of(), rejected));
        assertEquals(Set.of(), CoarseCorridorPlanningAdapter.retryContacts(
                null, Set.of(), Set.of()));
    }
}
