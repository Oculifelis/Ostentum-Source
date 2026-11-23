package com.ostentum.mixin;

import com.ostentum.Props;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link ItemMixin}'s enchantment value for the item classes that set their own (bows, crossbows, tridents, tools). */
@Mixin({ProjectileWeaponItem.class, TridentItem.class, TieredItem.class})
public abstract class ItemSubclassMixin {
    @Inject(method = "getEnchantmentValue", at = @At("HEAD"), cancellable = true)
    private void ostentum$enchantable(CallbackInfoReturnable<Integer> cir) {
        int value = Props.enchantable((Item) (Object) this);
        if (value > 0) {
            cir.setReturnValue(value);
        }
    }
}
