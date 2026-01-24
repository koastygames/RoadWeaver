package net.shiroha233.roadweaver.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModConfig {
    public enum PlanningAlgorithm {
        KNN,
        DELAUNAY,
        RNG,
        MST
    }

    public enum PathfindingAlgorithm {
        ASTAR_BASIC,
        ASTAR_BIDIRECTIONAL,
        GRADIENT_DESCENT
    }

    // 旧字段：历史上用于控制“村庄预测”。为兼容旧配置文件保留。
    private boolean villagePredictionEnabled;
    // 新字段：结构预测总开关（支持多维度）。用 Boolean 以区分“缺失字段(null)”与用户显式设置。
    private Boolean structurePredictionEnabled;
    private int predictRadiusChunks;
    private boolean biomePrefilter;
    private List<String> structureWhitelist;
    private List<String> structureBlacklist;

    // 结构预测维度白名单：仅在白名单中的维度会进行预测/扫描。
    // 用字符串存储 ResourceLocation（例如 "minecraft:overworld"），避免在 config 层引入 MC 类依赖。
    private List<String> structurePredictionDimensionWhitelist;

    // 路网规划配置
    private int initialPlanRadiusChunks; // 新建世界后以出生点为中心的初始规划半径（区块）
    private boolean dynamicPlanEnabled; // 是否启用基于玩家的动态增量规划
    private int dynamicPlanRadiusChunks; // 玩家为中心的动态规划半径（区块）
    private int dynamicPlanStrideChunks; // 动态规划触发步进（区块），用于判定玩家移动到新网格时触发
    private PlanningAlgorithm planningAlgorithm; // 路网连边算法

    // 公路（Highway）配置：独立于 path 道路系统
    private boolean highwayEnabled;
    private boolean highwayAutoPlanEnabled;
    // 公路网格间距（方块）：每隔多少方块生成一个网格节点
    private int highwayGridBlocks;
    // 是否启用公路动态拓展（3x3 cell 滚动窗口）。关闭时仅保持玩家所在 1x1 cell。
    private Boolean highwayDynamicPlanEnabled;
    private int highwayRoadWidth;
    // Highway 高度平滑：默认每 5 格高度差为 1。
    // Boolean 用于兼容旧配置：缺失字段=null 时保持默认启用。
    private Boolean highwaySlopeLimitEnabled;
    private int highwaySlopeRunBlocks;
    private int highwaySlopeRiseBlocks;
    private int highwayAStarStep;
    private int highwayAStarMaxSteps;
    private double highwayFloatingWeight;
    private double highwayPenetrationWeight;

    // 道路生成配置
    // 道路系统总开关（Boolean 用于兼容旧配置：缺失字段=null 时保持旧行为=启用）
    private Boolean roadsEnabled;
    private boolean allowArtificial;
    private boolean allowNatural;
    private boolean placeWaypoints;
    private boolean spawnCabinEnabled;
    private int averagingRadius;
    // 是否启用分层寻路（粗步长引导 + 细步长精化）
    private boolean hierarchicalPathfindingEnabled;
    private int generationThreads;
    private int computeThreads; // 计算线程池大小（0=自动，>0=固定值）
    private int initialGenerationThreads; // 初始生成专用线程数
    private int maxConcurrentGenerations;
    private int threadDutyCycle; // 线程占空比（1-100%），控制CPU使用率
    private int aStarStep; // A* 采样步长（方块）
    private int aStarMaxSteps; // A* 寻路最大步数上限
    private int causewayMaxDepth;
    // 是否启用道路“路基/地形适配”填充（RoadTerrainAdapter）。
    // Boolean 用于兼容旧配置：缺失字段=null 时保持旧行为=启用。
    private Boolean roadFillEnabled;
    private int maxSlopeStepPerTwoSegments;
    private boolean slopeLimitEnabled = true; // 是否启用基于 maxSlopeStepPerTwoSegments 的限坡平滑
    private PathfindingAlgorithm pathfindingAlgorithm; // 具体寻路算法策略

    private int roadWidth;
    // 是否启用道路路牌（距离牌/跨海提示牌）
    private boolean roadSignsEnabled;

    // 是否启用“高度平滑插值路基填充”（RoadTerrainAdapter.adaptWithInterpolation）。
    // Boolean 用于兼容旧配置：缺失字段=null 时保持旧行为=启用。
    private Boolean interpolatedRoadbedFillEnabled;
    private int lampInterval;
    private int roadClearHeight;
    private boolean tunnelEnabled;
    private int tunnelClearHeight;
    private boolean preventTreesOnRoad; // 阻止树木在道路上生成

    // 桥梁配置
    private boolean bridgeEnabled;
    private int bridgeDeckClearance;
    private int bridgeMaxLengthBlocks; // 超过该长度的水域跨度将跳过（0=不限制）
    private boolean bridgeUseBuoysInstead; // 用浮标代替桥梁
    private boolean bridgeUseBuoysWhenSkipped; // 当桥梁因超长跳过时，用浮标代替
    private int buoyIntervalBlocks; // 浮标间隔（方块）
    private int bridgePierInterval;
    private int bridgePierWidth;
    private int bridgePierMaxHeight;
    private boolean bridgeKeepLamps;
    private int bridgeRampSegments;
    private int bridgeMinWaterDepth; // 最小水深，低于此值不建桥
    private int bridgeMinLength; // 最小桥梁长度（段数），太短的桥跳过
    private int bridgeMergeGap; // 桥梁区间合并间隔，间隔小于此值的区间合并

    // 路边结构配置
    private boolean roadsideStructuresEnabled;
    private int maxStructuresPerRoad; // 每条道路最多放置的结构数
    private int smallStructureOffset; // 小型结构距道路中心的距离
    private int mediumStructureOffset; // 中型结构距道路中心的距离
    private int largeStructureOffset; // 大型结构距道路中心的距离

    // 结构距离控制
    private int villageRoadOffset; // 村庄类结构的道路缩进距离（方块）
    private int otherStructureRoadOffset; // 其他结构的道路缩进距离（方块）
    private boolean structureAvoidanceEnabled; // 放置阶段检测并跳过结构内的道路
    private int structureRoadOffset; // 道路端点距结构中心的缩进距离（方块）（兼容旧配置）

    // 按维度覆盖的道路功能设置。key 为维度 ResourceLocation 字符串（例如 "minecraft:overworld"）。
    private Map<String, DimensionRoadSettings> dimensionRoadSettings;

    // A* 寻路成本权重
    private double orthoStepCost;
    private double diagStepCost;
    private int elevationWeight;
    private int biomeWeight;
    private int stabilityWeight;
    private int waterDepthWeight;
    private int nearWaterCost;
    private int waterProximityCost;
    private double heuristicWeight;
    private double deviationWeight;

    // 测试栏配置
    private boolean loadingTipsEnabled;

    public ModConfig() {
        this.villagePredictionEnabled = true;
        this.structurePredictionEnabled = true;
        this.predictRadiusChunks = 1024;
        this.biomePrefilter = true;
        this.structureWhitelist = new ArrayList<>();
        this.structureBlacklist = new ArrayList<>();
        this.structureWhitelist.add("#minecraft:village");

        // 默认开启三大原版维度的预测（多维度搜寻）。
        this.structurePredictionDimensionWhitelist = new ArrayList<>();
        this.structurePredictionDimensionWhitelist.add("minecraft:overworld");
        this.structurePredictionDimensionWhitelist.add("minecraft:the_nether");
        this.structurePredictionDimensionWhitelist.add("minecraft:the_end");
        this.structurePredictionDimensionWhitelist.add("minecraft:the_end");

        // 默认规划参数：初始128区块；动态规划开启，半径256区块
        this.initialPlanRadiusChunks = 128;
        this.dynamicPlanEnabled = true;
        this.dynamicPlanRadiusChunks = 256;
        this.dynamicPlanStrideChunks = Math.max(8, Math.min(64, this.dynamicPlanRadiusChunks / 2));
        this.planningAlgorithm = PlanningAlgorithm.RNG;

        // 公路（Highway）默认参数：默认关闭，避免改变旧世界行为
        this.highwayEnabled = false;
        this.highwayAutoPlanEnabled = true;
        this.highwayGridBlocks = 2500;
        this.highwayDynamicPlanEnabled = true;
        this.highwayRoadWidth = 7;
        this.highwaySlopeLimitEnabled = true;
        this.highwaySlopeRunBlocks = 5;
        this.highwaySlopeRiseBlocks = 1;
        this.highwayAStarStep = 32;
        this.highwayAStarMaxSteps = 20000;
        this.highwayFloatingWeight = 2.0;
        this.highwayPenetrationWeight = 4.0;

        // 道路生成默认参数
        this.roadsEnabled = true;
        this.allowArtificial = true;
        this.allowNatural = true;
        this.placeWaypoints = false;
        this.spawnCabinEnabled = true;
        this.averagingRadius = 8;

        // 分层寻路默认关闭，避免改变旧世界的生成行为/性能特征
        this.hierarchicalPathfindingEnabled = false;

        this.generationThreads = Math.max(2, Math.min(3, Runtime.getRuntime().availableProcessors()));
        // computeThreads=0 表示自动模式：在 ThreadPoolManager 中按 CPU-1 计算
        this.computeThreads = 0;
        this.initialGenerationThreads = 6; // 初始生成默认6个线程
        this.maxConcurrentGenerations = Math.max(1, Math.min(3, this.generationThreads));
        this.threadDutyCycle = 50; // 默认50%占空比，降低CPU占用
        this.aStarStep = 16;
        this.aStarMaxSteps = 10000;
        this.causewayMaxDepth = 1;
        this.roadFillEnabled = true;
        this.maxSlopeStepPerTwoSegments = 1;
        this.slopeLimitEnabled = true;
        this.pathfindingAlgorithm = PathfindingAlgorithm.GRADIENT_DESCENT;

        // 新增默认值
        this.roadWidth = 3;
        this.roadSignsEnabled = false;
        this.interpolatedRoadbedFillEnabled = true;
        this.lampInterval = 32;
        this.roadClearHeight = 4;
        this.tunnelEnabled = false;
        this.tunnelClearHeight = 5;
        this.preventTreesOnRoad = true; // 默认开启

        // 维度覆盖默认值
        this.dimensionRoadSettings = new HashMap<>();

        // 桥梁默认值
        this.bridgeEnabled = true;
        this.bridgeDeckClearance = 2;
        this.bridgeMaxLengthBlocks = 100;
        this.bridgeUseBuoysInstead = false;
        this.bridgeUseBuoysWhenSkipped = false;
        this.buoyIntervalBlocks = 32;
        this.bridgePierInterval = 6;
        this.bridgePierWidth = 1;
        this.bridgePierMaxHeight = 20;
        this.bridgeKeepLamps = true;
        this.bridgeRampSegments = 4;
        this.bridgeMinWaterDepth = 1; // 水深至少1格才建桥
        this.bridgeMinLength = 5; // 桥至少5段才建，避免小水坑
        this.bridgeMergeGap = 8; // 间隔小于8段的桥梁区间合并

        // 路边结构默认值
        this.roadsideStructuresEnabled = true;
        this.maxStructuresPerRoad = 3; // 每条道路最多3个结构
        this.smallStructureOffset = 8; // 小型结构距道路8格
        this.mediumStructureOffset = 12; // 中型结构距道路12格
        this.largeStructureOffset = 16; // 大型结构距道路16格

        // 结构距离控制默认值
        this.villageRoadOffset = 60; // 村庄默认缩进 60 格
        this.otherStructureRoadOffset = 25; // 其他结构默认缩进 25 格
        this.structureAvoidanceEnabled = true; // 默认开启结构避让
        this.structureRoadOffset = 60; // 道路端点默认缩进 60 格（兼容旧配置）

        // A* 寻路成本权重
        this.orthoStepCost = 1.0;
        this.diagStepCost = 1.414;
        this.elevationWeight = 80;
        this.biomeWeight = 2;
        this.stabilityWeight = 15;
        this.waterDepthWeight = 80;
        this.nearWaterCost = 80;
        this.waterProximityCost = 20;
        this.heuristicWeight = 15.0;
        this.deviationWeight = 0.5;

        // 测试栏默认值
        this.loadingTipsEnabled = true;
    }

    public boolean villagePredictionEnabled() {
        return structurePredictionEnabled();
    }

    public void setVillagePredictionEnabled(boolean villagePredictionEnabled) {
        this.villagePredictionEnabled = villagePredictionEnabled;
        this.structurePredictionEnabled = villagePredictionEnabled;
    }

    public boolean structurePredictionEnabled() {
        return structurePredictionEnabled != null ? structurePredictionEnabled : villagePredictionEnabled;
    }

    public void setStructurePredictionEnabled(boolean enabled) {
        this.structurePredictionEnabled = enabled;
        this.villagePredictionEnabled = enabled;
    }

    public List<String> structurePredictionDimensionWhitelist() {
        return structurePredictionDimensionWhitelist;
    }

    public void setStructurePredictionDimensionWhitelist(List<String> whitelist) {
        this.structurePredictionDimensionWhitelist = whitelist == null ? new ArrayList<>() : new ArrayList<>(whitelist);
    }

    public boolean isStructurePredictionEnabledForDimension(String dimensionId) {
        if (!structurePredictionEnabled())
            return false;
        if (dimensionId == null || dimensionId.isEmpty())
            return false;
        if (structurePredictionDimensionWhitelist == null || structurePredictionDimensionWhitelist.isEmpty())
            return false;
        return structurePredictionDimensionWhitelist.contains(dimensionId);
    }

    public int predictRadiusChunks() {
        return predictRadiusChunks;
    }

    public void setPredictRadiusChunks(int predictRadiusChunks) {
        this.predictRadiusChunks = predictRadiusChunks;
    }

    public boolean biomePrefilter() {
        return biomePrefilter;
    }

    public void setBiomePrefilter(boolean biomePrefilter) {
        this.biomePrefilter = biomePrefilter;
    }

    public List<String> structureWhitelist() {
        return structureWhitelist;
    }

    public void setStructureWhitelist(List<String> structureWhitelist) {
        this.structureWhitelist = structureWhitelist == null ? new ArrayList<>() : new ArrayList<>(structureWhitelist);
    }

    public List<String> structureBlacklist() {
        return structureBlacklist;
    }

    public void setStructureBlacklist(List<String> structureBlacklist) {
        this.structureBlacklist = structureBlacklist == null ? new ArrayList<>() : new ArrayList<>(structureBlacklist);
    }

    // 载入后修复缺省值与兼容项
    public void sanitize() {
        if (structureWhitelist == null)
            structureWhitelist = new ArrayList<>();
        if (structureBlacklist == null)
            structureBlacklist = new ArrayList<>();

        // 结构预测开关：若新字段缺失，则沿用旧字段值
        if (structurePredictionEnabled == null) {
            structurePredictionEnabled = villagePredictionEnabled;
        }

        // 维度白名单：若缺失字段则填充默认维度；若用户显式清空则尊重（=不在任何维度预测）
        if (structurePredictionDimensionWhitelist == null) {
            structurePredictionDimensionWhitelist = new ArrayList<>();
            structurePredictionDimensionWhitelist.add("minecraft:overworld");
            structurePredictionDimensionWhitelist.add("minecraft:the_nether");
            structurePredictionDimensionWhitelist.add("minecraft:the_end");
        }

        if (predictRadiusChunks <= 0)
            predictRadiusChunks = 1024;

        // 道路系统总开关：缺失字段时保持旧行为（启用）
        if (roadsEnabled == null)
            roadsEnabled = true;
        if (initialPlanRadiusChunks <= 0)
            initialPlanRadiusChunks = 64;
        if (dynamicPlanRadiusChunks <= 0)
            dynamicPlanRadiusChunks = 256;
        if (dynamicPlanStrideChunks <= 0)
            dynamicPlanStrideChunks = Math.max(8, Math.min(64, Math.max(1, dynamicPlanRadiusChunks) / 2));
        if (dynamicPlanStrideChunks > dynamicPlanRadiusChunks)
            dynamicPlanStrideChunks = dynamicPlanRadiusChunks;
        if (dynamicPlanStrideChunks > 256)
            dynamicPlanStrideChunks = 256;
        if (planningAlgorithm == null)
            planningAlgorithm = PlanningAlgorithm.RNG;

        // Highway 字段校验
        // 公路动态拓展缺省为 true（用于兼容旧配置文件：旧版本没有该字段，Gson 反序列化后为 null）。
        if (highwayDynamicPlanEnabled == null)
            highwayDynamicPlanEnabled = true;
        if (highwaySlopeLimitEnabled == null)
            highwaySlopeLimitEnabled = true;
        if (highwayGridBlocks < 128)
            highwayGridBlocks = 128;
        if (highwayGridBlocks > 20000)
            highwayGridBlocks = 20000;
        if (highwayRoadWidth < 1)
            highwayRoadWidth = 1;
        if (highwayRoadWidth > 31)
            highwayRoadWidth = 31;
        if (highwaySlopeRunBlocks < 1)
            highwaySlopeRunBlocks = 5;
        if (highwaySlopeRunBlocks > 64)
            highwaySlopeRunBlocks = 64;
        if (highwaySlopeRiseBlocks < 0)
            highwaySlopeRiseBlocks = 0;
        if (highwaySlopeRiseBlocks > 16)
            highwaySlopeRiseBlocks = 16;
        if (highwayAStarStep < 4)
            highwayAStarStep = 32;
        if (highwayAStarStep > 128)
            highwayAStarStep = 128;
        if (highwayAStarMaxSteps < 1000)
            highwayAStarMaxSteps = 1000;
        if (highwayAStarMaxSteps > 200000)
            highwayAStarMaxSteps = 200000;
        if (highwayFloatingWeight < 0)
            highwayFloatingWeight = 0;
        if (highwayPenetrationWeight < 0)
            highwayPenetrationWeight = 0;
        if (aStarStep > 128)
            aStarStep = 128; // 步数上限
        if (aStarMaxSteps < 3000)
            aStarMaxSteps = 3000; // 最小步数下限
        if (aStarMaxSteps > 100000)
            aStarMaxSteps = 100000; // 最大步数上限
        if (causewayMaxDepth < 0)
            causewayMaxDepth = 0;// 最小填充深度
        if (causewayMaxDepth > 12)
            causewayMaxDepth = 12;// 最大填充深度
        if (maxSlopeStepPerTwoSegments < 0)
            maxSlopeStepPerTwoSegments = 0;// 最小斜坡步数
        if (maxSlopeStepPerTwoSegments > 8)
            maxSlopeStepPerTwoSegments = 8;// 最大斜坡步数

        // computeThreads 校验：0=自动模式，>0 时限制上限，防止配置过大
        if (computeThreads < 0)
            computeThreads = 0;
        if (computeThreads > 128)
            computeThreads = 128;

        // 线程占空比校验：1-100，0 或异常值回退到 50%（推荐）
        if (threadDutyCycle < 1 || threadDutyCycle > 100)
            threadDutyCycle = 50;

        if (pathfindingAlgorithm == null) {
            // 迁移旧配置
            pathfindingAlgorithm = PathfindingAlgorithm.ASTAR_BASIC;
        }

        // 插值路基填充：缺失字段时保持旧行为（启用）
        if (interpolatedRoadbedFillEnabled == null)
            interpolatedRoadbedFillEnabled = true;

        // 路基/地形适配填充：缺失字段时保持旧行为（启用）
        if (roadFillEnabled == null)
            roadFillEnabled = true;

        // 分层寻路新字段：缺省为 false
        // 这里不做额外校验，仅保证反序列化时 null/缺失字段不会影响

        // 新增字段校验
        if (roadWidth < 0)
            roadWidth = 0; // 0=自动
        if (roadWidth > 15)
            roadWidth = 15; // 宽度上限合理限制
        if (lampInterval < 1)
            lampInterval = 59; // 保底
        if (lampInterval > 2048)
            lampInterval = 2048;
        if (roadClearHeight < 1)
            roadClearHeight = 4;
        if (roadClearHeight > 16)
            roadClearHeight = 16;
        if (tunnelClearHeight < 2)
            tunnelClearHeight = 2;
        if (tunnelClearHeight > 16)
            tunnelClearHeight = 16;

        // 桥梁字段校验
        if (bridgeDeckClearance < 1)
            bridgeDeckClearance = 1;
        if (bridgeDeckClearance > 8)
            bridgeDeckClearance = 8;
        if (bridgeMaxLengthBlocks < 0)
            bridgeMaxLengthBlocks = 0;
        if (bridgeMaxLengthBlocks > 10000)
            bridgeMaxLengthBlocks = 10000;
        if (buoyIntervalBlocks < 4)
            buoyIntervalBlocks = 4;
        if (buoyIntervalBlocks > 256)
            buoyIntervalBlocks = 256;
        if (bridgePierInterval < 3)
            bridgePierInterval = 3;
        if (bridgePierInterval > 32)
            bridgePierInterval = 32;
        if (bridgePierWidth < 1)
            bridgePierWidth = 1;
        if (bridgePierWidth > 3)
            bridgePierWidth = 3;
        if (bridgePierMaxHeight < 6)
            bridgePierMaxHeight = 6;
        if (bridgePierMaxHeight > 64)
            bridgePierMaxHeight = 64;
        if (bridgeRampSegments < 0)
            bridgeRampSegments = 0;
        if (bridgeRampSegments > 12)
            bridgeRampSegments = 12;

        // A* 寻路成本权重校验
        if (orthoStepCost < 0)
            orthoStepCost = 0;
        if (diagStepCost < 0)
            diagStepCost = 0;
        if (elevationWeight < 0)
            elevationWeight = 0;
        if (biomeWeight < 0)
            biomeWeight = 0;
        if (stabilityWeight < 0)
            stabilityWeight = 0;
        if (waterDepthWeight < 0)
            waterDepthWeight = 0;
        if (nearWaterCost < 0)
            nearWaterCost = 0;
        if (waterProximityCost < 0)
            waterProximityCost = 0;
        if (heuristicWeight < 0)
            heuristicWeight = 0;
        if (deviationWeight < 0)
            deviationWeight = 0;

        // 路边结构配置校验
        if (maxStructuresPerRoad < 0)
            maxStructuresPerRoad = 0;
        if (maxStructuresPerRoad > 20)
            maxStructuresPerRoad = 20;
        if (smallStructureOffset < 1)
            smallStructureOffset = 1;
        if (smallStructureOffset > 64)
            smallStructureOffset = 64;
        if (mediumStructureOffset < 1)
            mediumStructureOffset = 1;
        if (mediumStructureOffset > 64)
            mediumStructureOffset = 64;
        if (largeStructureOffset < 1)
            largeStructureOffset = 1;
        if (largeStructureOffset > 64)
            largeStructureOffset = 64;

        // 结构距离控制校验
        if (villageRoadOffset < 0)
            villageRoadOffset = 0;
        if (villageRoadOffset > 256)
            villageRoadOffset = 256;
        if (otherStructureRoadOffset < 0)
            otherStructureRoadOffset = 0;
        if (otherStructureRoadOffset > 256)
            otherStructureRoadOffset = 256;
        if (structureRoadOffset < 0)
            structureRoadOffset = 0;
        if (structureRoadOffset > 256)
            structureRoadOffset = 256;

        // 按维度配置：保底非空，并清理“全继承”的空条目，避免配置文件膨胀
        if (dimensionRoadSettings == null) {
            dimensionRoadSettings = new HashMap<>();
        } else {
            try {
                dimensionRoadSettings.values().removeIf(v -> v == null || v.isAllInherit());
            } catch (Throwable ignored) {
            }
        }
    }

    // 初始规划半径
    public int initialPlanRadiusChunks() {
        return initialPlanRadiusChunks;
    }

    public void setInitialPlanRadiusChunks(int v) {
        this.initialPlanRadiusChunks = v;
    }

    // 动态规划开关
    public boolean dynamicPlanEnabled() {
        return dynamicPlanEnabled;
    }

    public void setDynamicPlanEnabled(boolean v) {
        this.dynamicPlanEnabled = v;
    }

    // 动态规划半径
    public int dynamicPlanRadiusChunks() {
        return dynamicPlanRadiusChunks;
    }

    public void setDynamicPlanRadiusChunks(int v) {
        this.dynamicPlanRadiusChunks = v;
    }

    // 动态规划触发步进
    public int dynamicPlanStrideChunks() {
        return dynamicPlanStrideChunks;
    }

    public void setDynamicPlanStrideChunks(int v) {
        this.dynamicPlanStrideChunks = v;
    }

    // 道路系统总开关
    public boolean roadsEnabled() {
        return roadsEnabled == null || roadsEnabled;
    }

    public void setRoadsEnabled(boolean v) {
        this.roadsEnabled = v;
    }

    public boolean allowArtificial() {
        return allowArtificial;
    }

    public void setAllowArtificial(boolean v) {
        this.allowArtificial = v;
    }

    public boolean allowNatural() {
        return allowNatural;
    }

    public void setAllowNatural(boolean v) {
        this.allowNatural = v;
    }

    public boolean placeWaypoints() {
        return placeWaypoints;
    }

    public void setPlaceWaypoints(boolean v) {
        this.placeWaypoints = v;
    }

    public boolean spawnCabinEnabled() {
        return spawnCabinEnabled;
    }

    public void setSpawnCabinEnabled(boolean v) {
        this.spawnCabinEnabled = v;
    }

    public int averagingRadius() {
        return averagingRadius;
    }

    public void setAveragingRadius(int v) {
        this.averagingRadius = v;
    }

    public boolean hierarchicalPathfindingEnabled() {
        return hierarchicalPathfindingEnabled;
    }

    public void setHierarchicalPathfindingEnabled(boolean v) {
        this.hierarchicalPathfindingEnabled = v;
    }

    public int generationThreads() {
        return generationThreads;
    }

    public void setGenerationThreads(int v) {
        this.generationThreads = v;
    }

    // 计算线程池线程数（0=自动，>0=固定值）
    public int computeThreads() {
        return computeThreads;
    }

    public void setComputeThreads(int v) {
        this.computeThreads = v;
    }

    public int initialGenerationThreads() {
        return initialGenerationThreads;
    }

    public void setInitialGenerationThreads(int v) {
        this.initialGenerationThreads = v;
    }

    public int maxConcurrentGenerations() {
        return maxConcurrentGenerations;
    }

    public void setMaxConcurrentGenerations(int v) {
        this.maxConcurrentGenerations = v;
    }

    // 线程占空比（1-100%），用于控制CPU使用率
    public int threadDutyCycle() {
        return threadDutyCycle;
    }

    public void setThreadDutyCycle(int v) {
        this.threadDutyCycle = Math.max(1, Math.min(100, v));
    }

    // A* 采样步长
    public int aStarStep() {
        return aStarStep;
    }

    public void setAStarStep(int v) {
        this.aStarStep = v;
    }

    // A* 最大步数
    public int aStarMaxSteps() {
        return aStarMaxSteps;
    }

    public void setAStarMaxSteps(int v) {
        this.aStarMaxSteps = v;
    }

    public int causewayMaxDepth() {
        return causewayMaxDepth;
    }

    public void setCausewayMaxDepth(int v) {
        this.causewayMaxDepth = v;
    }

    public boolean roadFillEnabled() {
        return roadFillEnabled == null || roadFillEnabled;
    }

    public void setRoadFillEnabled(boolean v) {
        this.roadFillEnabled = v;
    }

    public int maxSlopeStepPerTwoSegments() {
        return maxSlopeStepPerTwoSegments;
    }

    public void setMaxSlopeStepPerTwoSegments(int v) {
        this.maxSlopeStepPerTwoSegments = v;
    }

    public boolean slopeLimitEnabled() {
        return slopeLimitEnabled;
    }

    public void setSlopeLimitEnabled(boolean v) {
        this.slopeLimitEnabled = v;
    }

    public PathfindingAlgorithm pathfindingAlgorithm() {
        return pathfindingAlgorithm;
    }

    public void setPathfindingAlgorithm(PathfindingAlgorithm v) {
        this.pathfindingAlgorithm = v;
    }

    // 新增：道路宽度（0=自动）
    public int roadWidth() {
        return roadWidth;
    }

    public void setRoadWidth(int v) {
        this.roadWidth = v;
    }

    // 新增：路牌系统开关
    public boolean roadSignsEnabled() {
        return roadSignsEnabled;
    }

    public void setRoadSignsEnabled(boolean v) {
        this.roadSignsEnabled = v;
    }

    // 新增：高度平滑插值路基填充
    public boolean interpolatedRoadbedFillEnabled() {
        return interpolatedRoadbedFillEnabled == null || interpolatedRoadbedFillEnabled;
    }

    public void setInterpolatedRoadbedFillEnabled(boolean v) {
        this.interpolatedRoadbedFillEnabled = v;
    }

    // 新增：路灯间隔（段）
    public int lampInterval() {
        return lampInterval;
    }

    public void setLampInterval(int v) {
        this.lampInterval = v;
    }

    public int roadClearHeight() {
        return roadClearHeight;
    }

    public void setRoadClearHeight(int v) {
        this.roadClearHeight = v;
    }

    // 路网连边算法
    public PlanningAlgorithm planningAlgorithm() {
        return planningAlgorithm;
    }

    public void setPlanningAlgorithm(PlanningAlgorithm v) {
        this.planningAlgorithm = v;
    }

    // 公路（Highway）配置存取
    public boolean highwayEnabled() {
        return highwayEnabled;
    }

    public void setHighwayEnabled(boolean v) {
        this.highwayEnabled = v;
    }

    public boolean highwayAutoPlanEnabled() {
        return highwayAutoPlanEnabled;
    }

    public void setHighwayAutoPlanEnabled(boolean v) {
        this.highwayAutoPlanEnabled = v;
    }

    public int highwayGridBlocks() {
        return highwayGridBlocks;
    }

    public void setHighwayGridBlocks(int v) {
        this.highwayGridBlocks = v;
    }

    public boolean highwayDynamicPlanEnabled() {
        return highwayDynamicPlanEnabled == null || highwayDynamicPlanEnabled;
    }

    public void setHighwayDynamicPlanEnabled(boolean v) {
        this.highwayDynamicPlanEnabled = v;
    }

    public int highwayPlanningRadiusBlocks() {
        int grid = Math.max(1, highwayGridBlocks);
        // 1x1 cell：覆盖范围约等于 1 * grid；3x3 cell：覆盖范围约等于 2 * grid
        return highwayDynamicPlanEnabled() ? (grid * 2) : grid;
    }

    public int highwayRoadWidth() {
        return highwayRoadWidth;
    }

    public void setHighwayRoadWidth(int v) {
        this.highwayRoadWidth = v;
    }

    public boolean highwaySlopeLimitEnabled() {
        return highwaySlopeLimitEnabled == null || highwaySlopeLimitEnabled;
    }

    public void setHighwaySlopeLimitEnabled(boolean v) {
        this.highwaySlopeLimitEnabled = v;
    }

    public int highwaySlopeRunBlocks() {
        return highwaySlopeRunBlocks;
    }

    public void setHighwaySlopeRunBlocks(int v) {
        this.highwaySlopeRunBlocks = v;
    }

    public int highwaySlopeRiseBlocks() {
        return highwaySlopeRiseBlocks;
    }

    public void setHighwaySlopeRiseBlocks(int v) {
        this.highwaySlopeRiseBlocks = v;
    }

    public int highwayAStarStep() {
        return highwayAStarStep;
    }

    public void setHighwayAStarStep(int v) {
        this.highwayAStarStep = v;
    }

    public int highwayAStarMaxSteps() {
        return highwayAStarMaxSteps;
    }

    public void setHighwayAStarMaxSteps(int v) {
        this.highwayAStarMaxSteps = v;
    }

    public double highwayFloatingWeight() {
        return highwayFloatingWeight;
    }

    public void setHighwayFloatingWeight(double v) {
        this.highwayFloatingWeight = v;
    }

    public double highwayPenetrationWeight() {
        return highwayPenetrationWeight;
    }

    public void setHighwayPenetrationWeight(double v) {
        this.highwayPenetrationWeight = v;
    }

    public boolean tunnelEnabled() {
        return tunnelEnabled;
    }

    public void setTunnelEnabled(boolean v) {
        this.tunnelEnabled = v;
    }

    public int tunnelClearHeight() {
        return tunnelClearHeight;
    }

    public void setTunnelClearHeight(int v) {
        this.tunnelClearHeight = v;
    }

    public boolean preventTreesOnRoad() {
        return preventTreesOnRoad;
    }

    public void setPreventTreesOnRoad(boolean v) {
        this.preventTreesOnRoad = v;
    }

    // 桥梁配置存取
    public boolean bridgeEnabled() {
        return bridgeEnabled;
    }

    public void setBridgeEnabled(boolean v) {
        this.bridgeEnabled = v;
    }

    public int bridgeDeckClearance() {
        return bridgeDeckClearance;
    }

    public void setBridgeDeckClearance(int v) {
        this.bridgeDeckClearance = v;
    }

    public int bridgeMaxLengthBlocks() {
        return bridgeMaxLengthBlocks;
    }

    public void setBridgeMaxLengthBlocks(int v) {
        this.bridgeMaxLengthBlocks = v;
    }

    public boolean bridgeUseBuoysInstead() {
        return bridgeUseBuoysInstead;
    }

    public void setBridgeUseBuoysInstead(boolean v) {
        this.bridgeUseBuoysInstead = v;
    }

    public boolean bridgeUseBuoysWhenSkipped() {
        return bridgeUseBuoysWhenSkipped;
    }

    public void setBridgeUseBuoysWhenSkipped(boolean v) {
        this.bridgeUseBuoysWhenSkipped = v;
    }

    public int buoyIntervalBlocks() {
        return buoyIntervalBlocks;
    }

    public void setBuoyIntervalBlocks(int v) {
        this.buoyIntervalBlocks = v;
    }

    public int bridgePierInterval() {
        return bridgePierInterval;
    }

    public void setBridgePierInterval(int v) {
        this.bridgePierInterval = v;
    }

    public int bridgePierWidth() {
        return bridgePierWidth;
    }

    public void setBridgePierWidth(int v) {
        this.bridgePierWidth = v;
    }

    public int bridgePierMaxHeight() {
        return bridgePierMaxHeight;
    }

    public void setBridgePierMaxHeight(int v) {
        this.bridgePierMaxHeight = v;
    }

    public boolean bridgeKeepLamps() {
        return bridgeKeepLamps;
    }

    public void setBridgeKeepLamps(boolean v) {
        this.bridgeKeepLamps = v;
    }

    public int bridgeRampSegments() {
        return bridgeRampSegments;
    }

    public void setBridgeRampSegments(int v) {
        this.bridgeRampSegments = v;
    }

    public int bridgeMinWaterDepth() {
        return bridgeMinWaterDepth;
    }

    public void setBridgeMinWaterDepth(int v) {
        this.bridgeMinWaterDepth = v;
    }

    public int bridgeMinLength() {
        return bridgeMinLength;
    }

    public void setBridgeMinLength(int v) {
        this.bridgeMinLength = v;
    }

    public int bridgeMergeGap() {
        return bridgeMergeGap;
    }

    public void setBridgeMergeGap(int v) {
        this.bridgeMergeGap = v;
    }

    // A* 寻路成本权重
    public double orthoStepCost() {
        return orthoStepCost;
    }

    public void setOrthoStepCost(double v) {
        this.orthoStepCost = v;
    }

    public double diagStepCost() {
        return diagStepCost;
    }

    public void setDiagStepCost(double v) {
        this.diagStepCost = v;
    }

    public int elevationWeight() {
        return elevationWeight;
    }

    public void setElevationWeight(int v) {
        this.elevationWeight = v;
    }

    public int biomeWeight() {
        return biomeWeight;
    }

    public void setBiomeWeight(int v) {
        this.biomeWeight = v;
    }

    public int stabilityWeight() {
        return stabilityWeight;
    }

    public void setStabilityWeight(int v) {
        this.stabilityWeight = v;
    }

    public int waterDepthWeight() {
        return waterDepthWeight;
    }

    public void setWaterDepthWeight(int v) {
        this.waterDepthWeight = v;
    }

    public int nearWaterCost() {
        return nearWaterCost;
    }

    public void setNearWaterCost(int v) {
        this.nearWaterCost = v;
    }

    public int waterProximityCost() {
        return waterProximityCost;
    }

    public void setWaterProximityCost(int v) {
        this.waterProximityCost = v;
    }

    public double heuristicWeight() {
        return heuristicWeight;
    }

    public void setHeuristicWeight(double v) {
        this.heuristicWeight = v;
    }

    public double deviationWeight() {
        return deviationWeight;
    }

    public void setDeviationWeight(double v) {
        this.deviationWeight = v;
    }

    // 测试栏配置存取
    public boolean loadingTipsEnabled() {
        return loadingTipsEnabled;
    }

    public void setLoadingTipsEnabled(boolean v) {
        this.loadingTipsEnabled = v;
    }

    // 路边结构配置存取
    public boolean roadsideStructuresEnabled() {
        return roadsideStructuresEnabled;
    }

    public void setRoadsideStructuresEnabled(boolean v) {
        this.roadsideStructuresEnabled = v;
    }

    public int maxStructuresPerRoad() {
        return maxStructuresPerRoad;
    }

    public void setMaxStructuresPerRoad(int v) {
        this.maxStructuresPerRoad = Math.max(0, v);
    }

    public int smallStructureOffset() {
        return smallStructureOffset;
    }

    public void setSmallStructureOffset(int v) {
        this.smallStructureOffset = Math.max(1, v);
    }

    public int mediumStructureOffset() {
        return mediumStructureOffset;
    }

    public void setMediumStructureOffset(int v) {
        this.mediumStructureOffset = Math.max(1, v);
    }

    public int largeStructureOffset() {
        return largeStructureOffset;
    }

    public void setLargeStructureOffset(int v) {
        this.largeStructureOffset = Math.max(1, v);
    }

    // 结构距离控制存取
    public int villageRoadOffset() {
        return villageRoadOffset;
    }

    public void setVillageRoadOffset(int v) {
        this.villageRoadOffset = Math.max(0, Math.min(256, v));
    }

    public int otherStructureRoadOffset() {
        return otherStructureRoadOffset;
    }

    public void setOtherStructureRoadOffset(int v) {
        this.otherStructureRoadOffset = Math.max(0, Math.min(256, v));
    }

    public boolean structureAvoidanceEnabled() {
        return structureAvoidanceEnabled;
    }

    public void setStructureAvoidanceEnabled(boolean v) {
        this.structureAvoidanceEnabled = v;
    }

    @Deprecated
    public int structureRoadOffset() {
        return villageRoadOffset;
    }

    @Deprecated
    public void setStructureRoadOffset(int v) {
        this.villageRoadOffset = Math.max(0, Math.min(256, v));
    }

    public Map<String, DimensionRoadSettings> dimensionRoadSettings() {
        return dimensionRoadSettings;
    }

    public void setDimensionRoadSettings(Map<String, DimensionRoadSettings> v) {
        this.dimensionRoadSettings = (v == null) ? new HashMap<>() : new HashMap<>(v);
        try {
            this.dimensionRoadSettings.values().removeIf(s -> s == null || s.isAllInherit());
        } catch (Throwable ignored) {
        }
    }

    private DimensionRoadSettings getDimensionRoadSettingsInternal(String dimensionId) {
        if (dimensionId == null || dimensionId.isEmpty())
            return null;
        if (dimensionRoadSettings == null || dimensionRoadSettings.isEmpty())
            return null;
        return dimensionRoadSettings.get(dimensionId);
    }

    private static boolean chooseBool(Boolean override, boolean globalValue) {
        return override != null ? override : globalValue;
    }

    public DimensionRoadSettings getOrCreateDimensionRoadSettings(String dimensionId) {
        if (dimensionId == null || dimensionId.isEmpty())
            return null;
        if (dimensionRoadSettings == null)
            dimensionRoadSettings = new HashMap<>();
        return dimensionRoadSettings.computeIfAbsent(dimensionId, k -> new DimensionRoadSettings());
    }

    public void removeDimensionRoadSettingsIfAllInherit(String dimensionId) {
        if (dimensionId == null || dimensionId.isEmpty())
            return;
        if (dimensionRoadSettings == null)
            return;
        DimensionRoadSettings s = dimensionRoadSettings.get(dimensionId);
        if (s != null && s.isAllInherit()) {
            dimensionRoadSettings.remove(dimensionId);
        }
    }

    // -------- 维度优先、全局兜底：effective 读取方法 --------

    public boolean roadsEnabledForDimension(String dimensionId) {
        DimensionRoadSettings s = getDimensionRoadSettingsInternal(dimensionId);
        return chooseBool(s == null ? null : s.roadsEnabled(), roadsEnabled());
    }

    public boolean bridgeEnabledForDimension(String dimensionId) {
        DimensionRoadSettings s = getDimensionRoadSettingsInternal(dimensionId);
        return chooseBool(s == null ? null : s.bridgeEnabled(), bridgeEnabled());
    }

    public PathfindingAlgorithm pathfindingAlgorithmForDimension(String dimensionId) {
        DimensionRoadSettings s = getDimensionRoadSettingsInternal(dimensionId);
        PathfindingAlgorithm v = (s == null) ? null : s.pathfindingAlgorithm();
        return v != null ? v : pathfindingAlgorithm();
    }

    public boolean roadFillEnabledForDimension(String dimensionId) {
        DimensionRoadSettings s = getDimensionRoadSettingsInternal(dimensionId);
        // roadFillEnabled 只控制“RoadTerrainAdapter 路基/地形适配”这部分。
        return chooseBool(s == null ? null : s.roadFillEnabled(), roadFillEnabled());
    }

    public boolean slopeLimitEnabledForDimension(String dimensionId) {
        DimensionRoadSettings s = getDimensionRoadSettingsInternal(dimensionId);
        return chooseBool(s == null ? null : s.slopeLimitEnabled(), slopeLimitEnabled());
    }

    public boolean highwayEnabledForDimension(String dimensionId) {
        DimensionRoadSettings s = getDimensionRoadSettingsInternal(dimensionId);
        return chooseBool(s == null ? null : s.highwayEnabled(), highwayEnabled());
    }

    public boolean roadsideStructuresEnabledForDimension(String dimensionId) {
        DimensionRoadSettings s = getDimensionRoadSettingsInternal(dimensionId);
        return chooseBool(s == null ? null : s.roadsideStructuresEnabled(), roadsideStructuresEnabled());
    }

    public boolean roadSignsEnabledForDimension(String dimensionId) {
        DimensionRoadSettings s = getDimensionRoadSettingsInternal(dimensionId);
        return chooseBool(s == null ? null : s.roadSignsEnabled(), roadSignsEnabled());
    }

    public boolean interpolatedRoadbedFillEnabledForDimension(String dimensionId) {
        DimensionRoadSettings s = getDimensionRoadSettingsInternal(dimensionId);
        return chooseBool(s == null ? null : s.interpolatedRoadbedFillEnabled(), interpolatedRoadbedFillEnabled());
    }
}
