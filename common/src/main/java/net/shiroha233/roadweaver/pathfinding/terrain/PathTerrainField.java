/* 文件职责：定义普通道路搜索阶段使用的只读量化地形场接口。 */
package net.shiroha233.roadweaver.pathfinding.terrain;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/**
 * 普通道路搜索阶段使用的只读量化地形场。
 */
public interface PathTerrainField {

    int seaLevel();

    int height(int x, int z);

    int oceanFloor(int x, int z);

    boolean isColumnWater(int x, int z);

    boolean isNearWater(int x, int z, int neighborDistance);

    Holder<Biome> biome(int x, int z);

    boolean contains(int x, int z);

    int step();

    default boolean isWaterBiome(int x, int z) {
        if (!contains(x, z)) {
            return false;
        }
        Holder<Biome> biomeHolder = biome(x, z);
        return biomeHolder != null
                && (biomeHolder.is(BiomeTags.IS_RIVER)
                || biomeHolder.is(BiomeTags.IS_OCEAN)
                || biomeHolder.is(BiomeTags.IS_DEEP_OCEAN));
    }

    default int waterDepth(int x, int z) {
        return Math.max(0, height(x, z) - oceanFloor(x, z));
    }

    default boolean isBridgeWater(int x, int z, int minWaterDepth) {
        return contains(x, z)
                && isColumnWater(x, z)
                && waterDepth(x, z) >= Math.max(1, minWaterDepth);
    }
}
