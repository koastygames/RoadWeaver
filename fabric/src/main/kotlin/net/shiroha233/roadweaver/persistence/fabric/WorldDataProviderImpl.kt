package net.shiroha233.roadweaver.persistence.fabric

import net.shiroha233.roadweaver.persistence.WorldDataProvider

/**
 * Architectury @ExpectPlatform 实现类（Fabric）。
 * 位置必须为：net.shiroha233.roadweaver.persistence.fabric.WorldDataProviderImpl
 */
object WorldDataProviderImpl {
    @JvmField
    val INSTANCE: WorldDataProvider = FabricWorldDataProvider()

    @JvmStatic
    fun getInstance(): WorldDataProvider = INSTANCE
}

object WorldDataProviderAccessImpl {
    @JvmStatic
    fun getInstance(): WorldDataProvider = WorldDataProviderImpl.getInstance()
}
