# RoadWeaver 3.1.0

## Generation and performance

- Added a bounded biome + terrain-signature road material cache.
- Terrain palettes now sample north, south, east and west neighbours as well as the local terrain layers.
- Prevents a single sampled location from permanently determining the material palette for an entire biome.
- Added segment-local road height projection for per-block slope/slab checks.
- Reduced repeated whole-road projection scans during paving.

## Compatibility

- Keeps modded biome/worldgen support dependency-free by using the active generated terrain as material candidates.
- Retains safe filtering for fluids, vegetation, snow and other unsuitable terrain blocks.

## Version

RoadWeaver 3.1.0 targets Minecraft 26.2 Fabric and Java 25.
