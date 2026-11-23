package com.ostentum;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.DamageEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 1.20.1: enchantments are not data driven, so an Ostentum weapon that is a plain {@code Item} (swords, spears, the
 * hatchet, the picks, the Quake Hammer, the Fisher King's Trident's fishing enchantments) takes its enchantments from
 * the same item tags 1.21 uses ({@code minecraft:swords}, {@code minecraft:enchantable/weapon} and so on), read here
 * by {@code mixin/ItemMixin} and {@code mixin/EnchantmentMixin}.
 */
public final class Enchantables {
    private static final TagKey<Item> SWORDS = ItemTags.SWORDS;
    private static final TagKey<Item> AXES = ItemTags.AXES;
    private static final TagKey<Item> PICKAXES = ItemTags.PICKAXES;
    private static final TagKey<Item> SPEARS = tag("spears");
    private static final TagKey<Item> WEAPON = tag("enchantable/weapon");
    private static final TagKey<Item> MACE = tag("enchantable/mace");
    private static final TagKey<Item> FISHING = tag("enchantable/fishing");

    private static TagKey<Item> tag(String path) {
        return ItemTags.create(new ResourceLocation("minecraft", path));
    }

    /** True when one of Ostentum's tagged items takes {@code enchantment} (on top of what its own class allows). */
    public static boolean allows(ItemStack stack, Enchantment enchantment) {
        if (stack.isEmpty() || !"ostentum".equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace())) {
            return false;
        }
        EnchantmentCategory category = enchantment.category;
        if (category == EnchantmentCategory.WEAPON && (stack.is(SWORDS) || stack.is(SPEARS) || stack.is(WEAPON) || stack.is(MACE))) {
            return true;
        }
        if (category == EnchantmentCategory.DIGGER && (stack.is(AXES) || stack.is(PICKAXES))) {
            return true;
        }
        if (enchantment instanceof DamageEnchantment && stack.is(AXES)) {
            return true;
        }
        return category == EnchantmentCategory.FISHING_ROD && stack.is(FISHING);
    }

    private Enchantables() {}
}
