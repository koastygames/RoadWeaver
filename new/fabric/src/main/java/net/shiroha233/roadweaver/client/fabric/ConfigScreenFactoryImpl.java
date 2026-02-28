package net.shiroha233.roadweaver.client.fabric;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.PresetService;
import net.shiroha233.roadweaver.config.sub.*;

import java.util.Locale;

/**
 * Fabric 平台配置屏幕工厂实现
 * 职责：使用 Cloth Config API 创建配置界面
 */
public class ConfigScreenFactoryImpl {

    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.roadweaver.title"));

        PresetService.reload();
        ModConfig conf = ConfigService.get();
        ModConfig defaultConf = new ModConfig();
        builder.setSavingRunnable(ConfigService::save);

        ConfigEntryBuilder eb = builder.entryBuilder();

        // 结构预测配置
        buildStructurePredictionCategory(builder, eb, conf, defaultConf);
        
        // 路网规划配置
        buildPlanningCategory(builder, eb, conf, defaultConf);
        
        // 高速公路配置
        buildHighwayCategory(builder, eb, conf, defaultConf);
        
        // 长途驾驶配置
        buildLongDriveCategory(builder, eb, conf, defaultConf);
        
        // 道路生成配置
        buildRoadGenerationCategory(builder, eb, conf, defaultConf);
        
        // 地形设置
        buildSurfaceSettingsCategory(builder, eb, conf, defaultConf);
        
        // 桥梁配置
        buildBridgeCategory(builder, eb, conf, defaultConf);
        
        // 路边结构配置
        buildRoadsideStructuresCategory(builder, eb, conf, defaultConf);
        
        // 性能配置
        buildPerformanceCategory(builder, eb, conf, defaultConf);
        
        // 寻路代价配置
        buildPathfindingCostsCategory(builder, eb, conf, defaultConf);
        
        // 客户端配置
        buildClientCategory(builder, eb, conf, defaultConf);

