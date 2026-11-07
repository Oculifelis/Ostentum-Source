package com.ostentum.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A block display's private block setter ({@code weapon.QuakeHammer}). */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccess {
    @Invoker("setBlockState")
    void ostentum$setBlockState(BlockState state);
}
