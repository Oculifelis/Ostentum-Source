package com.ostentum;

import com.fundamentum.damage.Elements;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ItemAttributeModifierEvent;

/**
 * Elements groundwork (Fundamentum damage elements): Fire Aspect weapons hit as fire, snowballs as ice, the trident is
 * a water weapon (item tag fundamentum:element/water), and each netherite armour piece resists fire a little.
 */
final class ElementsSetup {
    private ElementsSetup() {}

    static void init() {
        Elements.registerClassifier((element, source) -> {
            if (element == Elements.FIRE) {
                // 1.20.1 damage sources carry no weapon: a melee hit (direct entity is the attacker) uses its main hand
                Entity attacker = source.getEntity();
                if (!(attacker instanceof LivingEntity living) || source.getDirectEntity() != attacker) {
                    return false;
                }
                ItemStack weapon = living.getMainHandItem();
                return !weapon.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, weapon) > 0;
            }
            if (element == Elements.ICE) {
                return source.getDirectEntity() instanceof Snowball;
            }
            return false;
        });
        MinecraftForge.EVENT_BUS.addListener(ElementsSetup::netheriteFire);
    }

    private static void netheriteFire(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        EquipmentSlot slot;
        if (stack.is(Items.NETHERITE_HELMET)) {
            slot = EquipmentSlot.HEAD;
        } else if (stack.is(Items.NETHERITE_CHESTPLATE)) {
            slot = EquipmentSlot.CHEST;
        } else if (stack.is(Items.NETHERITE_LEGGINGS)) {
            slot = EquipmentSlot.LEGS;
        } else if (stack.is(Items.NETHERITE_BOOTS)) {
            slot = EquipmentSlot.FEET;
        } else {
            return;
        }
        if (event.getSlotType() != slot) {
            return;
        }
        String name = Ostentum.MODID + ":netherite_fire_" + slot.getName();
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        event.addModifier(Elements.FIRE.resistance().get(), new AttributeModifier(id, name, 0.0625, AttributeModifier.Operation.ADDITION));
    }
}
