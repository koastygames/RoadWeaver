package net.shiroha233.roadweaver.client.fabric;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.shiroha233.roadweaver.config.ConfigService;
import net.shiroha233.roadweaver.config.ModConfig;
import net.shiroha233.roadweaver.config.PresetService;

import java.util.Locale;

/**
 * Fabric 平台的配置屏幕工厂实现
 */
public class ConfigScreenFactoryImpl {

        /**
         * 创建配置屏幕 (Fabric实现)
         * 
         * @param parent 父屏幕
         * @return 配置屏幕实例
         */
        public static Screen createConfigScreen(Screen parent) {
                ConfigBuilder builder = ConfigBuilder.create()
                                .setParentScreen(parent)
                                .setTitle(Component.translatable("config.roadweaver.title"));

                PresetService.reload();
                ModConfig conf = ConfigService.get();
                ModConfig defaultConf = new ModConfig(); // 用于 Cloth Config "重置"按钮：提供每个选项的默认值
                builder.setSavingRunnable(ConfigService::save);

                ConfigCategory filters = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.structure_filters"));
                ConfigEntryBuilder eb = builder.entryBuilder();

                filters.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.enable_prediction"),
                                                conf.structurePredictionEnabled())
                                                .setDefaultValue(defaultConf.structurePredictionEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.enable_prediction.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setStructurePredictionEnabled(v); })
                                                .build());

                // 结构预测维度白名单
                filters.addEntry(new OpenStructurePredictionDimensionWhitelistEntry());

                filters.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.radius_chunks"),
                                                conf.predictRadiusChunks())
                                                .setDefaultValue(defaultConf.predictRadiusChunks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.radius_chunks.tooltip"))
                                                .setMin(1)
                                                .setMax(4096)
                                                .setSaveConsumer(v -> { if (v != null) conf.setPredictRadiusChunks(v); })
                                                .build());

                filters.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.biome_prefilter"),
                                                conf.biomePrefilter())
                                                .setDefaultValue(defaultConf.biomePrefilter())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.biome_prefilter.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setBiomePrefilter(v); })
                                                .build());

                // 打开结构选择界面的按钮（替代旧的白名单/黑名单输入框）
                filters.addEntry(new OpenStructureSelectionEntry());

                ConfigCategory planning = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.road_planning"));

                planning.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.initial_plan_radius_chunks"),
                                                conf.initialPlanRadiusChunks())
                                                .setDefaultValue(defaultConf.initialPlanRadiusChunks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.initial_plan_radius_chunks.tooltip"))
                                                .setMin(1)
                                                .setMax(4096)
                                                .setSaveConsumer(v -> { if (v != null) conf.setInitialPlanRadiusChunks(v); })
                                                .build());

                planning.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.dynamic_plan_enabled"),
                                                conf.dynamicPlanEnabled())
                                                .setDefaultValue(defaultConf.dynamicPlanEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.dynamic_plan_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setDynamicPlanEnabled(v); })
                                                .build());

                planning.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.dynamic_plan_radius_chunks"),
                                                conf.dynamicPlanRadiusChunks())
                                                .setDefaultValue(defaultConf.dynamicPlanRadiusChunks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.dynamic_plan_radius_chunks.tooltip"))
                                                .setMin(1)
                                                .setMax(4096)
                                                .setSaveConsumer(v -> { if (v != null) conf.setDynamicPlanRadiusChunks(v); })
                                                .build());

                planning.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.dynamic_plan_stride_chunks"),
                                                conf.dynamicPlanStrideChunks())
                                                .setDefaultValue(defaultConf.dynamicPlanStrideChunks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.dynamic_plan_stride_chunks.tooltip"))
                                                .setMin(1)
                                                .setMax(256)
                                                .setSaveConsumer(v -> { if (v != null) conf.setDynamicPlanStrideChunks(v); })
                                                .build());

                ModConfig.PlanningAlgorithm planningAlgo =
                                conf.planningAlgorithm() != null ? conf.planningAlgorithm() : ModConfig.PlanningAlgorithm.RNG;

                planning.addEntry(
                                eb.startEnumSelector(
                                                Component.translatable("config.roadweaver.planning_algorithm"),
                                                ModConfig.PlanningAlgorithm.class,
                                                planningAlgo)
                                                .setDefaultValue(defaultConf.planningAlgorithm())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.planning_algorithm.tooltip"))
                                                .setEnumNameProvider(v -> Component.translatable(
                                                                "config.roadweaver.planning_algorithm.option."
                                                                                + v.name().toLowerCase(Locale.ROOT)))
                                                .setSaveConsumer(v -> { if (v != null) conf.setPlanningAlgorithm(v); })
                                                .build());

                ConfigCategory highway = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.highway"));

                highway.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.highway_enabled"),
                                                conf.highwayEnabled())
                                                .setDefaultValue(defaultConf.highwayEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.highway_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setHighwayEnabled(v); })
                                                .build());

                highway.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.highway_auto_plan_enabled"),
                                                conf.highwayAutoPlanEnabled())
                                                .setDefaultValue(defaultConf.highwayAutoPlanEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.highway_auto_plan_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setHighwayAutoPlanEnabled(v); })
                                                .build());

                highway.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.highway_grid_blocks"),
                                                conf.highwayGridBlocks())
                                                .setDefaultValue(defaultConf.highwayGridBlocks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.highway_grid_blocks.tooltip"))
                                                .setMin(128)
                                                .setMax(20000)
                                                .setSaveConsumer(v -> { if (v != null) conf.setHighwayGridBlocks(v); })
                                                .build());

                highway.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.highway_dynamic_plan_enabled"),
                                                conf.highwayDynamicPlanEnabled())
                                                .setDefaultValue(defaultConf.highwayDynamicPlanEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.highway_dynamic_plan_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setHighwayDynamicPlanEnabled(v); })
                                                .build());

                highway.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.highway_road_width"),
                                                conf.highwayRoadWidth())
                                                .setDefaultValue(defaultConf.highwayRoadWidth())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.highway_road_width.tooltip"))
                                                .setMin(1)
                                                .setMax(31)
                                                .setSaveConsumer(v -> { if (v != null) conf.setHighwayRoadWidth(v); })
                                                .build());

                highway.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.highway_slope_limit_enabled"),
                                                conf.highwaySlopeLimitEnabled())
                                                .setDefaultValue(defaultConf.highwaySlopeLimitEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.highway_slope_limit_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setHighwaySlopeLimitEnabled(v); })
                                                .build());

                highway.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.highway_slope_run_blocks"),
                                                conf.highwaySlopeRunBlocks())
                                                .setDefaultValue(defaultConf.highwaySlopeRunBlocks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.highway_slope_run_blocks.tooltip"))
                                                .setMin(1)
                                                .setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setHighwaySlopeRunBlocks(v); })
                                                .build());

                highway.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.highway_slope_rise_blocks"),
                                                conf.highwaySlopeRiseBlocks())
                                                .setDefaultValue(defaultConf.highwaySlopeRiseBlocks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.highway_slope_rise_blocks.tooltip"))
                                                .setMin(0)
                                                .setMax(16)
                                                .setSaveConsumer(v -> { if (v != null) conf.setHighwaySlopeRiseBlocks(v); })
                                                .build());

                highway.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.highway_a_star_step"),
                                                conf.highwayAStarStep())
                                                .setDefaultValue(defaultConf.highwayAStarStep())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.highway_a_star_step.tooltip"))
                                                .setMin(4)
                                                .setMax(128)
                                                .setSaveConsumer(v -> { if (v != null) conf.setHighwayAStarStep(v); })
                                                .build());

                highway.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.highway_a_star_max_steps"),
                                                conf.highwayAStarMaxSteps())
                                                .setDefaultValue(defaultConf.highwayAStarMaxSteps())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.highway_a_star_max_steps.tooltip"))
                                                .setMin(1000)
                                                .setMax(200000)
                                                .setSaveConsumer(v -> { if (v != null) conf.setHighwayAStarMaxSteps(v); })
                                                .build());

                highway.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.highway_floating_weight"),
                                                conf.highwayFloatingWeight())
                                                .setDefaultValue(defaultConf.highwayFloatingWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.highway_floating_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setHighwayFloatingWeight(v); })
                                                .build());

                highway.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.highway_penetration_weight"),
                                                conf.highwayPenetrationWeight())
                                                .setDefaultValue(defaultConf.highwayPenetrationWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.highway_penetration_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setHighwayPenetrationWeight(v); })
                                                .build());

                ConfigCategory roadGen = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.road_generation"));

                // 新增：道路系统总开关
                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.roads_enabled"),
                                                conf.roadsEnabled())
                                                .setDefaultValue(defaultConf.roadsEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.roads_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setRoadsEnabled(v); })
                                                .build());

                // 新增：按维度道路功能控制
                roadGen.addEntry(new OpenDimensionRoadSettingsEntry());

                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.allow_artificial"),
                                                conf.allowArtificial())
                                                .setDefaultValue(defaultConf.allowArtificial())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.allow_artificial.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setAllowArtificial(v); })
                                                .build());

                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.allow_natural"),
                                                conf.allowNatural())
                                                .setDefaultValue(defaultConf.allowNatural())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.allow_natural.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setAllowNatural(v); })
                                                .build());

                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.place_waypoints"),
                                                conf.placeWaypoints())
                                                .setDefaultValue(defaultConf.placeWaypoints())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.place_waypoints.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setPlaceWaypoints(v); })
                                                .build());

                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.spawn_cabin_enabled"),
                                                conf.spawnCabinEnabled())
                                                .setDefaultValue(defaultConf.spawnCabinEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.spawn_cabin_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setSpawnCabinEnabled(v); })
                                                .build());

                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.road_width"),
                                                conf.roadWidth())
                                                .setDefaultValue(defaultConf.roadWidth())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.road_width.tooltip"))
                                                .setMin(0).setMax(15)
                                                .setSaveConsumer(v -> { if (v != null) conf.setRoadWidth(v); })
                                                .build());

                // 村庄缩进距离
                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.village_road_offset"),
                                                conf.villageRoadOffset())
                                                .setDefaultValue(defaultConf.villageRoadOffset())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.village_road_offset.tooltip"))
                                                .setMin(0).setMax(256)
                                                .setSaveConsumer(v -> { if (v != null) conf.setVillageRoadOffset(v); })
                                                .build());

                // 其他结构缩进距离
                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.other_structure_road_offset"),
                                                conf.otherStructureRoadOffset())
                                                .setDefaultValue(defaultConf.otherStructureRoadOffset())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.other_structure_road_offset.tooltip"))
                                                .setMin(0).setMax(256)
                                                .setSaveConsumer(v -> { if (v != null) conf.setOtherStructureRoadOffset(v); })
                                                .build());

                // 结构避让开关
                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.structure_avoidance_enabled"),
                                                conf.structureAvoidanceEnabled())
                                                .setDefaultValue(defaultConf.structureAvoidanceEnabled())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.structure_avoidance_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setStructureAvoidanceEnabled(v); })
                                                .build());

                roadGen.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.road_signs_enabled"),
                                                conf.roadSignsEnabled())
                                                .setDefaultValue(defaultConf.roadSignsEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.road_signs_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setRoadSignsEnabled(v); })
                                                .build());

                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.lamp_interval"),
                                                conf.lampInterval())
                                                .setDefaultValue(defaultConf.lampInterval())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.lamp_interval.tooltip"))
                                                .setMin(1).setMax(2048)
                                                .setSaveConsumer(v -> { if (v != null) conf.setLampInterval(v); })
                                                .build());

                roadGen.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.road_clear_height"),
                                                conf.roadClearHeight())
                                                .setDefaultValue(defaultConf.roadClearHeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.road_clear_height.tooltip"))
                                                .setMin(1).setMax(16)
                                                .setSaveConsumer(v -> { if (v != null) conf.setRoadClearHeight(v); })
                                                .build());

                roadGen.addEntry(new OpenPresetEditorEntry());

                ConfigCategory genSurface = builder
                                .getOrCreateCategory(Component.translatable("config.roadweaver.category.gen_surface"));

                genSurface.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.averaging_radius"),
                                                conf.averagingRadius())
                                                .setDefaultValue(defaultConf.averagingRadius())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.averaging_radius.tooltip"))
                                                .setMin(0).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setAveragingRadius(v); })
                                                .build());

                genSurface.addEntry(
                                eb.startIntField(
                                                Component.translatable(
                                                                "config.roadweaver.max_slope_step_per_two_segments"),
                                                conf.maxSlopeStepPerTwoSegments())
                                                .setDefaultValue(defaultConf.maxSlopeStepPerTwoSegments())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.max_slope_step_per_two_segments.tooltip"))
                                                .setMin(0).setMax(8)
                                                .setSaveConsumer(v -> { if (v != null) conf.setMaxSlopeStepPerTwoSegments(v); })
                                                .build());

                genSurface.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.slope_limit_enabled"),
                                                conf.slopeLimitEnabled())
                                                .setDefaultValue(defaultConf.slopeLimitEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.slope_limit_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setSlopeLimitEnabled(v); })
                                                .build());

                // 新增：道路填充（路基/堤道）总开关
                genSurface.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.road_fill_enabled"),
                                                conf.roadFillEnabled())
                                                .setDefaultValue(defaultConf.roadFillEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.road_fill_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setRoadFillEnabled(v); })
                                                .build());

                // 新增：插值路基填充
                genSurface.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.interpolated_roadbed_fill_enabled"),
                                                conf.interpolatedRoadbedFillEnabled())
                                                .setDefaultValue(defaultConf.interpolatedRoadbedFillEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.interpolated_roadbed_fill_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setInterpolatedRoadbedFillEnabled(v); })
                                                .build());

                genSurface.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.causeway_max_depth"),
                                                conf.causewayMaxDepth())
                                                .setDefaultValue(defaultConf.causewayMaxDepth())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.causeway_max_depth.tooltip"))
                                                .setMin(0).setMax(12)
                                                .setSaveConsumer(v -> { if (v != null) conf.setCausewayMaxDepth(v); })
                                                .build());
                genSurface.addEntry(
                                eb.startBooleanToggle(
                                                Component.translatable("config.roadweaver.prevent_trees_on_road"),
                                                conf.preventTreesOnRoad())
                                                .setDefaultValue(defaultConf.preventTreesOnRoad())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.prevent_trees_on_road.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setPreventTreesOnRoad(v); })
                                                .build());

                // 桥梁设置
                ConfigCategory bridge = builder
                                .getOrCreateCategory(Component.translatable("config.roadweaver.category.bridge"));
                bridge.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.bridge_enabled"),
                                                conf.bridgeEnabled())
                                                .setDefaultValue(defaultConf.bridgeEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeEnabled(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_deck_clearance"),
                                                conf.bridgeDeckClearance())
                                                .setDefaultValue(defaultConf.bridgeDeckClearance())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_deck_clearance.tooltip"))
                                                .setMin(1).setMax(8)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeDeckClearance(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_max_length_blocks"),
                                                conf.bridgeMaxLengthBlocks())
                                                .setDefaultValue(defaultConf.bridgeMaxLengthBlocks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_max_length_blocks.tooltip"))
                                                .setMin(0).setMax(10000)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeMaxLengthBlocks(v); })
                                                .build());
                bridge.addEntry(
                                eb.startBooleanToggle(
                                                Component.translatable("config.roadweaver.bridge_use_buoys_instead"),
                                                conf.bridgeUseBuoysInstead())
                                                .setDefaultValue(defaultConf.bridgeUseBuoysInstead())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_use_buoys_instead.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeUseBuoysInstead(v); })
                                                .build());
                bridge.addEntry(
                                eb.startBooleanToggle(
                                                Component.translatable("config.roadweaver.bridge_use_buoys_when_skipped"),
                                                conf.bridgeUseBuoysWhenSkipped())
                                                .setDefaultValue(defaultConf.bridgeUseBuoysWhenSkipped())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_use_buoys_when_skipped.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeUseBuoysWhenSkipped(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.buoy_interval_blocks"),
                                                conf.buoyIntervalBlocks())
                                                .setDefaultValue(defaultConf.buoyIntervalBlocks())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.buoy_interval_blocks.tooltip"))
                                                .setMin(4).setMax(256)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBuoyIntervalBlocks(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_pier_interval"),
                                                conf.bridgePierInterval())
                                                .setDefaultValue(defaultConf.bridgePierInterval())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_pier_interval.tooltip"))
                                                .setMin(3).setMax(32)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgePierInterval(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_pier_width"),
                                                conf.bridgePierWidth())
                                                .setDefaultValue(defaultConf.bridgePierWidth())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_pier_width.tooltip"))
                                                .setMin(1).setMax(3)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgePierWidth(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_pier_max_height"),
                                                conf.bridgePierMaxHeight())
                                                .setDefaultValue(defaultConf.bridgePierMaxHeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_pier_max_height.tooltip"))
                                                .setMin(6).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgePierMaxHeight(v); })
                                                .build());
                bridge.addEntry(
                                eb.startBooleanToggle(Component.translatable("config.roadweaver.bridge_keep_lamps"),
                                                conf.bridgeKeepLamps())
                                                .setDefaultValue(defaultConf.bridgeKeepLamps())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_keep_lamps.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeKeepLamps(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_ramp_segments"),
                                                conf.bridgeRampSegments())
                                                .setDefaultValue(defaultConf.bridgeRampSegments())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_ramp_segments.tooltip"))
                                                .setMin(0).setMax(12)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeRampSegments(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_min_water_depth"),
                                                conf.bridgeMinWaterDepth())
                                                .setDefaultValue(defaultConf.bridgeMinWaterDepth())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_min_water_depth.tooltip"))
                                                .setMin(1).setMax(10)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeMinWaterDepth(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_min_length"),
                                                conf.bridgeMinLength())
                                                .setDefaultValue(defaultConf.bridgeMinLength())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_min_length.tooltip"))
                                                .setMin(1).setMax(32)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeMinLength(v); })
                                                .build());
                bridge.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.bridge_merge_gap"),
                                                conf.bridgeMergeGap())
                                                .setDefaultValue(defaultConf.bridgeMergeGap())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.bridge_merge_gap.tooltip"))
                                                .setMin(1).setMax(32)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBridgeMergeGap(v); })
                                                .build());

                // 路边结构设置
                ConfigCategory roadsideStructures = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.roadside_structures"));
                roadsideStructures.addEntry(
                                eb.startBooleanToggle(
                                                Component.translatable("config.roadweaver.roadside_structures_enabled"),
                                                conf.roadsideStructuresEnabled())
                                                .setDefaultValue(defaultConf.roadsideStructuresEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.roadside_structures_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setRoadsideStructuresEnabled(v); })
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.max_structures_per_road"),
                                                conf.maxStructuresPerRoad())
                                                .setDefaultValue(defaultConf.maxStructuresPerRoad())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.max_structures_per_road.tooltip"))
                                                .setMin(0).setMax(20)
                                                .setSaveConsumer(v -> { if (v != null) conf.setMaxStructuresPerRoad(v); })
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.small_structure_offset"),
                                                conf.smallStructureOffset())
                                                .setDefaultValue(defaultConf.smallStructureOffset())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.small_structure_offset.tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setSmallStructureOffset(v); })
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.medium_structure_offset"),
                                                conf.mediumStructureOffset())
                                                .setDefaultValue(defaultConf.mediumStructureOffset())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.medium_structure_offset.tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setMediumStructureOffset(v); })
                                                .build());
                roadsideStructures.addEntry(
                                eb.startIntField(
                                                Component.translatable("config.roadweaver.large_structure_offset"),
                                                conf.largeStructureOffset())
                                                .setDefaultValue(defaultConf.largeStructureOffset())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.large_structure_offset.tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setLargeStructureOffset(v); })
                                                .build());

                ConfigCategory genPerformance = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.gen_performance"));

                genPerformance.addEntry(
                                eb.startIntField(
                                                Component.translatable(
                                                                "text.autoconfig.roadweaver.option.computeThreads"),
                                                conf.computeThreads())
                                                .setDefaultValue(defaultConf.computeThreads())
                                                .setTooltip(Component.translatable(
                                                                "text.autoconfig.roadweaver.option.computeThreads.@Tooltip"))
                                                .setMin(0).setMax(128)
                                                .setSaveConsumer(v -> { if (v != null) conf.setComputeThreads(v); })
                                                .build());

                genPerformance.addEntry(
                                eb.startIntField(
                                                Component.translatable(
                                                                "text.autoconfig.roadweaver.option.generationThreads"),
                                                conf.generationThreads())
                                                .setDefaultValue(defaultConf.generationThreads())
                                                .setTooltip(Component.translatable(
                                                                "text.autoconfig.roadweaver.option.generationThreads.@Tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setGenerationThreads(v); })
                                                .build());

                genPerformance.addEntry(
                                eb.startIntField(Component.translatable(
                                                "text.autoconfig.roadweaver.option.initialGenerationThreads"),
                                                conf.initialGenerationThreads())
                                                .setDefaultValue(defaultConf.initialGenerationThreads())
                                                .setTooltip(Component.translatable(
                                                                "text.autoconfig.roadweaver.option.initialGenerationThreads.@Tooltip"))
                                                .setMin(1).setMax(64)
                                                .setSaveConsumer(v -> { if (v != null) conf.setInitialGenerationThreads(v); })
                                                .build());

                genPerformance.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.max_concurrent_generations"),
                                                conf.maxConcurrentGenerations())
                                                .setDefaultValue(defaultConf.maxConcurrentGenerations())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.max_concurrent_generations.tooltip"))
                                                .setMin(1).setMax(128)
                                                .setSaveConsumer(v -> { if (v != null) conf.setMaxConcurrentGenerations(v); })
                                                .build());

                genPerformance.addEntry(
                                eb.startIntSlider(Component.translatable("config.roadweaver.thread_duty_cycle"),
                                                conf.threadDutyCycle(), 1, 100)
                                                .setDefaultValue(defaultConf.threadDutyCycle())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.thread_duty_cycle.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setThreadDutyCycle(v); })
                                                .build());

                ConfigCategory pathfindingCosts = builder.getOrCreateCategory(
                                Component.translatable("config.roadweaver.category.pathfinding_costs"));

                ModConfig.PathfindingAlgorithm pathfindingAlgo =
                                conf.pathfindingAlgorithm() != null ? conf.pathfindingAlgorithm()
                                                : ModConfig.PathfindingAlgorithm.ASTAR_BASIC;

                pathfindingCosts.addEntry(
                                eb.startEnumSelector(
                                                Component.translatable("config.roadweaver.pathfinding_algorithm"),
                                                ModConfig.PathfindingAlgorithm.class,
                                                pathfindingAlgo)
                                                .setDefaultValue(defaultConf.pathfindingAlgorithm())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.pathfinding_algorithm.tooltip"))
                                                .setEnumNameProvider(v -> Component.translatable(
                                                                "config.roadweaver.pathfinding_algorithm.option."
                                                                                + v.name().toLowerCase(Locale.ROOT)))
                                                .setSaveConsumer(v -> { if (v != null) conf.setPathfindingAlgorithm(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startBooleanToggle(
                                                Component.translatable("config.roadweaver.hierarchical_pathfinding_enabled"),
                                                conf.hierarchicalPathfindingEnabled())
                                                .setDefaultValue(defaultConf.hierarchicalPathfindingEnabled())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.hierarchical_pathfinding_enabled.tooltip"))
                                                .setSaveConsumer(v -> { if (v != null) conf.setHierarchicalPathfindingEnabled(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.a_star_step"),
                                                conf.aStarStep())
                                                .setDefaultValue(defaultConf.aStarStep())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.a_star_step.tooltip"))
                                                .setMin(4).setMax(128)
                                                .setSaveConsumer(v -> { if (v != null) conf.setAStarStep(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.a_star_max_steps"),
                                                conf.aStarMaxSteps())
                                                .setDefaultValue(defaultConf.aStarMaxSteps())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.a_star_max_steps.tooltip"))
                                                .setMin(100).setMax(100000)
                                                .setSaveConsumer(v -> { if (v != null) conf.setAStarMaxSteps(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.ortho_step_cost"),
                                                conf.orthoStepCost())
                                                .setDefaultValue(defaultConf.orthoStepCost())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.ortho_step_cost.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setOrthoStepCost(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.diag_step_cost"),
                                                conf.diagStepCost())
                                                .setDefaultValue(defaultConf.diagStepCost())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.diag_step_cost.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setDiagStepCost(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.elevation_weight"),
                                                conf.elevationWeight())
                                                .setDefaultValue(defaultConf.elevationWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.elevation_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setElevationWeight(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.biome_weight"),
                                                conf.biomeWeight())
                                                .setDefaultValue(defaultConf.biomeWeight())
                                                .setTooltip(Component
                                                                .translatable("config.roadweaver.biome_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setBiomeWeight(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.stability_weight"),
                                                conf.stabilityWeight())
                                                .setDefaultValue(defaultConf.stabilityWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.stability_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setStabilityWeight(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.water_depth_weight"),
                                                conf.waterDepthWeight())
                                                .setDefaultValue(defaultConf.waterDepthWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.water_depth_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setWaterDepthWeight(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.near_water_cost"),
                                                conf.nearWaterCost())
                                                .setDefaultValue(defaultConf.nearWaterCost())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.near_water_cost.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setNearWaterCost(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startIntField(Component.translatable("config.roadweaver.water_proximity_cost"),
                                                conf.waterProximityCost())
                                                .setDefaultValue(defaultConf.waterProximityCost())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.water_proximity_cost.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setWaterProximityCost(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.heuristic_weight"),
                                                conf.heuristicWeight())
                                                .setDefaultValue(defaultConf.heuristicWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.heuristic_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setHeuristicWeight(v); })
                                                .build());

                pathfindingCosts.addEntry(
                                eb.startDoubleField(Component.translatable("config.roadweaver.deviation_weight"),
                                                conf.deviationWeight())
                                                .setDefaultValue(defaultConf.deviationWeight())
                                                .setTooltip(Component.translatable(
                                                                "config.roadweaver.deviation_weight.tooltip"))
                                                .setMin(0)
                                                .setSaveConsumer(v -> { if (v != null) conf.setDeviationWeight(v); })
                                                .build());

                return builder.build();
        }
}
