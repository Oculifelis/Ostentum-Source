package com.ostentum;

import com.fundamentum.damage.Elements;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * Elements groundwork (Fundamentum damage elements): Fire Aspect weapons hit as fire, snowballs as ice, the trident is
 * a water weapon (item tag fundamentum:element/water), and each netherite armour piece resists fire a little.
 */
final class ElementsSetup {
    private ElementsSetup() {}

    static void init() {
        Elements.registerClassifier((element, source) -> {
            if (element == Elements.FIRE) {
                ItemStack weapon = source.getWeaponItem();
                Entity attacker = source.getEntity();
                return weapon != null && !weapon.isEmpty() && attacker != null && EnchantmentHelper.getItemEnchantmentLevel(
                    attacker.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FIRE_ASPECT), weapon) > 0;
            }
            if (element == Elements.ICE) {
                return source.getDirectEntity() instanceof Snowball;
            }
            return false;
        });
        NeoForge.EVENT_BUS.addListener(ElementsSetup::netheriteFire);
    }

    private static void netheriteFire(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        EquipmentSlotGroup slot;
        if (stack.is(Items.NETHERITE_HELMET)) {
            slot = EquipmentSlotGroup.HEAD;
        } else if (stack.is(Items.NETHERITE_CHESTPLATE)) {
            slot = EquipmentSlotGroup.CHEST;
        } else if (stack.is(Items.NETHERITE_LEGGINGS)) {
            slot = EquipmentSlotGroup.LEGS;
        } else if (stack.is(Items.NETHERITE_BOOTS)) {
            slot = EquipmentSlotGroup.FEET;
        } else {
            return;
        }
        Identifier id = Ostentum.id("netherite_fire_" + slot.getSerializedName());
        event.addModifier(Elements.FIRE.resistance(), new AttributeModifier(id, 0.0625, AttributeModifier.Operation.ADD_VALUE), slot);
    }
}
