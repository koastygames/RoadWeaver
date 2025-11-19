# RoadWeaver
 
<p align="center">
  <a href="https://modrinth.com/mod/roadweaver">
    <img src="https://img.shields.io/modrinth/dt/roadweaver?logo=modrinth&label=Modrinth&color=1bd96a&style=flat-square" alt="Modrinth downloads">
  </a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/roadweaver">
    <img src="https://img.shields.io/curseforge/dt/1358489?logo=curseforge&label=CurseForge&color=f16436&style=flat-square" alt="CurseForge downloads">
  </a>
  <a href="https://discord.gg/tUJMJkbbr2">
    <img src="https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white&style=flat-square" alt="Discord">
  </a>
  <a href="https://github.com/shiroha-233/RoadWeaver">
    <img src="https://img.shields.io/github/stars/shiroha-233/RoadWeaver?logo=github&style=flat-square" alt="GitHub stars">
  </a>
</p>

 [English](#english) | [简体中文](#简体中文)

## English

### Overview

RoadWeaver automatically generates roads between vanilla and modded structures (such as villages and outposts) on top of existing terrain. It is built as a cross‑loader Architectury mod targeting **Minecraft 1.20.1** (Fabric & Forge).

Compared to earlier versions, the structure predictor, road network planner, pathfinding core and road generator have all been refactored with a strong focus on performance and stability.

### Project Status

- Focus on stabilizing and polishing the **1.20.1** release.
- Finalizing data formats and configuration layout.
- Ports to **1.21.x** will happen **after** the 1.20.1 line is considered stable.

The mod is still under active development. Current versions should be seen as a solid foundation rather than a feature‑complete end state.

### Key Features (English)

- **Seed‑based structure predictor**
  - Predicts many structure locations from the world seed and noise, instead of spamming `/locate` commands.
  - Supports tag‑based whitelist/blacklist and biome pre‑filtering.
- **Road network planners**
  - KNN, Delaunay and RNG graph algorithms with different density and style:
    - KNN: sparse, tree‑like networks.
    - Delaunay: very dense, web‑like networks (can easily overlap in highly developed regions).
    - RNG: in‑between, grid‑like networks – the default and generally recommended.
- **Pathfinding (A* + bidirectional A*)**
  - Configurable step size (4–128 blocks); smaller steps are more precise but slower – 8–16 is a good balance.
  - Configurable concurrency: number of simultaneous generations and worker threads.
  - Rich cost weights: elevation, biome, stability, water depth, near‑water cost, heuristic weight, deviation weight, etc.
  - Optional **bidirectional A*** for more efficient long‑distance searches.
  - During road generation, polyline paths are smoothed with **Bezier interpolation** to turn sharp angles into natural‑looking curves.
- **Map UI**
  - Default hotkey **`H`** opens a medieval‑style world map.
  - Supports zooming, panning, grid display.
  - Shows structure nodes and road states (planned / generating / completed / failed).
  - Provides a context menu (for operators): safe teleportation and manual connection tools.
- **Terrain‑aware road generation**
  - Supports cutting and filling terrain to avoid broken roads across ravines or steep slopes.
  - Configurable road width, clearance height, lamp placement and spacing.
  - Bridges: clearance, railings, pier interval/width/max height, lamp preservation, ramps, etc.
  - Tunnels and tunnel clearance settings.
  - Whole‑tree removal around the road with strict limits on radius/height/block count to avoid over‑removal.
- **Road material presets**
  - All artificial road materials are driven by presets under `config/roadweaver/presets/*.json`.
  - Multiple material combinations and weights are supported.
  - An in‑game preset editor allows adding/removing/editing presets without manually writing JSON.
- **Dynamic planning & persistence**
  - Initial blocking plan around spawn with configurable radius.
  - Dynamic, player‑centric incremental planning controlled by radius and stride.
  - All planning results and generation states are persisted via `WorldDataProvider` and shard storage so they survive restarts.

### Performance & Compatibility (English)

- RoadWeaver no longer relies on blocking `/locate` commands; structure prediction and planning run on worker threads, with minimal main‑thread impact.
- Performance is mainly affected by terrain complexity, A* step size and configured concurrency.
- Terrain mods **without height caching** (e.g. Tectonic, Terralith) can make height sampling extremely expensive, leading to **very slow** road generation and map rendering.
- Terrain mods **with height caching**, such as **ReTerraForged**, are strongly recommended for best performance.

### Basic Workflow (English)

1. Install RoadWeaver plus required libraries (Cloth Config, Architectury).
2. Create a new world. On first load, RoadWeaver performs a blocking initial planning pass around spawn within the configured radius. Do **not** set this radius too large, or world creation will take a long time.
3. After entering the world, background threads keep planning and generating roads dynamically around players according to the configured dynamic radius and stride.
4. Press **H** to open the map to inspect structures, planned edges and generated roads; operators can optionally teleport or manually connect nodes from this screen.

## 简体中文

一个基于 Countered's Settlement Roads 重构的自动道路生成模组，支持 Fabric / Forge，当前目标版本为 Minecraft 1.20.1。

### 项目状态

本项目已经过初步重构，核心结构搜寻、路网规划、寻路和道路铺设逻辑都已重写，重点优化了性能和稳定性。

当前主要精力是：

- 完善 1.20.1 版本的功能与兼容性
- 稳定世界数据格式与配置系统
- 在此基础上再进行 1.21.x 的版本移植

目前模组仍在完善中，现在也只是打好基础，还未开始添加更多功能，敬请期待。


### 主要特性

### 1. 基于种子的结构搜寻器

1. 结构搜寻器：抛弃原版 `/locate` 指令搜寻，改用依据**地图种子与噪声**进行反推，一次性推算出大量结构点。
  - 支持**结构标签白名单 / 黑名单**，并可搭配**群系预筛**使用。
  - 优点：几乎不占用主线程性能，可随意添加任意数量的结构标签，兼容大部分第三方结构和地形模组.
  - 缺点：虽已新增验证功能，但仍有小概率出现“假结构点”。

### 2. 路网规划器

提供三种路网规划算法，可在配置中切换：

- **KNN（最近邻）**：
  - 路网非常稀疏，类似树状结构;
  - 缺点是路网连接性较差，更适合“乡间小路”风格.
- **Delaunay（三角剖分）**:
  - 路网非常密集，类似蜘蛛网;
  - 缺点是过于密集，适合极度发达，道路容易出现重叠.
- **RNG（随机邻域图）**:
  - 路网密度介于两者之间，整体呈网格状;
  - 综合效果最好，**默认与最推荐的算法**.

### 3. 寻路算法（A*）

1. 寻路算法采用 **A***和**双向 A***，可调整若干关键参数：

- **步长（Step）**：4–128 方块，步长越低精度越高但越慢;
  - 实测推荐范围 **8–16**，在精度和性能之间较为平衡;
- **同时生成数量**与**线程池大小**可配置（默认都为 3 线程）;
- 支持多种权重调节：高度变化、群系、地形稳定性、水深、贴水成本、启发式权重、偏离权重等;
- 支持**双向 A***（可选），进一步提高长距离寻路的效率.
- 在道路生成阶段，会对寻路得到的折线路径应用**贝塞尔曲线平滑插值**，将其变为更自然的平滑曲线，减少“机械感”和锐角拐弯.


强烈推荐搭配 **ReTerraForged** 这类自带高度缓存、地形平滑的地形模组使用，可以显著降低寻路成本、提升生成速度。

### 4. 地图与可视化

1. 地图系统完全重构：

- 默认按 **`H` 键** 打开世界地图，采用**中世纪纸质地图**风格;
- 支持缩放、拖动、网格显示等;
- 显示结构节点、规划中/已完成/失败的道路状态;
- 提供地图右键菜单（在有权限时）：
  - **地图传送**：传送前先采样目标点高度，避免卡在方块内部;
  - **手动连接模式**：可手工指定起点/终点补路.

地图传送功能默认只在**作弊模式 / 具有相应权限**时可用，生存普通玩家无法滥用.

### 5. 道路铺设与地形适配

1. 道路铺设系统：

- 支持**地形切削与填补**，避免峡谷和陡坡导致的道路铺设异常;
- 可配置道路宽度、净空高度、是否生成路灯以及灯间距;
- 支持**桥梁与桥墩**：
  - 可开关桥梁功能;
  - 可配置桥面净空高度、桥栏、桥墩间距与宽度、最大桥墩高度、是否保留路灯、引桥长度等;
- 支持**隧道模式**与净空高度配置;
- 支持沿路**整棵树移除**，并限制最大半径 / 高度 / 方块数，防止误删大片地形.

2. 添加**道路材质预设**功能，可快速配置道路材质，并保存为预设，下次使用可快速选择预设.

- 所有“人工道路”材质由 `config/roadweaver/presets/*.json` 控制;
- 可配置多套材质组合与权重，例如：
  - Stone Street（石砖 + 磨制安山岩）
  - Mud Road（泥路）
  - Aged Stone（风化石砖等）
- 游戏内提供**预设编辑器**，可直接增删改材质组合，无需手写 JSON.

### 6. 动态规划与持久化

- 支持**新建世界初始规划**：
  - 以出生点为中心，根据配置的“初始规划半径（区块）”一次性规划大区域路网;
  - 该过程是单线程的，会在进入世界前阻塞一段时间;
  - **不要把初始规划半径设置得过大**，否则首次进入世界会非常慢.
- 支持**基于玩家位置的动态增量规划**：
  - 以玩家为中心的动态规划半径与步进（stride）可配置;
  - 玩家移动到新网格时才触发新的规划任务;
- 所有规划结果与道路生成状态通过 `WorldDataProvider` 与分片存储系统持久化，服务器重启后可恢复.

## 性能与兼容性

- 新版完全抛弃了旧版依赖的原版指令 `/locate` 搜索机制，不会长时间阻塞游戏主线程;
- 结构预测、路网规划和寻路均在**专用线程池**中执行，主线程只负责驱动与结果应用;
- 性能主要受以下因素影响：
  - 世界地形复杂度（尤其是极端地形数据包 / 模组）;
  - 寻路步长、权重配置;
  - **同时生成数量**与**线程池数量**（不建议开太大避免掉帧）。

已知兼容性与性能问题：

- 模组与 **Tectonic、Terralith 等不自带高度缓存的地形模组** 一起使用时：
  - 为了铺路与绘制地图，RoadWeaver 需要大量采样高度;
  - 这些模组没有内建高度缓存时，采样会非常昂贵;
  - 具体表现为：
    - 道路生成**极为缓慢**;
    - 地图数据加载缓慢甚至长时间空白.
- 建议方案：
  - 使用 **ReTerraForged** 等带高度缓存的地形模组平替;
  - 或在这类整合包中适当降低 RoadWeaver 的规划/生成范围与线程数，必要时关闭本模组.

从实现上看，RoadWeaver 并未直接修改原版的核心机制，因此理论上与大多数模组是兼容的。之前版本容易崩溃主要还是旧搜索机制造成的主线程阻塞.

## 基本使用流程（概览）

1. 安装本模组及必要前置（Cloth Config / Architectury）.
2. 创建新世界：
   - 在世界首次加载时，RoadWeaver 会根据配置的**初始规划半径**做一次全局路网规划;
   - 该过程是单线程的，会在进入世界前阻塞一段时间;
   - **不要把初始规划半径设置得过大**，否则首次进入世界会非常慢.
3. 进入游戏后：
   - 模组会按配置的“动态规划半径”和“步进”围绕玩家进行增量规划;
   - 后台线程持续生成/更新道路.
4. 按 **H 键** 打开地图：
   - 查看已检测到的结构、规划中的边和已生成的道路;
   - 在有权限的情况下，可使用地图传送与手动连接功能.

## 常见问题（FAQ）

1. **出生在村庄附近时，看到道路中断？**

   已实现新建世界的阻塞功能：会先在进入世界前加载玩家设置的初始规划半径。但这个阶段是单线程的，如果半径设置过大，加载会非常慢。建议：

   - 初次尝试时先用较小的半径测试效果;
   - 在确认性能可接受后再逐步调大.

2. **传送（TP）会导致道路生成中断吗？**

   目前仍然**会有影响**，因为还没有对区块生成做严格的阻塞控制。快速跨越大距离 TP 可能让部分区域暂时来不及规划和铺设道路，后续会考虑改进这一点.

3. **新版性能怎么样，还会掉 TPS 吗？**

   旧版之所以卡顿、掉 TPS、甚至崩溃，主要原因是大量使用原版指令搜索结构：

   - 结构标签越多，搜索越慢;
   - `/locate` 会严重阻塞主线程.

   重构后的版本：

   - 完全抛弃了这套机制，改为基于种子的结构预测;
   - 几乎不占用主线程时间，性能有大幅提升;
   - 但仍然**不建议把道路生成线程数和并发数量调得太大**，否则可能造成帧率波动.

4. **地图传送还会卡在方块里吗？**

   不会。地图传送已经改为：

   - 先采样目标点的高度;
   - 再把玩家传送到安全高度;
   - 并为传送功能添加了使用权限限制，非作弊模式玩家默认无法使用.

5. **1.21.x 版本计划？**

   计划是：

   - 先把 1.20.1 版本的重构和功能完善好;
   - 等核心逻辑稳定后，再开始移植到 1.21.x.

6. **整体兼容性如何？**

   - 模组没有直接篡改原版的核心机制，大部分情况下与其他模组是兼容的;
   - 实测在几个大型整合包（例如乌托邦、ATM9）中并未出现严重报错;
   - 但部分整合包加入了许多“奇形怪状”的地形数据包/模组，导致地形极其混乱，这会影响道路生成的速度和效果.
