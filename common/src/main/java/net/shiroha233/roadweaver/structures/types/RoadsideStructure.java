package net.shiroha233.roadweaver.structures.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.shiroha233.roadweaver.structures.data.LootConfig;
import net.shiroha233.roadweaver.structures.data.MobSpawnRule;
import net.shiroha233.roadweaver.structures.data.RoadsidePlacementRule;
import net.shiroha233.roadweaver.structures.data.StructureScale;
import net.shiroha233.roadweaver.structures.pieces.SimpleTemplatePiece;
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes;

import java.util.List;
import java.util.Optional;

/**
 * 路边结构类型
 */
public class RoadsideStructure extends Structure {
    
    public static final Codec<RoadsideStructure> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            settingsCodec(instance),
            ResourceLocation.CODEC.fieldOf("template").forGetter(s -> s.templateId),
            Vec3i.CODEC.optionalFieldOf("size_hint", new Vec3i(5, 5, 5)).forGetter(s -> s.sizeHint),
            Codec.INT.optionalFieldOf("weight", 10).forGetter(s -> s.weight),
            Codec.BOOL.optionalFieldOf("face_road", true).forGetter(s -> s.faceRoad),
            StructureScale.CODEC.optionalFieldOf("scale", StructureScale.SMALL).forGetter(s -> s.scale),
            RoadsidePlacementRule.CODEC.optionalFieldOf("placement_rule", RoadsidePlacementRule.UNIVERSAL).forGetter(s -> s.placementRule),
            MobSpawnRule.LIST_CODEC.optionalFieldOf("mob_spawns", List.of()).forGetter(s -> s.mobSpawns),
            LootConfig.LIST_CODEC.optionalFieldOf("loot_configs", List.of()).forGetter(s -> s.lootConfigs)
        ).apply(instance, RoadsideStructure::new)
    );
    
    private final ResourceLocation templateId;
    private final Vec3i sizeHint;
    private final int weight;
    private final boolean faceRoad;
    private final StructureScale scale;
    private final RoadsidePlacementRule placementRule;
    private final List<MobSpawnRule> mobSpawns;
    private final List<LootConfig> lootConfigs;
    
    public RoadsideStructure(StructureSettings settings,
                            ResourceLocation templateId,
                            Vec3i sizeHint,
                            int weight,
                            boolean faceRoad,
                            StructureScale scale,
                            RoadsidePlacementRule placementRule,
                            List<MobSpawnRule> mobSpawns,
                            List<LootConfig> lootConfigs) {
        super(settings);
        this.templateId = templateId;
        this.sizeHint = sizeHint;
        this.weight = weight;
        this.faceRoad = faceRoad;
        this.scale = scale;
        this.placementRule = placementRule;
        this.mobSpawns = mobSpawns;
        this.lootConfigs = lootConfigs;
    }
    
    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return Optional.empty();
    }
    
    @Override
    public StructureType<?> type() {
        return ModStructureTypes.ROADSIDE;
    }
    
    public ResourceLocation templateId() {
        return templateId;
    }
    
    public Vec3i sizeHint() {
        return sizeHint;
    }
    
    public int weight() {
        return weight;
    }
    
    public boolean faceRoad() {
        return faceRoad;
    }
    
    public StructureScale scale() {
        return scale;
    }
    
    public RoadsidePlacementRule placementRule() {
        return placementRule;
    }
    
    public List<MobSpawnRule> mobSpawns() {
        return mobSpawns;
    }
    
    public List<LootConfig> lootConfigs() {
        return lootConfigs;
    }
    
    public StructureStart placeAt(WorldGenLevel level,
                                  StructureManager structureManager,
                                  StructureTemplateManager templateManager,
                                  ChunkGenerator generator,
                                  BlockPos pos,
                                  Rotation rotation,
                                  RandomSource random) {
        StructurePiecesBuilder builder = new StructurePiecesBuilder();
        
        SimpleTemplatePiece piece = new SimpleTemplatePiece(
            templateManager,
            templateId,
            pos,
            rotation,
            Mirror.NONE,
            mobSpawns,
            lootConfigs
        );
        builder.addPiece(piece);
        
        ChunkPos chunkPos = new ChunkPos(pos);
        PiecesContainer container = builder.build();
        StructureStart start = new StructureStart(this, chunkPos, 0, container);
        
        if (!start.isValid()) {
            return null;
        }
        
        BoundingBox boundingBox = start.getBoundingBox();
        start.placeInChunk(level, structureManager, generator, random, boundingBox, chunkPos);
        
        return start;
    }
    
    public void saveToChunk(WorldGenLevel level, 
                           StructureManager structureManager,
                           StructureStart start,
                           ChunkPos chunkPos) {
    }
}
