package net.shiroha233.roadweaver.client.neoforge

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache

@Suppress("MemberVisibilityCanBePrivate")
object ClientEvents {
    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::onLoggingIn)
        NeoForge.EVENT_BUS.addListener(::onLoggingOut)
    }

    private fun onLoggingIn(e: ClientPlayerNetworkEvent.LoggingIn) {
        MapSnapshotCache.clearNow()
    }

    private fun onLoggingOut(e: ClientPlayerNetworkEvent.LoggingOut) {
        MapSnapshotCache.clearNow()
    }
}
