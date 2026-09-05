package net.koastygames.witherdimension.item;

import java.util.Map;
import net.koastygames.witherdimension.WitherDimensionMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

public final class WitheriteArmorMaterial {
    public static final int BASE_DURABILITY = 41;
    public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, WitherDimensionMod.id("witherite"));
    public static final TagKey<Item> REPAIRS = TagKey.create(BuiltInRegistries.ITEM.key(), WitherDimensionMod.id("repairs_witherite"));
    public static final ArmorMaterial INSTANCE = new ArmorMaterial(
            BASE_DURABILITY,
            Map.of(ArmorType.HELMET, 4, ArmorType.CHESTPLATE, 9, ArmorType.LEGGINGS, 7, ArmorType.BOOTS, 4),
            18, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.5F, 0.15F, REPAIRS, ASSET);
    private WitheriteArmorMaterial() { }
}
