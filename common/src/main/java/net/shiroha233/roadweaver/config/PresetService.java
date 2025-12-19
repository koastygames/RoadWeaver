package net.shiroha233.roadweaver.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class PresetService {
    private static final Logger LOGGER = LoggerFactory.getLogger("roadweaver");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BASE_DIR = "roadweaver";
    private static final String PRESET_DIR = "presets";
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft:overworld");

    public enum RoadType {
        ARTIFICIAL,
        NATURAL
    }

    private static final AtomicReference<Map<String, PresetDef>> PRESETS = new AtomicReference<>(new LinkedHashMap<>());
    private static final List<SamplePresetTemplate> SAMPLE_PRESETS;

    static {
        List<SamplePresetTemplate> list = new ArrayList<>();
        // ===== 人工道路 =====
        list.add(new SamplePresetTemplate(
                "stone_street",
                "Stone Street",
                RoadType.ARTIFICIAL,
                List.of(OVERWORLD),
                List.of("minecraft:stone_bricks", "minecraft:polished_andesite"),
                List.of("minecraft:stone_brick_slab", "minecraft:polished_andesite_slab")
        ));
        list.add(new SamplePresetTemplate(
                "mud_road",
                "Mud Road",
                RoadType.ARTIFICIAL,
                List.of(OVERWORLD),
                List.of("minecraft:mud_bricks", "minecraft:packed_mud"),
                List.of("minecraft:mud_brick_slab")
        ));
        list.add(new SamplePresetTemplate(
                "aged_stone",
                "Aged Stone",
                RoadType.ARTIFICIAL,
                List.of(OVERWORLD),
                List.of("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"),
                List.of("minecraft:stone_brick_slab", "minecraft:mossy_stone_brick_slab")
        ));

        // ===== 自然道路（按生物群系划分）=====
        list.add(naturalPreset("plains", "Plains Trail",
                List.of("minecraft:dirt_path", "minecraft:gravel", "minecraft:coarse_dirt"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("sunflower_plains", "Sunflower Plains Trail",
                List.of("minecraft:dirt_path", "minecraft:gravel", "minecraft:coarse_dirt"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("meadow", "Meadow Trail",
                List.of("minecraft:dirt_path", "minecraft:moss_block", "minecraft:coarse_dirt"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("forest", "Forest Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("flower_forest", "Flower Forest Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:moss_block"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("birch_forest", "Birch Forest Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:gravel"),
                List.of("minecraft:birch_slab")));
        list.add(naturalPreset("old_growth_birch_forest", "Old Growth Birch Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
                List.of("minecraft:birch_slab")));
        list.add(naturalPreset("dark_forest", "Dark Forest Trail",
                List.of("minecraft:coarse_dirt", "minecraft:rooted_dirt", "minecraft:podzol"),
                List.of("minecraft:dark_oak_slab")));
        list.add(naturalPreset("cherry_grove", "Cherry Grove Path",
                List.of("minecraft:dirt_path", "minecraft:moss_block", "minecraft:pink_petals"),
                List.of("minecraft:cherry_slab")));
        list.add(naturalPreset("taiga", "Taiga Trail",
                List.of("minecraft:coarse_dirt", "minecraft:podzol", "minecraft:gravel"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("old_growth_pine_taiga", "Old Growth Pine Trail",
                List.of("minecraft:podzol", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("old_growth_spruce_taiga", "Old Growth Spruce Trail",
                List.of("minecraft:podzol", "minecraft:coarse_dirt", "minecraft:rooted_dirt"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("snowy_taiga", "Snowy Taiga Trail",
                List.of("minecraft:snow_block", "minecraft:coarse_dirt", "minecraft:gravel"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("snowy_plains", "Snowy Plains Trail",
                List.of("minecraft:snow_block", "minecraft:packed_ice", "minecraft:gravel"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("ice_spikes", "Ice Spikes Trail",
                List.of("minecraft:packed_ice", "minecraft:blue_ice", "minecraft:snow_block"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("snowy_slopes", "Snowy Slopes Trail",
                List.of("minecraft:snow_block", "minecraft:powder_snow", "minecraft:stone"),
                List.of("minecraft:stone_slab")));
        list.add(naturalPreset("frozen_peaks", "Frozen Peaks Trail",
                List.of("minecraft:packed_ice", "minecraft:snow_block", "minecraft:stone"),
                List.of("minecraft:stone_slab")));
        list.add(naturalPreset("grove", "Grove Trail",
                List.of("minecraft:snow_block", "minecraft:powder_snow", "minecraft:dirt_path"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("windswept_hills", "Windswept Hills Trail",
                List.of("minecraft:stone", "minecraft:gravel", "minecraft:cobblestone"),
                List.of("minecraft:stone_slab", "minecraft:cobblestone_slab")));
        list.add(naturalPreset("windswept_gravelly_hills", "Windswept Gravelly Trail",
                List.of("minecraft:gravel", "minecraft:stone", "minecraft:cobblestone"),
                List.of("minecraft:stone_slab")));
        list.add(naturalPreset("windswept_forest", "Windswept Forest Trail",
                List.of("minecraft:coarse_dirt", "minecraft:stone", "minecraft:gravel"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("stony_peaks", "Stony Peaks Trail",
                List.of("minecraft:stone", "minecraft:calcite", "minecraft:andesite"),
                List.of("minecraft:stone_slab", "minecraft:andesite_slab")));
        list.add(naturalPreset("jagged_peaks", "Jagged Peaks Trail",
                List.of("minecraft:stone", "minecraft:snow_block", "minecraft:packed_ice"),
                List.of("minecraft:stone_slab")));
        list.add(naturalPreset("desert", "Desert Trail",
                List.of("minecraft:sandstone", "minecraft:smooth_sandstone", "minecraft:sand"),
                List.of("minecraft:sandstone_slab", "minecraft:smooth_sandstone_slab")));
        list.add(naturalPreset("badlands", "Badlands Trail",
                List.of("minecraft:red_sand", "minecraft:terracotta", "minecraft:orange_terracotta"),
                List.of("minecraft:red_sandstone_slab")));
        list.add(naturalPreset("eroded_badlands", "Eroded Badlands Trail",
                List.of("minecraft:red_sand", "minecraft:terracotta", "minecraft:red_terracotta"),
                List.of("minecraft:red_sandstone_slab")));
        list.add(naturalPreset("wooded_badlands", "Wooded Badlands Trail",
                List.of("minecraft:coarse_dirt", "minecraft:terracotta", "minecraft:red_sand"),
                List.of("minecraft:red_sandstone_slab", "minecraft:oak_slab")));
        list.add(naturalPreset("jungle", "Jungle Trail",
                List.of("minecraft:dirt_path", "minecraft:rooted_dirt", "minecraft:mud"),
                List.of("minecraft:jungle_slab")));
        list.add(naturalPreset("sparse_jungle", "Sparse Jungle Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:gravel"),
                List.of("minecraft:jungle_slab")));
        list.add(naturalPreset("bamboo_jungle", "Bamboo Jungle Trail",
                List.of("minecraft:dirt_path", "minecraft:podzol", "minecraft:rooted_dirt"),
                List.of("minecraft:bamboo_slab", "minecraft:jungle_slab")));
        list.add(naturalPreset("swamp", "Swamp Path",
                List.of("minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:dirt_path"),
                List.of("minecraft:mud_brick_slab", "minecraft:oak_slab")));
        list.add(naturalPreset("mangrove_swamp", "Mangrove Swamp Path",
                List.of("minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:mangrove_roots"),
                List.of("minecraft:mud_brick_slab", "minecraft:mangrove_slab")));
        list.add(naturalPreset("savanna", "Savanna Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:gravel"),
                List.of("minecraft:acacia_slab")));
        list.add(naturalPreset("savanna_plateau", "Savanna Plateau Trail",
                List.of("minecraft:dirt_path", "minecraft:coarse_dirt", "minecraft:stone"),
                List.of("minecraft:acacia_slab")));
        list.add(naturalPreset("windswept_savanna", "Windswept Savanna Trail",
                List.of("minecraft:coarse_dirt", "minecraft:stone", "minecraft:gravel"),
                List.of("minecraft:acacia_slab", "minecraft:stone_slab")));
        list.add(naturalPreset("beach", "Beach Path",
                List.of("minecraft:sand", "minecraft:sandstone", "minecraft:gravel"),
                List.of("minecraft:sandstone_slab")));
        list.add(naturalPreset("snowy_beach", "Snowy Beach Path",
                List.of("minecraft:snow_block", "minecraft:sand", "minecraft:gravel"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("stony_shore", "Stony Shore Path",
                List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:gravel"),
                List.of("minecraft:stone_slab", "minecraft:cobblestone_slab")));
        list.add(naturalPreset("river", "River Bank Path",
                List.of("minecraft:dirt_path", "minecraft:gravel", "minecraft:sand"),
                List.of("minecraft:oak_slab")));
        list.add(naturalPreset("frozen_river", "Frozen River Path",
                List.of("minecraft:packed_ice", "minecraft:gravel", "minecraft:snow_block"),
                List.of("minecraft:spruce_slab")));
        list.add(naturalPreset("mushroom_fields", "Mushroom Fields Path",
                List.of("minecraft:mycelium", "minecraft:dirt_path", "minecraft:coarse_dirt"),
                List.of("minecraft:oak_slab")));

        SAMPLE_PRESETS = List.copyOf(list);
    }

    private static SamplePresetTemplate naturalPreset(String biomeId, String displayName, List<String> materials, List<String> slabMaterials) {
        String sanitizedId = biomeId.contains(":")
                ? biomeId.replace(':', '_')
                : biomeId;
        String presetId = "natural_" + sanitizedId;
        return new SamplePresetTemplate(
                presetId,
                displayName,
                RoadType.NATURAL,
                List.of(OVERWORLD),
                List.copyOf(materials),
                List.copyOf(slabMaterials)
        );
    }

    private PresetService() {}

    public static synchronized void reload() {
        Path cfgRoot = Platform.getConfigFolder();
        Path baseDir = cfgRoot.resolve(BASE_DIR);
        Path presetDir = baseDir.resolve(PRESET_DIR);
        Map<String, PresetDef> map = new LinkedHashMap<>();
        try {
            try {
                Files.createDirectories(presetDir);
            } catch (Exception e) {
                LOGGER.warn("Failed to create preset directory: {}", presetDir, e);
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(presetDir, "*.json")) {
                for (Path p : ds) {
                    PresetFile dto = readPresetFile(p);
                    if (dto == null) continue;
                    String id = dto.id;
                    if (id == null || id.isBlank()) id = stripExt(p.getFileName().toString());
                    
                    List<String> mats = dto.materials != null ? dto.materials : List.of();
                    List<String> valid = new ArrayList<>();
                    for (String s : mats) {
                        try {
                            ResourceLocation rl = new ResourceLocation(s);
                            Block b = BuiltInRegistries.BLOCK.get(rl);
                            if (b != null && b != Blocks.AIR) valid.add(s);
                        } catch (Throwable ignored) {}
                    }
                    
                    List<String> slabIds = dto.slabMaterials != null ? dto.slabMaterials : List.of();
                    List<String> validSlabs = new ArrayList<>();
                    for (String s : slabIds) {
                        try {
                            ResourceLocation rl = new ResourceLocation(s);
                            Block b = BuiltInRegistries.BLOCK.get(rl);
                            if (b != null && b != Blocks.AIR) validSlabs.add(s);
                        } catch (Throwable ignored) {}
                    }
                    
                    if (valid.isEmpty()) {
                        LOGGER.warn("Skip preset {} due to empty/invalid materials", p.getFileName());
                        continue;
                    }

                    String name = dto.name == null || dto.name.isBlank() ? id : dto.name;
                    
                    // Parse RoadType
                    RoadType type = RoadType.ARTIFICIAL;
                    if (dto.type != null) {
                        try {
                            type = RoadType.valueOf(dto.type.toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException e) {
                            LOGGER.warn("Invalid road type '{}' in preset {}, defaulting to ARTIFICIAL", dto.type, id);
                        }
                    }

                    // Parse Dimensions
                    List<ResourceLocation> dims = new ArrayList<>();
                    if (dto.dimensions != null) {
                        for (String d : dto.dimensions) {
                            ResourceLocation rl = ResourceLocation.tryParse(d);
                            if (rl != null) dims.add(rl);
                        }
                    }
                    // Default to Overworld if empty (for backward compatibility or new files)
                    if (dims.isEmpty()) {
                        dims.add(new ResourceLocation("minecraft:overworld"));
                    }

                    PresetDef def = new PresetDef(id, name, type, Collections.unmodifiableList(dims), Collections.unmodifiableList(valid), Collections.unmodifiableList(validSlabs));
                    if (map.containsKey(id)) {
                        LOGGER.warn("Duplicate preset id '{}', file {} is ignored", id, p.getFileName());
                        continue;
                    }
                    map.put(id, def);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed scanning presets: {}", presetDir, e);
        }
        if (map.isEmpty()) {
            try {
                writeSamplePresets(presetDir);
            } catch (Exception e) {
                LOGGER.warn("Failed to write sample presets: {}", e.toString());
            }
            map = defaultPresets();
        }
        PRESETS.set(map);
        LOGGER.info("Presets loaded: {} entries", map.size());
    }

    private static String stripExt(String fn) {
        int i = fn.lastIndexOf('.');
        return i > 0 ? fn.substring(0, i) : fn;
    }

    private static PresetFile readPresetFile(Path p) {
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return GSON.fromJson(br, PresetFile.class);
        } catch (Exception e) {
            LOGGER.warn("Failed to read preset file {}: {}", p.getFileName(), e.toString());
            return null;
        }
    }

    private static void writeSamplePresets(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {}

        for (SamplePresetTemplate template : SAMPLE_PRESETS) {
            writePreset(dir.resolve(template.id() + ".json"), template.toPresetFile());
        }
    }

    private static void writePreset(Path file, PresetFile dto) {
        try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(dto, bw);
        } catch (Exception ignored) {}
    }

    private static Map<String, PresetDef> defaultPresets() {
        Map<String, PresetDef> m = new LinkedHashMap<>();
        for (SamplePresetTemplate template : SAMPLE_PRESETS) {
            PresetDef def = template.toPresetDef();
            if (m.containsKey(def.id())) continue;
            m.put(def.id(), def);
        }
        return m;
    }

    public static synchronized List<PresetDef> getAllPresets() {
        if (PRESETS.get().isEmpty()) reload();
        return List.copyOf(PRESETS.get().values());
    }

    public static synchronized PresetDef choosePreset(RandomSource rnd, ResourceLocation dimension, RoadType type) {
        if (PRESETS.get().isEmpty()) reload();
        Map<String, PresetDef> all = PRESETS.get();

        List<PresetDef> candidates = new ArrayList<>();
        for (PresetDef def : all.values()) {
            if (def.type == type && def.dimensions.contains(dimension)) {
                candidates.add(def);
            }
        }

        if (candidates.isEmpty()) {
            if (type == RoadType.NATURAL) {
                return new PresetDef("fallback_natural", "Natural Fallback", RoadType.NATURAL, List.of(dimension),
                        List.of("minecraft:dirt_path", "minecraft:gravel"), List.of());
            }
            return new PresetDef("fallback_artificial", "Artificial Fallback", RoadType.ARTIFICIAL, List.of(dimension),
                    List.of("minecraft:stone_bricks"), List.of("minecraft:stone_brick_slab"));
        }

        return pickPreset(rnd, candidates);
    }

    private static PresetDef pickPreset(RandomSource rnd, List<PresetDef> pool) {
        return pool.get(rnd.nextInt(pool.size()));
    }

    private static List<BlockState> toBlockStates(List<String> ids) {
        List<BlockState> out = new ArrayList<>();
        if (ids == null) return out;
        for (String s : ids) {
            try {
                ResourceLocation rl = new ResourceLocation(s);
                Block b = BuiltInRegistries.BLOCK.get(rl);
                if (b != null && b != Blocks.AIR) out.add(b.defaultBlockState());
            } catch (Throwable ignored) {}
        }
        if (out.isEmpty()) out.add(Blocks.STONE_BRICKS.defaultBlockState());
        return out;
    }

    public static List<BlockState> toBlockStatesFromIds(List<String> ids) {
        return toBlockStates(ids);
    }

    public static synchronized List<List<String>> getMaterialCombos() {
        if (PRESETS.get().isEmpty()) reload();
        List<List<String>> combos = new ArrayList<>();
        for (PresetDef d : PRESETS.get().values()) combos.add(d.materials());
        return combos;
    }

    public static synchronized void saveOrUpdatePresetFile(String id, String name, RoadType type, List<ResourceLocation> dimensions, List<String> materials, List<String> slabMaterials) {
        if (id == null || id.isBlank()) {
            return;
        }
        Path cfgRoot = Platform.getConfigFolder();
        Path baseDir = cfgRoot.resolve(BASE_DIR);
        Path presetDir = baseDir.resolve(PRESET_DIR);
        try {
            Files.createDirectories(presetDir);
        } catch (Exception e) {
            LOGGER.warn("Failed to create preset directory: {}", presetDir, e);
        }
        PresetFile dto = new PresetFile();
        dto.id = id;
        dto.name = name;
        dto.type = type.name();
        dto.dimensions = dimensions.stream().map(ResourceLocation::toString).toList();
        dto.materials = materials == null ? List.of() : new ArrayList<>(materials);
        dto.slabMaterials = slabMaterials == null ? List.of() : new ArrayList<>(slabMaterials);
        writePreset(presetDir.resolve(id + ".json"), dto);
    }
    
    // Kept for backward compatibility but redirecting
    public static synchronized void saveOrUpdatePresetFile(String id, String name, List<String> materials, List<String> slabMaterials) {
        saveOrUpdatePresetFile(id, name, RoadType.ARTIFICIAL, List.of(new ResourceLocation("minecraft:overworld")), materials, slabMaterials);
    }

    public static synchronized void deletePresetFile(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        Path cfgRoot = Platform.getConfigFolder();
        Path baseDir = cfgRoot.resolve(BASE_DIR);
        Path presetDir = baseDir.resolve(PRESET_DIR);
        try {
            Files.deleteIfExists(presetDir.resolve(id + ".json"));
        } catch (Exception e) {
            LOGGER.warn("Failed to delete preset file for id {}: {}", id, e.toString());
        }
    }

    private static class PresetFile {
        String id;
        String name;
        String type;
        List<String> dimensions;
        List<String> materials;
        List<String> slabMaterials;
    }

    public record PresetDef(String id, String name, RoadType type, List<ResourceLocation> dimensions, List<String> materials, List<String> slabMaterials) {}

    private record SamplePresetTemplate(String id, String name, RoadType type, List<ResourceLocation> dimensions, List<String> materials, List<String> slabMaterials) {
        PresetDef toPresetDef() {
            return new PresetDef(
                    id,
                    name,
                    type,
                    List.copyOf(dimensions),
                    List.copyOf(materials),
                    List.copyOf(slabMaterials)
            );
        }

        PresetFile toPresetFile() {
            PresetFile dto = new PresetFile();
            dto.id = id;
            dto.name = name;
            dto.type = type.name();
            dto.dimensions = dimensions.stream().map(ResourceLocation::toString).toList();
            dto.materials = List.copyOf(materials);
            dto.slabMaterials = List.copyOf(slabMaterials);
            return dto;
        }
    }

}
