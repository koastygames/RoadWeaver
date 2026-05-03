/* 文件职责：定义普通道路搜索阶段使用的只读量化地形场接口。 */
package net.shiroha233.roadweaver.pathfinding.terrain;

import net.minecraft.core.Holder;
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
}
