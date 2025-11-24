package net.shiroha233.roadweaver.structures.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 使用噪声生成圆形碗状地形托盘，让结构自然融入原版地形。
 * 
 * 设计理念：
 * - 圆形：使用欧几里得距离而非切比雪夫距离
 * - 碗状：中心高度最高，边缘平滑衰减到原地形
 * - 噪声：叠加柏林噪声变体，避免完美圆形的人工感
 * - 地形重塑：清理原表层，填充/削减到目标高度，避免空隙
 */
public final class NoiseTerracePlacer {
    private NoiseTerracePlacer() {}

    /**
     * 在结构锚点周围生成圆形碗状托盘
     * 
     * @param level 世界
     * @param centerX 中心X坐标（结构footprint中心）
     * @param centerZ 中心Z坐标（结构footprint中心）
     * @param targetY 目标高度（结构底部期望的Y坐标）
     * @param innerRadius 内环半径（完全平坦区域）
     * @param outerRadius 外环半径（过渡区域的外边界）
     * @param random 随机源（用于噪声种子）
     */
    public static void buildNoisyTerrace(ServerLevel level, int centerX, int centerZ, int targetY,
                                         int innerRadius, int outerRadius, RandomSource random) {
        // 生成简单的2D噪声偏移（模拟自然地形起伏）
        long noiseSeed = random.nextLong();
        double noiseScale = 0.15; // 噪声频率
        double noiseAmplitude = 1.5; // 噪声强度（方块单位）

        // 遍历圆形区域
        int searchRadius = outerRadius + 2; // 稍微扩大搜索范围
        for (int x = centerX - searchRadius; x <= centerX + searchRadius; x++) {
            for (int z = centerZ - searchRadius; z <= centerZ + searchRadius; z++) {
                // 计算到中心的欧几里得距离
                double dx = x - centerX;
                double dz = z - centerZ;
                double dist = Math.sqrt(dx * dx + dz * dz);

                // 超出外环，跳过
                if (dist > outerRadius) continue;

                // 计算当前位置的原始地表高度
                int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

                // 计算期望高度（基于距离的平滑插值）
                double desiredY;
                if (dist <= innerRadius) {
                    // 内环：完全平坦，对齐目标高度
                    desiredY = targetY;
                } else {
                    // 外环：从 targetY 平滑过渡到 groundY
                    double t = (dist - innerRadius) / (outerRadius - innerRadius); // 0..1
                    // 使用平滑步函数（Smoothstep）让过渡更自然
                    double smoothT = smoothstep(t);
                    desiredY = targetY * (1.0 - smoothT) + groundY * smoothT;
                }

                // 叠加噪声（让边缘更自然，避免完美圆形）
                double noise = simplexNoise2D(x * noiseScale, z * noiseScale, noiseSeed);
                desiredY += noise * noiseAmplitude * Math.max(0, 1.0 - dist / outerRadius); // 边缘噪声减弱

                int finalY = (int) Math.round(desiredY);

                // 改变地形：清理原表层，从下往上填充到目标高度
                if (finalY != groundY) {
                    reshapeColumn(level, x, z, groundY, finalY);
                }
            }
        }
    }

    /**
     * 重塑一列地形（清理原表层，填充到目标高度）
     * 
     * @param level 世界
     * @param x X坐标
     * @param z Z坐标
     * @param groundY 原地表高度
     * @param targetY 目标高度
     */
    private static void reshapeColumn(ServerLevel level, int x, int z, int groundY, int targetY) {
        if (targetY > groundY) {
            // 需要抬升：先清理原表层的草/植物，再从下往上填充
            // 清理原地表上方的植物/草（避免悬空草）
            for (int y = groundY; y <= groundY + 3; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                // 如果是植物/草/花等非固体方块，清空
                if (!state.isAir() && !state.isSolidRender(level, pos)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            
            // 从原地表+1开始填充到目标高度
            for (int y = groundY + 1; y <= targetY; y++) {
                BlockState state = (y == targetY) ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                level.setBlock(new BlockPos(x, y, z), state, 3);
            }
        } else if (targetY < groundY) {
            // 需要削减：从目标高度+1开始清空到原地表
            for (int y = targetY + 1; y <= groundY; y++) {
                level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
            // 在目标高度放置草方块作为新表层
            level.setBlock(new BlockPos(x, targetY, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        }
    }

    /**
     * Smoothstep 插值函数：3t² - 2t³
     * 让过渡曲线在起点和终点的斜率为0，中间平滑
     */
    private static double smoothstep(double t) {
        t = Math.max(0.0, Math.min(1.0, t)); // clamp [0,1]
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * 简化版 2D Simplex 噪声（基于梯度噪声原理）
     * 
     * 这里使用一个快速但质量足够的近似实现，避免引入外部库。
     * 返回值范围约 [-1, 1]。
     */
    private static double simplexNoise2D(double x, double z, long seed) {
        // 使用哈希函数生成伪随机梯度
        int ix = fastFloor(x);
        int iz = fastFloor(z);
        double fx = x - ix;
        double fz = z - iz;

        // 四个角的梯度
        double g00 = gradDot(hash2D(ix, iz, seed), fx, fz);
        double g10 = gradDot(hash2D(ix + 1, iz, seed), fx - 1.0, fz);
        double g01 = gradDot(hash2D(ix, iz + 1, seed), fx, fz - 1.0);
        double g11 = gradDot(hash2D(ix + 1, iz + 1, seed), fx - 1.0, fz - 1.0);

        // 双线性插值（用 smoothstep 让过渡更平滑）
        double u = smoothstep(fx);
        double v = smoothstep(fz);

        double nx0 = lerp(g00, g10, u);
        double nx1 = lerp(g01, g11, u);
        return lerp(nx0, nx1, v);
    }

    private static int fastFloor(double x) {
        return x >= 0 ? (int) x : (int) x - 1;
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    /**
     * 2D 哈希函数（生成伪随机整数）
     */
    private static int hash2D(int x, int z, long seed) {
        long h = seed;
        h ^= x * 374761393L;
        h ^= z * 668265263L;
        h = (h ^ (h >> 13)) * 1274126177L;
        return (int) (h ^ (h >> 16));
    }

    /**
     * 梯度点积（用哈希值选择一个随机单位向量）
     */
    private static double gradDot(int hash, double x, double z) {
        // 用哈希的低2位选择4个方向之一：(1,0), (-1,0), (0,1), (0,-1)
        switch (hash & 3) {
            case 0: return x;       // (1, 0)
            case 1: return -x;      // (-1, 0)
            case 2: return z;       // (0, 1)
            default: return -z;     // (0, -1)
        }
    }
}
