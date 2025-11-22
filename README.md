# RoadWeaver
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/6jk8Pote?style=flat-square&logo=Modrinth&label=Modrinth)](https://modrinth.com/mod/roadweaver)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1358489?style=flat-square&logo=CurseForge&label=CurseForge&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/roadweaver)
[![Discord Invite](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white&style=flat-square)](https://discord.gg/tUJMJkbbr2)
[![Github Stars](https://img.shields.io/github/stars/shiroha-233/RoadWeaver?logo=github&style=flat-square)](https://github.com/shiroha-233/RoadWeaver)

English | [简体中文](README_CN.md)

### Overview

An automatic road generation mod based on [Countered's Settlement Roads](https://modrinth.com/mod/countereds-settlement-roads), supporting Fabric/Forge, currently targeting Minecraft version 1.20.1.

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
