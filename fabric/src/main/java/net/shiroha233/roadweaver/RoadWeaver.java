package net.shiroha233.roadweaver;

import net.shiroha233.roadweaver.config.ConfigService;

import net.shiroha233.roadweaver.persistence.attachments.WorldDataAttachment;
import net.shiroha233.roadweaver.planning.fabric.ServerPlanningHooks;
import net.shiroha233.roadweaver.network.fabric.MapNetworkFabric;
import net.shiroha233.roadweaver.features.config.RoadFeatureRegistry;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoadWeaver implements ModInitializer {

    public static final String MOD_ID = "roadweaver";

    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing RoadWeaver (Fabric)...");
        
        // 注册 Fabric Attachment API
        WorldDataAttachment.registerWorldDataAttachment();
        
        // 加载配置
        ConfigService.load();
        LOGGER.info("Configuration loaded");
        // 注册世界生成要素与群系注入
        RoadFeatureRegistry.register();
        // 注册网络：服务端接收地图快照请求
        MapNetworkFabric.registerServerReceivers();
        
        // 注册服务器规划钩子：初始与动态增量规划
        ServerPlanningHooks.register();
        

    }
}