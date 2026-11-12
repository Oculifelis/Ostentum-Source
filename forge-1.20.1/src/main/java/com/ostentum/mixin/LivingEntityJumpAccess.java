package com.ostentum.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether a living thing holds jump: a zipline rider's jump lets go (weapon.Zipline.Rider). */
@Mixin(LivingEntity.class)
public interface LivingEntityJumpAccess {
    @Accessor("jumping")
    boolean ostentum$jumping();
}
