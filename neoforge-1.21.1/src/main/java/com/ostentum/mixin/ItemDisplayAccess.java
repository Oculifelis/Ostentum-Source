package com.ostentum.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** An item display's private item setter and getter ({@code weapon.PathfindersGlaive}, {@code client.GlaiveBeam}). */
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccess {
    @Invoker("setItemStack")
    void ostentum$setItemStack(ItemStack stack);

    @Invoker("getItemStack")
    ItemStack ostentum$getItemStack();
}
