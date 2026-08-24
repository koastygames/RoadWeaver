package net.shiroha233.roadweaver.client26;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.shiroha233.roadweaver.network.fabric.MapNetworkFabric;
import org.lwjgl.glfw.GLFW;

/** Physical-client bootstrap for the Minecraft 26.2 Fabric port. */
public final class FabricClient26 implements ClientModInitializer {
    private static KeyMapping openMap;

    @Override
    public void onInitializeClient() {
        MapNetworkFabric.registerClientReceivers();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> RoadMapScreen26.resetAccess());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RoadMapScreen26.resetAccess());

        openMap = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.roadweaver.open_map",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("roadweaver", "general"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(FabricClient26::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        if (openMap == null || client.player == null) return;
        if (client.gui.screen() instanceof RoadMapScreen26) return;
        while (openMap.consumeClick()) {
            if (RoadMapScreen26.canOpen()) client.gui.setScreen(new RoadMapScreen26());
        }
    }
}
