package net.shiroha233.roadweaver.structures.roadside.registry;

import net.minecraft.util.RandomSource;
import net.shiroha233.roadweaver.structures.roadside.model.BiomeCategory;
import net.shiroha233.roadweaver.structures.roadside.model.RoadsideDecorationSpec;
import net.shiroha233.roadweaver.structures.roadside.rules.RoadsidePlacementRule;

import java.util.ArrayList;
import java.util.List;

/**
 * 路边装饰选择器
 *
 * 职责：
 * - 从 RoadsideRegistry 中按上下文条件筛选候选装饰
 * - 使用权重做一次随机选择
 *
 * 目前语义保持与 RoadsideType.chooseWeightedFiltered 基本一致，
 * 只是从枚举切换为基于 RoadsideDecorationSpec 的数据驱动实现。
 */
public final class RoadsideSelector {
    private RoadsideSelector() {}

    /**
     * 根据群系和道路长度，从注册中心选择一个装饰规格
     *
     * @param random     随机源
     * @param biome      当前群系分类
     * @param roadLength 道路长度（路段数）
     * @return 选中的装饰规格；若无可用候选则返回 null
     */
    public static RoadsideDecorationSpec choose(RandomSource random,
                                                BiomeCategory biome,
                                                int roadLength) {
        List<RoadsideDecorationSpec> candidates = new ArrayList<>();
        int totalWeight = 0;

        for (RoadsideDecorationSpec spec : RoadsideRegistry.all()) {
            RoadsidePlacementRule rule = spec.placementRule();

            // 群系过滤
            if (!rule.isBiomeAllowed(biome)) {
                continue;
            }

            // 道路长度限制
            if (!rule.isRoadLongEnough(roadLength)) {
                continue;
            }

            int w = spec.weight();
            if (w <= 0) {
                continue;
            }

            candidates.add(spec);
            totalWeight += w;
        }

        if (candidates.isEmpty() || totalWeight <= 0) {
            return null;
        }

        int roll = random.nextInt(totalWeight);
        int sum = 0;
        for (RoadsideDecorationSpec spec : candidates) {
            sum += spec.weight();
            if (roll < sum) {
                return spec;
            }
        }

        // 理论上不会到达这里，兜底返回第一个
        return candidates.get(0);
    }
}
