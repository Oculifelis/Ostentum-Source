package com.ostentum.mixin;

import com.ostentum.weapon.Zipline;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** On a zipline sneak is the brake, not "get off": jump lets go instead (weapon.Zipline.Rider). */
@Mixin(Player.class)
abstract class ZiplinePlayerMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void ostentum$ziplineBrake(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof Zipline.Rider) {
            cir.setReturnValue(false);
        }
    }
}
