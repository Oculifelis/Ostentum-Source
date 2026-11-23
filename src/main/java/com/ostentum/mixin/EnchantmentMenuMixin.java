package com.ostentum.mixin;

import com.ostentum.Enchanted;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Under the Enchanted Moon the table accepts any level (both sides check, so the client lets the click through too). */
@Mixin(EnchantmentMenu.class)
abstract class EnchantmentMenuMixin {
    @Redirect(method = "clickMenuButton", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Player;experienceLevel:I",
        opcode = Opcodes.GETFIELD))
    private int ostentum$levels(Player player) {
        return Enchanted.up(player.level()) ? Integer.MAX_VALUE : player.experienceLevel;
    }
}
