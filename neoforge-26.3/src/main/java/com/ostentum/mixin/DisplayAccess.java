package com.ostentum.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Display entities' private setters, for the weapons' planted glaive and quake blocks ({@code weapon}). */
@Mixin(Display.class)
public interface DisplayAccess {
    @Invoker("setTransformation")
    void ostentum$setTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void ostentum$setInterpolationDuration(int ticks);

    @Invoker("setTransformationInterpolationDelay")
    void ostentum$setInterpolationDelay(int ticks);

    @Invoker("setGlowColorOverride")
    void ostentum$setGlowColorOverride(int argb);
}
