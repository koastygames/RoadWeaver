package net.shiroha233.roadweaver.client26;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.shiroha233.roadweaver.network26.ClientNetworkNeoForge26;
import org.lwjgl.glfw.GLFW;

/** Physical-client bootstrap isolated from the dedicated-server classloader. */
public final class NeoForgeClient26 {
    private static KeyMapping openMap;

    private NeoForgeClient26() {}

    public static void register(IEventBus modBus, ModContainer container) {
        modBus.addListener(NeoForgeClient26::registerKeyMappings);
        modBus.addListener(ClientNetworkNeoForge26::registerHandlers);

        NeoForge.EVENT_BUS.addListener(NeoForgeClient26::onClientTick);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient26::onLogin);
        NeoForge.EVENT_BUS.addListener(NeoForgeClient26::onLogout);

        IConfigScreenFactory factory = (minecraft, parent) -> new RoadWeaverConfigScreen26(parent);
        container.registerExtensionPoint(IConfigScreenFactory.class, factory);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("roadweaver", "general")
        );
        openMap = new KeyMapping(
                "key.roadweaver.open_map",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                category
        );
        event.register(openMap);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (openMap == null || mc.player == null) return;
        if (mc.gui.screen() instanceof RoadMapScreen26) return;

        while (openMap.consumeClick()) {
            if (RoadMapScreen26.canOpen()) {
                mc.gui.setScreen(new RoadMapScreen26());
            }
        }
    }

    private static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        RoadMapScreen26.resetAccess();
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RoadMapScreen26.resetAccess();
    }
}