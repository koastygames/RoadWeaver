package net.koastygames.witherdimension.registry;

import java.util.function.Function;
import net.koastygames.witherdimension.WitherDimensionMod;
import net.koastygames.witherdimension.block.SoulBrazierBlock;
import net.koastygames.witherdimension.block.WitherGateBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    public static final Block WITHERED_OBSIDIAN = register("withered_obsidian", Block::new,
            stone(50.0F, 1200.0F), true);
    public static final Block WITHER_STONE = register("wither_stone", Block::new, stone(3.5F, 9.0F), true);
    public static final Block POLISHED_WITHER_STONE = register("polished_wither_stone", Block::new, stone(3.7F, 10.0F), true);
    public static final Block WITHER_BRICKS = register("wither_bricks", Block::new, stone(4.0F, 11.0F), true);
    public static final Block CRACKED_WITHER_BRICKS = register("cracked_wither_bricks", Block::new, stone(3.4F, 9.0F), true);
    public static final Block VOID_INFUSED_STONE = register("void_infused_stone", Block::new,
            stone(4.8F, 14.0F).lightLevel(s -> 3), true);

    public static final Block ASH_STONE = register("ash_stone", Block::new, stone(2.0F, 6.0F), true);
    public static final Block ASH_BRICKS = register("ash_bricks", Block::new, stone(2.5F, 7.0F), true);
    public static final Block SOULSTONE = register("soulstone", Block::new,
            stone(2.5F, 7.0F).lightLevel(s -> 3), true);
    public static final Block SOUL_SAND_BRICKS = register("soul_sand_bricks", Block::new, stone(2.2F, 6.0F), true);
    public static final Block BONE_BRICKS = register("bone_bricks", Block::new, stone(2.0F, 6.0F), true);
    public static final Block BONE_PILLAR = register("bone_pillar", Block::new, stone(2.4F, 7.0F), true);
    public static final Block SOUL_FIRE_LOG = register("soul_fire_log", Block::new,
            BlockBehaviour.Properties.of().strength(2.2F, 4.0F).lightLevel(s -> 4), true);
    public static final Block WITHER_MOSS = register("wither_moss", Block::new,
            BlockBehaviour.Properties.of().strength(0.5F, 1.0F).lightLevel(s -> 2), true);
    public static final Block SKULL_LANTERN = register("skull_lantern", Block::new,
            stone(2.0F, 5.0F).lightLevel(s -> 13), true);

    public static final Block CURSED_CRYSTAL_BLOCK = register("cursed_crystal_block", Block::new,
            stone(4.0F, 8.0F).lightLevel(s -> 10), true);
    public static final Block CURSED_CRYSTAL_ORE = register("cursed_crystal_ore", Block::new,
            stone(6.0F, 12.0F).lightLevel(s -> 7), true);
    public static final Block WITHERITE_ORE = register("witherite_ore", Block::new,
            stone(7.0F, 15.0F), true);
    public static final Block SOUL_VEIN_ORE = register("soul_vein_ore", Block::new,
            stone(6.0F, 12.0F).lightLevel(s -> 6), true);

    public static final Block SOUL_BRAZIER = register("soul_brazier", SoulBrazierBlock::new,
            stone(5.0F, 12.0F).lightLevel(s -> 12), true);
    public static final Block WITHER_GATE = register("wither_gate", WitherGateBlock::new,
            BlockBehaviour.Properties.of().noCollision().noOcclusion().strength(-1.0F, 3600000.0F).lightLevel(s -> 11), false);

    private static BlockBehaviour.Properties stone(float hardness, float resistance) {
        return BlockBehaviour.Properties.of().strength(hardness, resistance).requiresCorrectToolForDrops();
    }

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties properties, boolean item) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, WitherDimensionMod.id(name));
        Block block = factory.apply(properties.setId(blockKey));
        if (item) {
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, WitherDimensionMod.id(name));
            BlockItem blockItem = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
            Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);
        }
        return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
    }

    public static void initialize() { }
    private ModBlocks() { }
}
