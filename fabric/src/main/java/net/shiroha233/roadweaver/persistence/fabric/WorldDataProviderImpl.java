package net.shiroha233.roadweaver.persistence.fabric;

import net.shiroha233.roadweaver.persistence.WorldDataProvider;

/**
 * Architectury @ExpectPlatform 实现类（Fabric）。
 * 位置必须为：net.shiroha233.roadweaver.persistence.fabric.WorldDataProviderImpl
 */
public final class WorldDataProviderImpl {
    private static final WorldDataProvider INSTANCE = new FabricWorldDataProvider();

    public static WorldDataProvider getInstance() {
        return INSTANCE;
    }
}
