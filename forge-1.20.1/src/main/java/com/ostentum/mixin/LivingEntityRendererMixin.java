package com.ostentum.mixin;

import com.ostentum.weapon.PhantomVeil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Phantom Veil's shimmer (1.21.1): a sneaking wearer seen from beyond {@link PhantomVeil#SHIMMER_RANGE} blocks by
 * someone else has no visible body, so vanilla draws them as it draws an invisible player one can still see: faint.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "isBodyVisible", at = @At("HEAD"), cancellable = true)
    private void ostentum$veilShimmer(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        Player viewer = Minecraft.getInstance().player;
        if (entity instanceof Player player && player != viewer && viewer != null && player.isCrouching() && PhantomVeil.wears(player)
            && player.distanceToSqr(viewer) > PhantomVeil.SHIMMER_RANGE * PhantomVeil.SHIMMER_RANGE) {
            cir.setReturnValue(false);
        }
    }
}
