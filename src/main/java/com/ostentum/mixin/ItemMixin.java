package com.ostentum.mixin;

import com.ostentum.Props;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.21.1: items built from {@link Props} take their enchantment value and repair item from it (26.x properties). */
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
}
