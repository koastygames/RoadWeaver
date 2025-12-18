package net.shiroha233.roadweaver.persistence.forge

import net.shiroha233.roadweaver.persistence.WorldDataProvider

/**
 * Architectury @ExpectPlatform 的 NeoForge 端实现入口。
 * 提供 Common 抽象的实例。
 */
object WorldDataProviderImpl {
    private val INSTANCE: WorldDataProvider = ForgeWorldDataProvider()

    @JvmStatic
    fun getInstance(): WorldDataProvider {
        return INSTANCE
    }
}

@Suppress("MemberVisibilityCanBePrivate")
object WorldDataProviderAccessImpl {
    @JvmStatic
    fun getInstance(): WorldDataProvider = WorldDataProviderImpl.getInstance()
}
