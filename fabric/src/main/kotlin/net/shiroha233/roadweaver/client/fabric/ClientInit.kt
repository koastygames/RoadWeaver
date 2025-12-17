package net.shiroha233.roadweaver.client.fabric

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.KeyMapping
import net.shiroha233.roadweaver.client.map.RoadMapScreen
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache
import net.shiroha233.roadweaver.network.fabric.MapNetworkFabric
import org.lwjgl.glfw.GLFW

class ClientInit : ClientModInitializer {
    override fun onInitializeClient() {
        MapNetworkFabric.registerClientReceivers()

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            MapSnapshotCache.clearNow()
            ClientMapNotes.onWorldJoin()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            MapSnapshotCache.clearNow()
            ClientMapNotes.onWorldLeave()
        }

        OPEN_MAP = KeyBindingHelper.registerKeyBinding(
            KeyMapping(
                "key.roadweaver.open_map",
                GLFW.GLFW_KEY_H,
                "key.categories.roadweaver"
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player == null) return@register
            if (client.screen is RoadMapScreen) return@register
            while (OPEN_MAP.consumeClick()) {
                client.setScreen(RoadMapScreen())
            }
        }
    }

    companion object {
        @JvmField
        var OPEN_MAP: KeyMapping = KeyMapping(
            "key.roadweaver.open_map",
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.roadweaver"
        )
    }
}
