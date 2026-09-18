# RoadWeaver 3.1.0

## Generation and performance

- Added bounded biome + terrain-signature road material caching.
- Road palettes sample surrounding terrain as well as local terrain layers.
- Reduced repeated whole-road height projection work during paving.
- Added segment-local height projection for slope/slab checks.
- Added defensive interpolation handling for invalid or incomplete road height data.

## Compatibility

- Keeps modded biome/worldgen support dependency-free by using active generated terrain as material candidates.
- Retains filtering for fluids, vegetation, snow and other unsuitable terrain blocks.

## Version

RoadWeaver 3.1.0 targets Minecraft 26.2 Fabric and Java 25.
