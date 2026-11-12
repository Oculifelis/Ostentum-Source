package com.ostentum.weapon;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Thrown weapons coming home go back to the slot they left.
 * The throw records the slot; on the way back the server puts the weapon there if that slot is still empty
 * (so a player still on that hotbar slot has it in hand again), else in the first free hotbar slot, else anywhere in
 * the inventory. Used by the Frostbite Hatchet and the Ostentum tridents.
 */
public final class ReturnSlot {
    /** No slot recorded (a dispenser's throw, or a projectile from before 1.9.4). */
    public static final int NONE = -1;

    private ReturnSlot() {}

    /** The inventory slot of the stack in {@code hand}: the selected hotbar slot, or 40 for the offhand. */
    public static int of(Player player, InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().selected;
    }

    /** Puts {@code stack} back: its own slot if empty, else the first free hotbar slot, else the inventory; false if full. */
    public static boolean give(Player player, ItemStack stack, int slot) {
        Inventory inventory = player.getInventory();
        if ((slot >= 0 && slot < Inventory.INVENTORY_SIZE || slot == Inventory.SLOT_OFFHAND) && inventory.getItem(slot).isEmpty()) {
            inventory.setItem(slot, stack);
            inventory.setChanged();
            return true;
        }
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                inventory.setItem(i, stack);
                inventory.setChanged();
                return true;
            }
        }
        return inventory.add(stack);
    }
}
