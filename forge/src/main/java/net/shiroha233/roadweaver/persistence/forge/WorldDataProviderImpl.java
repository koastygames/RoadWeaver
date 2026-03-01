package net.shiroha233.roadweaver.persistence.forge;

import net.shiroha233.roadweaver.persistence.WorldDataProvider;

/**
 * Architectury @ExpectPlatform 实现类（Forge）
 */
public final class WorldDataProviderImpl {
    private static final WorldDataProvider INSTANCE = new ForgeWorldDataProvider();

    public static WorldDataProvider getInstance() {
        return INSTANCE;
    }
}
