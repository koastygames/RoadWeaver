/* 文件职责：定义规划阶段可选的地形采样模式。 */
package net.shiroha233.roadweaver.config.sub;

/**
 * 规划阶段的地形采样策略。
 */
public enum TerrainSamplingMode {
    /**
     * 旧版模式：不构建规划区域地形，生成道路时按单条连接直接寻路。
     */
    LEGACY_DIRECT,

    /**
     * 先使用粗区域地形，再只对走廊与路径附近做精确补采。
     */
    COARSE_CORRIDOR,

    /**
     * 先为整个规划区域构建精确地形场；若会话内判定不可用，可降级为 COARSE_CORRIDOR。
     */
    FULL_REGION
}
