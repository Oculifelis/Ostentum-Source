package com.ostentum.mixin;

import com.ostentum.Enchantables;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The anvil's and commands' check: Ostentum's tagged items take the enchantments {@link Enchantables} gives them. */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void ostentum$canEnchant(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Enchantables.allows(stack, (Enchantment) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
