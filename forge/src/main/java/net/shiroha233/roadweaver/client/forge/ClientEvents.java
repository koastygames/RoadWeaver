package net.shiroha233.roadweaver.client.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.shiroha233.roadweaver.RoadWeaver;
import net.shiroha233.roadweaver.client.map.ClientMapAccessGuard;
import net.shiroha233.roadweaver.client.map.RoadMapScreen;
import net.shiroha233.roadweaver.client.map.data.ClientMapNotes;
import net.shiroha233.roadweaver.client.map.data.MapSnapshotCache;

/**
 * Forge client events.
 */
@Mod.EventBusSubscriber(modid = RoadWeaver.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn e) {
        ClientMapAccessGuard.reset();
        MapSnapshotCache.clearNow();
        ClientMapNotes.onWorldJoin();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientMapAccessGuard.reset();
        MapSnapshotCache.clearNow();
        ClientMapNotes.onWorldLeave();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ClientKeyMappings.OPEN_MAP == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (client.screen instanceof RoadMapScreen) return;

        while (ClientKeyMappings.OPEN_MAP.consumeClick()) {
            if (!ClientMapAccessGuard.canOpen(client)) {
                continue;
            }
            client.setScreen(new RoadMapScreen());
        }
    }
}
