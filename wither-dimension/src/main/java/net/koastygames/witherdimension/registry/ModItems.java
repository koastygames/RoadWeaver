package net.koastygames.witherdimension.registry;

import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.koastygames.witherdimension.WitherDimensionMod;
import net.koastygames.witherdimension.item.WitheriteArmorMaterial;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;

public final class ModItems {
    public static final TagKey<Block> INCORRECT_FOR_WITHERITE = TagKey.create(Registries.BLOCK, WitherDimensionMod.id("incorrect_for_witherite_tool"));
    public static final ToolMaterial WITHERITE_MATERIAL = new ToolMaterial(INCORRECT_FOR_WITHERITE, 2495, 10.0F, 5.0F, 18, WitheriteArmorMaterial.REPAIRS);

    public static final Item RAW_WITHERITE = register("raw_witherite", Item::new, new Item.Properties());
    public static final Item WITHERITE_INGOT = register("witherite_ingot", Item::new, new Item.Properties().fireResistant());
    public static final Item SOUL_VEIN_SHARD = register("soul_vein_shard", Item::new, new Item.Properties());
    public static final Item SOUL_CORE = register("soul_core", Item::new, new Item.Properties().rarity(Rarity.RARE));

    public static final Item WITHERITE_SWORD = register("witherite_sword", Item::new,
            new Item.Properties().sword(WITHERITE_MATERIAL, 4.0F, -2.35F).fireResistant());
    public static final Item WITHERITE_PICKAXE = register("witherite_pickaxe", Item::new,
            new Item.Properties().pickaxe(WITHERITE_MATERIAL, 1.0F, -2.75F).fireResistant());
    public static final Item WITHERITE_AXE = register("witherite_axe", p -> new AxeItem(WITHERITE_MATERIAL, 6.0F, -3.0F, p),
            new Item.Properties().fireResistant());
    public static final Item WITHERITE_SHOVEL = register("witherite_shovel", Item::new,
            new Item.Properties().shovel(WITHERITE_MATERIAL, 1.5F, -3.0F).fireResistant());
    public static final Item WITHERITE_HOE = register("witherite_hoe", p -> new HoeItem(WITHERITE_MATERIAL, -5.0F, 0.0F, p),
            new Item.Properties().fireResistant());
    public static final Item WITHERITE_WARHAMMER = register("witherite_warhammer", Item::new,
            new Item.Properties().sword(WITHERITE_MATERIAL, 9.0F, -3.4F).durability(3100).rarity(Rarity.EPIC).fireResistant());

    public static final Item WITHERITE_HELMET = armor("witherite_helmet", ArmorType.HELMET);
    public static final Item WITHERITE_CHESTPLATE = armor("witherite_chestplate", ArmorType.CHESTPLATE);
    public static final Item WITHERITE_LEGGINGS = armor("witherite_leggings", ArmorType.LEGGINGS);
    public static final Item WITHERITE_BOOTS = armor("witherite_boots", ArmorType.BOOTS);

    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), WitherDimensionMod.id("main"));
    public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.witherdimension.main"))
            .icon(() -> new ItemStack(WITHERITE_INGOT))
            .displayItems((params, output) -> {
                output.accept(ModBlocks.WITHERED_OBSIDIAN); output.accept(ModBlocks.WITHER_STONE); output.accept(ModBlocks.POLISHED_WITHER_STONE);
                output.accept(ModBlocks.WITHER_BRICKS); output.accept(ModBlocks.CRACKED_WITHER_BRICKS); output.accept(ModBlocks.VOID_INFUSED_STONE);
                output.accept(ModBlocks.ASH_STONE); output.accept(ModBlocks.ASH_BRICKS); output.accept(ModBlocks.SOULSTONE); output.accept(ModBlocks.SOUL_SAND_BRICKS);
                output.accept(ModBlocks.BONE_BRICKS); output.accept(ModBlocks.BONE_PILLAR); output.accept(ModBlocks.SOUL_FIRE_LOG); output.accept(ModBlocks.WITHER_MOSS);
                output.accept(ModBlocks.SKULL_LANTERN); output.accept(ModBlocks.CURSED_CRYSTAL_BLOCK); output.accept(ModBlocks.CURSED_CRYSTAL_ORE);
                output.accept(ModBlocks.WITHERITE_ORE); output.accept(ModBlocks.SOUL_VEIN_ORE); output.accept(ModBlocks.SOUL_BRAZIER);
                output.accept(RAW_WITHERITE); output.accept(WITHERITE_INGOT); output.accept(SOUL_VEIN_SHARD); output.accept(SOUL_CORE);
                output.accept(WITHERITE_SWORD); output.accept(WITHERITE_PICKAXE); output.accept(WITHERITE_AXE); output.accept(WITHERITE_SHOVEL); output.accept(WITHERITE_HOE); output.accept(WITHERITE_WARHAMMER);
                output.accept(WITHERITE_HELMET); output.accept(WITHERITE_CHESTPLATE); output.accept(WITHERITE_LEGGINGS); output.accept(WITHERITE_BOOTS);
            }).build();

    private static Item armor(String name, ArmorType type) {
        return register(name, Item::new, new Item.Properties().humanoidArmor(WitheriteArmorMaterial.INSTANCE, type)
                .durability(type.getDurability(WitheriteArmorMaterial.BASE_DURABILITY)).fireResistant());
    }

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, WitherDimensionMod.id(name));
        Item item = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void initialize() { Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, TAB); }
    private ModItems() { }
}
