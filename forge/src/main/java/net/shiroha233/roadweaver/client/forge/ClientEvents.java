package net.shiroha233.roadweaver.client.forge;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;

/**
 * Forge 客户端事件处理
 */
public final class ClientEvents {
    private ClientEvents() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn e) {
        MapSnapshotCache.clearNow();
        ClientMapNotes.onWorldJoin();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        MapSnapshotCache.clearNow();
        ClientMapNotes.onWorldLeave();
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (client.screen instanceof RoadMapScreen) return;
        
        while (ClientKeyMappings.OPEN_MAP.consumeClick()) {
            client.setScreen(new RoadMapScreen());
        }
    }
}
