package net.shiroha233.roadweaver.client.forge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.shiroha233.roadweaver.RoadWeaver;
import org.lwjgl.glfw.GLFW;

/**
 * Forge 客户端按键映射注册
 * 职责：注册模组的按键绑定
 */
@Mod.EventBusSubscriber(modid = RoadWeaver.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientKeyMappings {
    public static KeyMapping OPEN_MAP;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        OPEN_MAP = new KeyMapping(
                "key.roadweaver.open_map", 
                InputConstants.Type.KEYSYM, 
                GLFW.GLFW_KEY_H, 
                "key.categories.roadweaver"
        );
        event.register(OPEN_MAP);
    }
}
