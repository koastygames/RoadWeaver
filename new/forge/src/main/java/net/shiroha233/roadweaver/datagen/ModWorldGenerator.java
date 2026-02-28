package net.shiroha233.roadweaver.datagen;

import net.shiroha233.roadweaver.RoadWeaver;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 数据生成器
 * 职责：处理数据生成事件（Forge 端使用 JSON 定义特性）
 */
@Mod.EventBusSubscriber(modid = RoadWeaver.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModWorldGenerator {
    
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        RoadWeaver.getLogger().info("RoadWeaver data generation - using JSON-defined features from Common module");
    }
}
