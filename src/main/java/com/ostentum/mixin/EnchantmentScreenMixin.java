package com.ostentum.mixin;

import com.ostentum.client.ClientMoon;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Under the Enchanted Moon the table's buttons draw as affordable whatever the player's level. */
@Mixin(EnchantmentScreen.class)
abstract class EnchantmentScreenMixin {
    @Redirect(method = {"renderBg", "render"}, require = 0,
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I", opcode = Opcodes.GETFIELD))
    private int ostentum$levels(net.minecraft.client.player.LocalPlayer player) {
        return ClientMoon.enchanted() ? Integer.MAX_VALUE : player.experienceLevel;
    }
}
