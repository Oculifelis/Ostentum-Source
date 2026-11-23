package com.ostentum.mixin;

import com.ostentum.Enchanted;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Under the Enchanted Moon an enchantment takes no levels (the lapis is still used). */
@Mixin(Player.class)
abstract class PlayerMixin {
    @ModifyVariable(method = "onEnchantmentPerformed", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int ostentum$free(int cost, ItemStack stack) {
        return Enchanted.up(((Player) (Object) this).level()) ? 0 : cost;
    }
}
