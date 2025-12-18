package net.shiroha233.roadweaver.client.forge

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.shiroha233.roadweaver.RoadWeaver
import net.shiroha233.roadweaver.client.map.RoadMapScreen
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache
import org.lwjgl.glfw.GLFW

@Mod.EventBusSubscriber(modid = RoadWeaver.MOD_ID, value = [Dist.CLIENT], bus = Mod.EventBusSubscriber.Bus.MOD)
object ClientKeyMappings {
    @JvmField
    var OPEN_MAP: KeyMapping? = null

    @SubscribeEvent
    @JvmStatic
    fun onRegisterKeyMappings(event: RegisterKeyMappingsEvent) {
        val key = KeyMapping("key.roadweaver.open_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.roadweaver")
        OPEN_MAP = key
        event.register(key)
    }

    @Mod.EventBusSubscriber(modid = RoadWeaver.MOD_ID, value = [Dist.CLIENT], bus = Mod.EventBusSubscriber.Bus.FORGE)
    object ForgeBusHandlers {
        @SubscribeEvent
        @JvmStatic
        fun onClientTick(event: TickEvent.ClientTickEvent) {
            if (event.phase != TickEvent.Phase.END) return
            val mc = Minecraft.getInstance()
            if (mc.player === null) return
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
