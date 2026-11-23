package com.ostentum;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 1.20.1 stand-ins for 1.21's stack helpers (an item's components are its NBT tag here). */
public final class Stacks {
    /** 1.21's {@code stack.transmuteCopy(item)}: the same count and tag (enchantments, damage, name) on another item. */
    public static ItemStack transmute(ItemStack stack, Item item) {
        return transmute(stack, item, stack.getCount());
    }

    public static ItemStack transmute(ItemStack stack, Item item, int count) {
        ItemStack out = new ItemStack(item, count);
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            out.setTag(tag.copy());
        }
        return out;
    }

    private Stacks() {}
}
