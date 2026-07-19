/* 文件职责：验证规划配置中的地形采样模式默认值与快照行为。 */
package net.shiroha233.roadweaver.config.sub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class PlanningConfigTest {

    @Test
    void defaultsToLegacyDirectMode() {
        PlanningConfig config = new PlanningConfig();

        assertEquals(TerrainSamplingMode.LEGACY_DIRECT, config.terrainSamplingMode());
    }

    @Test
    void sanitizeAndSnapshotPreserveSamplingMode() {
        PlanningConfig config = new PlanningConfig();
        config.setTerrainSamplingMode(null);

        config.sanitize();
        assertEquals(TerrainSamplingMode.LEGACY_DIRECT, config.terrainSamplingMode());

        config.setTerrainSamplingMode(TerrainSamplingMode.FULL_REGION);
        PlanningConfig snapshot = config.snapshot();

        assertNotSame(config, snapshot);
        assertEquals(TerrainSamplingMode.FULL_REGION, snapshot.terrainSamplingMode());
    }
}
