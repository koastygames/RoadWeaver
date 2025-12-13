package net.shiroha233.roadweaver.features.decoration.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.shiroha233.roadweaver.features.decoration.base.Decoration;
import net.shiroha233.roadweaver.features.decoration.types.SeaQuestionSignDecoration;
import net.shiroha233.roadweaver.features.roadlogic.core.StructureAvoidanceService;

import java.util.Set;

/**
 * 当桥梁因为“超长”被跳过时，在水域跨度两端（岸边）放置提示路牌。
 * 这里只负责“是否需要放 + 计算侧向偏移 + 投放 Decoration”，不直接写方块。
 */
public final class SkippedBridgeBankSignPlanner {
    private SkippedBridgeBankSignPlanner() {}

    private static final int SIDE_OFFSET = 2;

    public static void addIfSkippedBridgeBank(WorldGenLevel world,
                                             Set<Decoration> out,
                                             BlockPos placePos,
                                             BlockPos nextPos,
                                             BlockPos prevPos,
                                             int roadWidth,
                                             boolean[] skipSegments,
                                             int i) {
        if (skipSegments == null || i < 0 || i >= skipSegments.length) return;
        if (skipSegments[i]) return;

        // i 处于“正常路段”，但紧挨着一段 skipSegments=true 的水域跨度
        // beforeSkip: 即将进入被跳过的水域
        // afterSkip : 刚从被跳过的水域出来
        boolean beforeSkip = (i + 1 < skipSegments.length) && skipSegments[i + 1];
        boolean afterSkip = (i - 1 >= 0) && skipSegments[i - 1];
        if (!beforeSkip && !afterSkip) return;

        int dx = nextPos.getX() - prevPos.getX();
        int dz = nextPos.getZ() - prevPos.getZ();
        double len = Math.sqrt((double) dx * dx + (double) dz * dz);
        int nx = len != 0 ? (int) Math.round(dx / len) : 0;
        int nz = len != 0 ? (int) Math.round(dz / len) : 0;
        Vec3i dir = new Vec3i(nx, 0, nz);
        Vec3i ortho = new Vec3i(-dir.getZ(), 0, dir.getX());

        int halfWidth = Math.max(1, roadWidth / 2);
        int sideOffset = Math.max(SIDE_OFFSET, halfWidth + 1);

        // 复用原有 start/end 的逻辑：
        // - afterSkip 代表“跨海后重新开始铺路”的一侧，当作 start
        // - beforeSkip 代表“跨海前道路结束”的一侧，当作 end
        boolean isStart = afterSkip;
        BlockPos shifted = isStart
                ? placePos.offset(ortho.getX() * sideOffset, 0, ortho.getZ() * sideOffset)
                : placePos.offset(-ortho.getX() * sideOffset, 0, -ortho.getZ() * sideOffset);

        if (StructureAvoidanceService.shouldAvoid(world, shifted)) return;
        out.add(new SeaQuestionSignDecoration(shifted, ortho, world, isStart));
    }
}