        return builder.build();
    }

    private static void buildStructurePredictionCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.structure_filters"));
        StructurePredictionConfig cfg = conf.structurePrediction();
        StructurePredictionConfig def = defaultConf.structurePrediction();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.enable_prediction"), cfg.enabled())
                .setDefaultValue(def.enabled())
                .setTooltip(Component.translatable("config.roadweaver.enable_prediction.tooltip"))
                .setSaveConsumer(cfg::setEnabled)
                .build());

        category.addEntry(new OpenStructurePredictionDimensionWhitelistEntry());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.radius_chunks"), cfg.predictRadiusChunks())
                .setDefaultValue(def.predictRadiusChunks())
                .setTooltip(Component.translatable("config.roadweaver.radius_chunks.tooltip"))
                .setMin(1).setMax(4096)
                .setSaveConsumer(cfg::setPredictRadiusChunks)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.biome_prefilter"), cfg.biomePrefilter())
                .setDefaultValue(def.biomePrefilter())
                .setTooltip(Component.translatable("config.roadweaver.biome_prefilter.tooltip"))
                .setSaveConsumer(cfg::setBiomePrefilter)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.village_road_offset"), cfg.villageRoadOffset())
                .setDefaultValue(def.villageRoadOffset())
                .setTooltip(Component.translatable("config.roadweaver.village_road_offset.tooltip"))
                .setMin(0).setMax(64)
                .setSaveConsumer(cfg::setVillageRoadOffset)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.other_structure_road_offset"), cfg.otherStructureRoadOffset())
                .setDefaultValue(def.otherStructureRoadOffset())
                .setTooltip(Component.translatable("config.roadweaver.other_structure_road_offset.tooltip"))
                .setMin(0).setMax(64)
                .setSaveConsumer(cfg::setOtherStructureRoadOffset)
                .build());

        category.addEntry(new OpenStructureSelectionEntry());
    }

    private static void buildPlanningCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.road_planning"));
        PlanningConfig cfg = conf.planning();
        PlanningConfig def = defaultConf.planning();

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.initial_plan_radius_chunks"), cfg.initialPlanRadiusChunks())
                .setDefaultValue(def.initialPlanRadiusChunks())
                .setTooltip(Component.translatable("config.roadweaver.initial_plan_radius_chunks.tooltip"))
                .setMin(1).setMax(4096)
                .setSaveConsumer(cfg::setInitialPlanRadiusChunks)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.dynamic_plan_enabled"), cfg.dynamicPlanEnabled())
                .setDefaultValue(def.dynamicPlanEnabled())
                .setTooltip(Component.translatable("config.roadweaver.dynamic_plan_enabled.tooltip"))
                .setSaveConsumer(cfg::setDynamicPlanEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.dynamic_plan_radius_chunks"), cfg.dynamicPlanRadiusChunks())
                .setDefaultValue(def.dynamicPlanRadiusChunks())
                .setTooltip(Component.translatable("config.roadweaver.dynamic_plan_radius_chunks.tooltip"))
                .setMin(1).setMax(4096)
                .setSaveConsumer(cfg::setDynamicPlanRadiusChunks)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.dynamic_plan_stride_chunks"), cfg.dynamicPlanStrideChunks())
                .setDefaultValue(def.dynamicPlanStrideChunks())
                .setTooltip(Component.translatable("config.roadweaver.dynamic_plan_stride_chunks.tooltip"))
                .setMin(1).setMax(256)
                .setSaveConsumer(cfg::setDynamicPlanStrideChunks)
                .build());

        category.addEntry(eb
                .startEnumSelector(Component.translatable("config.roadweaver.planning_algorithm"),
                        PlanningConfig.PlanningAlgorithm.class, cfg.planningAlgorithm())
                .setDefaultValue(def.planningAlgorithm())
                .setTooltip(Component.translatable("config.roadweaver.planning_algorithm.tooltip"))
                .setEnumNameProvider(v -> Component.translatable("config.roadweaver.planning_algorithm.option." + v.name().toLowerCase(Locale.ROOT)))
                .setSaveConsumer(cfg::setPlanningAlgorithm)
                .build());
    }

    private static void buildHighwayCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.highway"));
        HighwayConfig cfg = conf.highway();
        HighwayConfig def = defaultConf.highway();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.highway_enabled"), cfg.enabled())
                .setDefaultValue(def.enabled())
                .setTooltip(Component.translatable("config.roadweaver.highway_enabled.tooltip"))
                .setSaveConsumer(cfg::setEnabled)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.highway_auto_plan_enabled"), cfg.autoPlanEnabled())
                .setDefaultValue(def.autoPlanEnabled())
                .setTooltip(Component.translatable("config.roadweaver.highway_auto_plan_enabled.tooltip"))
                .setSaveConsumer(cfg::setAutoPlanEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.highway_grid_blocks"), cfg.gridBlocks())
                .setDefaultValue(def.gridBlocks())
                .setTooltip(Component.translatable("config.roadweaver.highway_grid_blocks.tooltip"))
                .setMin(128).setMax(20000)
                .setSaveConsumer(cfg::setGridBlocks)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.highway_road_width"), cfg.roadWidth())
                .setDefaultValue(def.roadWidth())
                .setTooltip(Component.translatable("config.roadweaver.highway_road_width.tooltip"))
                .setMin(1).setMax(31)
                .setSaveConsumer(cfg::setRoadWidth)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.highway_slope_limit_enabled"), cfg.slopeLimitEnabled())
                .setDefaultValue(def.slopeLimitEnabled())
                .setTooltip(Component.translatable("config.roadweaver.highway_slope_limit_enabled.tooltip"))
                .setSaveConsumer(cfg::setSlopeLimitEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.highway_slope_run_blocks"), cfg.slopeRunBlocks())
                .setDefaultValue(def.slopeRunBlocks())
                .setTooltip(Component.translatable("config.roadweaver.highway_slope_run_blocks.tooltip"))
                .setMin(1).setMax(64)
                .setSaveConsumer(cfg::setSlopeRunBlocks)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.highway_slope_rise_blocks"), cfg.slopeRiseBlocks())
                .setDefaultValue(def.slopeRiseBlocks())
                .setTooltip(Component.translatable("config.roadweaver.highway_slope_rise_blocks.tooltip"))
                .setMin(0).setMax(16)
                .setSaveConsumer(cfg::setSlopeRiseBlocks)
                .build());
    }

    private static void buildLongDriveCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.long_drive"));
        LongDriveConfig cfg = conf.longDrive();
        LongDriveConfig def = defaultConf.longDrive();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.long_drive_enabled"), cfg.enabled())
                .setDefaultValue(def.enabled())
                .setTooltip(Component.translatable("config.roadweaver.long_drive_enabled.tooltip"))
                .setSaveConsumer(cfg::setEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.long_drive_road_width"), cfg.roadWidth())
                .setDefaultValue(def.roadWidth())
                .setTooltip(Component.translatable("config.roadweaver.long_drive_road_width.tooltip"))
                .setMin(1).setMax(15)
                .setSaveConsumer(cfg::setRoadWidth)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.long_drive_segment_length"), cfg.segmentLength())
                .setDefaultValue(def.segmentLength())
                .setTooltip(Component.translatable("config.roadweaver.long_drive_segment_length.tooltip"))
                .setMin(50).setMax(5000)
                .setSaveConsumer(cfg::setSegmentLength)
                .build());
    }

    private static void buildRoadGenerationCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.road_generation"));
        RoadAppearanceConfig cfg = conf.roadAppearance();
        RoadAppearanceConfig def = defaultConf.roadAppearance();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.roads_enabled"), cfg.roadsEnabled())
                .setDefaultValue(def.roadsEnabled())
                .setTooltip(Component.translatable("config.roadweaver.roads_enabled.tooltip"))
                .setSaveConsumer(cfg::setRoadsEnabled)
                .build());

        category.addEntry(new OpenDimensionRoadSettingsEntry());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.road_width"), cfg.roadWidth())
                .setDefaultValue(def.roadWidth())
                .setTooltip(Component.translatable("config.roadweaver.road_width.tooltip"))
                .setMin(0).setMax(15)
                .setSaveConsumer(cfg::setRoadWidth)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.lamp_interval"), cfg.lampInterval())
                .setDefaultValue(def.lampInterval())
                .setTooltip(Component.translatable("config.roadweaver.lamp_interval.tooltip"))
                .setMin(1).setMax(2048)
                .setSaveConsumer(cfg::setLampInterval)
                .build());

        category.addEntry(new OpenPresetEditorEntry());
    }

    private static void buildSurfaceSettingsCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.gen_surface"));
        RoadAppearanceConfig cfg = conf.roadAppearance();
        RoadAppearanceConfig def = defaultConf.roadAppearance();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.slope_limit_enabled"), cfg.slopeLimitEnabled())
                .setDefaultValue(def.slopeLimitEnabled())
                .setTooltip(Component.translatable("config.roadweaver.slope_limit_enabled.tooltip"))
                .setSaveConsumer(cfg::setSlopeLimitEnabled)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.road_fill_enabled"), cfg.roadFillEnabled())
                .setDefaultValue(def.roadFillEnabled())
                .setTooltip(Component.translatable("config.roadweaver.road_fill_enabled.tooltip"))
                .setSaveConsumer(cfg::setRoadFillEnabled)
                .build());
    }

    private static void buildBridgeCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.bridge"));
        BridgeConfig cfg = conf.bridge();
        BridgeConfig def = defaultConf.bridge();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.bridge_enabled"), cfg.enabled())
                .setDefaultValue(def.enabled())
                .setTooltip(Component.translatable("config.roadweaver.bridge_enabled.tooltip"))
                .setSaveConsumer(cfg::setEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.bridge_deck_clearance"), cfg.deckClearance())
                .setDefaultValue(def.deckClearance())
                .setTooltip(Component.translatable("config.roadweaver.bridge_deck_clearance.tooltip"))
                .setMin(1).setMax(8)
                .setSaveConsumer(cfg::setDeckClearance)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.bridge_max_length_blocks"), cfg.maxLengthBlocks())
                .setDefaultValue(def.maxLengthBlocks())
                .setTooltip(Component.translatable("config.roadweaver.bridge_max_length_blocks.tooltip"))
                .setMin(0).setMax(10000)
                .setSaveConsumer(cfg::setMaxLengthBlocks)
                .build());
    }

    private static void buildRoadsideStructuresCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.roadside_structures"));
        RoadsideStructureConfig cfg = conf.roadsideStructure();
        RoadsideStructureConfig def = defaultConf.roadsideStructure();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.roadside_structures_enabled"), cfg.enabled())
                .setDefaultValue(def.enabled())
                .setTooltip(Component.translatable("config.roadweaver.roadside_structures_enabled.tooltip"))
                .setSaveConsumer(cfg::setEnabled)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.max_structures_per_road"), cfg.maxStructuresPerRoad())
                .setDefaultValue(def.maxStructuresPerRoad())
                .setTooltip(Component.translatable("config.roadweaver.max_structures_per_road.tooltip"))
                .setMin(0).setMax(20)
                .setSaveConsumer(cfg::setMaxStructuresPerRoad)
                .build());
    }

    private static void buildPerformanceCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.gen_performance"));
        PerformanceConfig cfg = conf.performance();
        PerformanceConfig def = defaultConf.performance();

        category.addEntry(eb
                .startIntField(Component.translatable("text.autoconfig.roadweaver.option.computeThreads"), cfg.computeThreads())
                .setDefaultValue(def.computeThreads())
                .setTooltip(Component.translatable("text.autoconfig.roadweaver.option.computeThreads.@Tooltip"))
                .setMin(0).setMax(128)
                .setSaveConsumer(cfg::setComputeThreads)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("text.autoconfig.roadweaver.option.generationThreads"), cfg.generationThreads())
                .setDefaultValue(def.generationThreads())
                .setTooltip(Component.translatable("text.autoconfig.roadweaver.option.generationThreads.@Tooltip"))
                .setMin(1).setMax(64)
                .setSaveConsumer(cfg::setGenerationThreads)
                .build());

        category.addEntry(eb
                .startIntSlider(Component.translatable("config.roadweaver.thread_duty_cycle"), cfg.threadDutyCycle(), 1, 100)
                .setDefaultValue(def.threadDutyCycle())
                .setTooltip(Component.translatable("config.roadweaver.thread_duty_cycle.tooltip"))
                .setSaveConsumer(cfg::setThreadDutyCycle)
                .build());
    }

    private static void buildPathfindingCostsCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.pathfinding_costs"));
        PathfindingCostConfig cfg = conf.pathfindingCost();
        PathfindingCostConfig def = defaultConf.pathfindingCost();

        category.addEntry(eb
                .startEnumSelector(Component.translatable("config.roadweaver.pathfinding_algorithm"),
                        PathfindingCostConfig.PathfindingAlgorithm.class, cfg.pathfindingAlgorithm())
                .setDefaultValue(def.pathfindingAlgorithm())
                .setTooltip(Component.translatable("config.roadweaver.pathfinding_algorithm.tooltip"))
                .setEnumNameProvider(v -> Component.translatable("config.roadweaver.pathfinding_algorithm.option." + v.name().toLowerCase(Locale.ROOT)))
                .setSaveConsumer(cfg::setPathfindingAlgorithm)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.a_star_step"), cfg.aStarStep())
                .setDefaultValue(def.aStarStep())
                .setTooltip(Component.translatable("config.roadweaver.a_star_step.tooltip"))
                .setMin(4).setMax(128)
                .setSaveConsumer(cfg::setAStarStep)
                .build());

        category.addEntry(eb
                .startDoubleField(Component.translatable("config.roadweaver.ortho_step_cost"), cfg.orthoStepCost())
                .setDefaultValue(def.orthoStepCost())
                .setTooltip(Component.translatable("config.roadweaver.ortho_step_cost.tooltip"))
                .setMin(0)
                .setSaveConsumer(cfg::setOrthoStepCost)
                .build());

        category.addEntry(eb
                .startIntField(Component.translatable("config.roadweaver.elevation_weight"), cfg.elevationWeight())
                .setDefaultValue(def.elevationWeight())
                .setTooltip(Component.translatable("config.roadweaver.elevation_weight.tooltip"))
                .setMin(0)
                .setSaveConsumer(cfg::setElevationWeight)
                .build());
    }

    private static void buildClientCategory(ConfigBuilder builder, ConfigEntryBuilder eb, ModConfig conf, ModConfig defaultConf) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.roadweaver.category.test"));
        ClientConfig cfg = conf.client();
        ClientConfig def = defaultConf.client();

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.loading_tips_enabled"), cfg.loadingTipsEnabled())
                .setDefaultValue(def.loadingTipsEnabled())
                .setTooltip(Component.translatable("config.roadweaver.loading_tips_enabled.tooltip"))
                .setSaveConsumer(cfg::setLoadingTipsEnabled)
                .build());

        category.addEntry(eb
                .startBooleanToggle(Component.translatable("config.roadweaver.loading_progress_enabled"), cfg.loadingProgressEnabled())
                .setDefaultValue(def.loadingProgressEnabled())
                .setTooltip(Component.translatable("config.roadweaver.loading_progress_enabled.tooltip"))
                .setSaveConsumer(cfg::setLoadingProgressEnabled)
                .build());
    }
}
