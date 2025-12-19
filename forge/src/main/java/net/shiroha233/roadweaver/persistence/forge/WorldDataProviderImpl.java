package net.shiroha233.roadweaver.persistence.forge;

import net.shiroha233.roadweaver.persistence.WorldDataProvider;

/**
 * Architectury @ExpectPlatform 的 Forge 端实现入口。
 * 提供 Common 抽象的实例。
 */
public final class WorldDataProviderImpl {
    private static final WorldDataProvider INSTANCE = new ForgeWorldDataProvider();

    public static WorldDataProvider getInstance() {
        return INSTANCE;
    }
}