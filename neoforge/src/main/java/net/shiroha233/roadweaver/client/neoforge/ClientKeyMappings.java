package net.shiroha233.roadweaver.client.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;
import org.lwjgl.glfw.GLFW;

public class ClientKeyMappings {
    public static KeyMapping OPEN_MAP;
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(RoadWeaver.MOD_ID, "roadweaver"));

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientKeyMappings::onRegisterKeyMappings);

        NeoForge.EVENT_BUS.addListener(ForgeBusHandlers::onClientTick);
        NeoForge.EVENT_BUS.addListener(ForgeBusHandlers::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ForgeBusHandlers::onPlayerLoggedOut);
    }

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        OPEN_MAP = new KeyMapping("key.roadweaver.open_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
        event.register(OPEN_MAP);
    }

    public static class ForgeBusHandlers {
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (OPEN_MAP == null) return;
            if (mc.screen instanceof RoadMapScreen) return;
            while (OPEN_MAP.consumeClick()) {
                mc.setScreen(new RoadMapScreen());
            }
        }

        public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
            MapSnapshotCache.clearNow();
            ClientMapNotes.onWorldJoin();
        }

        public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
            MapSnapshotCache.clearNow();
            ClientMapNotes.onWorldLeave();
        }
    }
}
