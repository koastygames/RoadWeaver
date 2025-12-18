package net.shiroha233.roadweaver.client.forge

import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.common.MinecraftForge
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache

@Suppress("MemberVisibilityCanBePrivate")
object ClientEvents {
    @JvmStatic
    fun register() {
        MinecraftForge.EVENT_BUS.addListener(::onLoggingIn)
        MinecraftForge.EVENT_BUS.addListener(::onLoggingOut)
    }

    private fun onLoggingIn(e: ClientPlayerNetworkEvent.LoggingIn) {
        MapSnapshotCache.clearNow()
    }

    private fun onLoggingOut(e: ClientPlayerNetworkEvent.LoggingOut) {
        MapSnapshotCache.clearNow()
    }
}
