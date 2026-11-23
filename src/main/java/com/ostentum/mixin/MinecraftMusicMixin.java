package com.ostentum.mixin;

import com.ostentum.client.MoonMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.20.1 has no SelectMusicEvent: the moon and siege tracks are picked at the end of the situational music. */
@Mixin(Minecraft.class)
public abstract class MinecraftMusicMixin {
    @Inject(method = "getSituationalMusic", at = @At("RETURN"), cancellable = true)
    private void ostentum$moonMusic(CallbackInfoReturnable<Music> cir) {
        cir.setReturnValue(MoonMusic.select(cir.getReturnValue()));
    }
}
