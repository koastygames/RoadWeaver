package net.shiroha233.roadweaver.structures.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.shiroha233.roadweaver.structures.registry.ModStructureTypes;

import java.util.Optional;

public class BridgeTemplateStructure extends Structure {
    public static final Codec<BridgeTemplateStructure> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    settingsCodec(instance),
                    ResourceLocation.CODEC.fieldOf("template").forGetter(s -> s.templateId),
                    // 桥高度偏移，通常为结构模板的地基的高度
                    Codec.INT.optionalFieldOf("height_offset", 1).forGetter(s -> s.heightOffset),
                    // 桥面开始位置
                    Codec.INT.optionalFieldOf("bridge_deck_start", 0).forGetter(s -> s.bridgeDeckStart),
                    // 桥面结束位置
                    Codec.INT.optionalFieldOf("bridge_deck_end", 100).forGetter(s -> s.bridgeDeckEnd)
            ).apply(instance, BridgeTemplateStructure::new)
    );

    public final ResourceLocation templateId;
    public final int heightOffset;
    public final int bridgeDeckStart;
    public final int bridgeDeckEnd;

    public BridgeTemplateStructure(StructureSettings structureSettings, ResourceLocation templateId, int roadbedHeight, int bridgeDeckStart, int bridgeDeckEnd) {
        super(structureSettings);
        this.templateId = templateId;
        this.heightOffset = roadbedHeight;
        this.bridgeDeckStart = bridgeDeckStart;
        this.bridgeDeckEnd = bridgeDeckEnd;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext generationContext) {
        return Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return ModStructureTypes.BRIDGE_TEMPLATE;
    }
}
