package net.shiroha233.roadweaver.client.neoforge

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.shiroha233.roadweaver.RoadWeaver
import net.shiroha233.roadweaver.client.map.RoadMapScreen
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache
import org.lwjgl.glfw.GLFW

@EventBusSubscriber(modid = RoadWeaver.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object ClientKeyMappings {
    @JvmField
    var OPEN_MAP: KeyMapping? = null

    @SubscribeEvent
    @JvmStatic
    fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
        OPEN_MAP = KeyMapping("key.roadweaver.open_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.roadweaver")
        event.register(OPEN_MAP)
    }

    @EventBusSubscriber(modid = RoadWeaver.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.GAME)
    object NeoForgeBusHandlers {
        @SubscribeEvent
        @JvmStatic
        fun onClientTick(event: ClientTickEvent.Post) {
            val mc = Minecraft.getInstance()
            if (mc.player == null) return
            val key = OPEN_MAP ?: return
            if (mc.screen is RoadMapScreen) return
            while (key.consumeClick()) {
                mc.setScreen(RoadMapScreen())
            }
        }

        @SubscribeEvent
        @JvmStatic
        fun onPlayerLoggedIn(event: ClientPlayerNetworkEvent.LoggingIn) {
            MapSnapshotCache.clearNow()
            ClientMapNotes.onWorldJoin()
        }

        @SubscribeEvent
        @JvmStatic
        fun onPlayerLoggedOut(event: ClientPlayerNetworkEvent.LoggingOut) {
            MapSnapshotCache.clearNow()
            ClientMapNotes.onWorldLeave()
        }
    }
}
