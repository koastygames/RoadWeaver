# WorldWeaver — NeoForge 1.21.1

WorldWeaver is a compatibility-first chunk-generation optimization layer for Minecraft 1.21.1 NeoForge.

## Pipeline design

Minecraft 1.21.1 separates structure *planning* from structure *block placement*. Structure starts are created early because terrain adaptation, structure references, `/locate`, jigsaw systems and many structure mods depend on them. The structure blocks themselves are placed during biome decoration, after noise terrain, surface building and carving.

WorldWeaver preserves this contract rather than replacing it with a private scheduler that would break other worldgen mods. It accelerates repeated base-height queries in vanilla `NoiseBasedChunkGenerator`, keeps generation asynchronous, and leaves all biome/structure/placed-feature registry contents intact.

## Low-GC height cache

Repeated structure and feature placement can ask the noise generator for the same terrain heights many times. WorldWeaver uses a fixed-size, primitive direct-mapped cache split by heightmap type. It stores the full packed X/Z key, performs race-safe double verification on concurrent reads, allocates no key objects on lookups, never grows, and invalidates in O(1) when the `RandomState` changes. This avoids the allocation pressure and bulk-clear spikes of a general-purpose map cache.

## Compatibility policy

- No hard-coded biome IDs.
- No hard-coded structure IDs.
- No replacement biome source.
- No replacement chunk generator.
- No forced world preset.
- No custom chunk executor or thread pool.
- Custom/modded chunk generators are left untouched unless they invoke vanilla `ChunkGenerator` decoration methods.
- Datapack worldgen remains registry-driven.

This is intentionally safer than globally reordering `ChunkStatus`, which would violate assumptions used by vanilla and many mods.

## JVM tuning switches

- `-Dworldweaver.disableHeightCache=true` — disable the base-height cache.
- `-Dworldweaver.heightCacheMaxEntries=65536` — approximate total fixed cache capacity requested per vanilla noise generator (1024–1048576).
- `-Dworldweaver.logSlowWorldgen=true` — opt in to slow-phase logging.
- `-Dworldweaver.slowWorldgenMs=250` — slow phase threshold in milliseconds (25–60000).
