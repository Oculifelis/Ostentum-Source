package com.ostentum.mixin;

import com.google.common.collect.Multimap;
import com.ostentum.Enchantables;
import com.ostentum.Props;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.20.1: items built from {@link Props} take their enchantment value, repair item, main-hand attributes and mining from
 * it (26.x properties and components), and Ostentum's tagged items their enchantments from {@link Enchantables}.
 */
@Mixin(Item.class)
public abstract class ItemMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void ostentum$props(Item.Properties properties, CallbackInfo ci) {
        Props.remember((Item) (Object) this, properties);
    }

    @Inject(method = "getEnchantmentValue", at = @At("HEAD"), cancellable = true)
    private void ostentum$enchantable(CallbackInfoReturnable<Integer> cir) {
        int value = Props.enchantable((Item) (Object) this);
        if (value > 0) {
            cir.setReturnValue(value);
        }
    }

    @Inject(method = "isValidRepairItem", at = @At("HEAD"), cancellable = true)
    private void ostentum$repairable(ItemStack stack, ItemStack with, CallbackInfoReturnable<Boolean> cir) {
        if (Props.repairs((Item) (Object) this, with)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getDefaultAttributeModifiers", at = @At("HEAD"), cancellable = true)
    private void ostentum$attributes(EquipmentSlot slot, CallbackInfoReturnable<Multimap<Attribute, AttributeModifier>> cir) {
        if (slot == EquipmentSlot.MAINHAND) {
            Multimap<Attribute, AttributeModifier> attributes = Props.attributes((Item) (Object) this);
            if (attributes != null) {
                cir.setReturnValue(attributes);
            }
        }
    }

    @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
    private void ostentum$destroySpeed(ItemStack stack, BlockState state, CallbackInfoReturnable<Float> cir) {
        float speed = Props.destroySpeed((Item) (Object) this, state);
        if (speed >= 0.0F) {
            cir.setReturnValue(speed);
        }
    }

    @Inject(method = "isCorrectToolForDrops", at = @At("HEAD"), cancellable = true)
    private void ostentum$correctTool(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        Boolean correct = Props.correctTool((Item) (Object) this, state);
        if (correct != null) {
            cir.setReturnValue(correct);
        }
    }

    /** Overrides Forge's {@code IForgeItem} default (the enchanting table's check), keeping what it did. */
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return Enchantables.allows(stack, enchantment) || enchantment.category.canEnchant(stack.getItem());
    }
}
