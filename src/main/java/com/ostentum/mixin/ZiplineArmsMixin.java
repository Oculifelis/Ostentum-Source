package com.ostentum.mixin;

import com.ostentum.client.ZiplineClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A zipline rider hangs from the rope by both hands, legs loose (client.ZiplineClient sets the render-state flag). */
@Mixin(PlayerModel.class)
abstract class ZiplineArmsMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void ostentum$hang(AvatarRenderState state, CallbackInfo ci) {
        if (!Boolean.TRUE.equals(state.getRenderData(ZiplineClient.HANGING))) {
            return;
        }
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        model.rightArm.xRot = (float) Math.PI;
        model.rightArm.yRot = 0.0F;
        model.rightArm.zRot = 0.12F;
        model.leftArm.xRot = (float) Math.PI;
        model.leftArm.yRot = 0.0F;
        model.leftArm.zRot = -0.12F;
        float sway = state.ageInTicks * 0.1F;
        model.rightLeg.xRot = 0.12F + (float) Math.sin(sway) * 0.06F;
        model.leftLeg.xRot = -0.05F - (float) Math.sin(sway) * 0.06F;
        model.rightLeg.zRot = 0.03F;
        model.leftLeg.zRot = -0.03F;
    }
}
