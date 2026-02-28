package net.shiroha233.roadweaver.persistence.fabric;

import net.shiroha233.roadweaver.persistence.WorldDataProvider;

/**
 * Architectury @ExpectPlatform 实现类（Fabric）
 * 职责：提供 Fabric 平台的 WorldDataProvider 单例
 */
public final class WorldDataProviderImpl {
    private static final WorldDataProvider INSTANCE = new FabricWorldDataProvider();

    public static WorldDataProvider getInstance() {
        return INSTANCE;
    }
}
