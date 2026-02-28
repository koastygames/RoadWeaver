package net.shiroha233.roadweaver.features.path.decoration.material;

import net.shiroha233.roadweaver.core.model.WoodAssets;

/**
 * 木材感知接口：装饰物可根据生物群系设置木材类型。
 */
public interface BiomeWoodAware {
    void setWoodType(WoodAssets assets);
}
