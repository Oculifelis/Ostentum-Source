package com.ostentum.music;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * The Chiseled Record Shelf's six discs, one per slot, and the last slot touched (for the comparator). A container,
 * so hoppers can fill and empty it; the block drops the discs when it breaks.
 */
public class ChiseledRecordShelfBlockEntity extends BlockEntity implements Container {
    public static final int SIZE = 6;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private int lastSlot = -1;

    public ChiseledRecordShelfBlockEntity(BlockPos pos, BlockState state) {
        super(RecordShelves.CHISELED_RECORD_SHELF_BE.get(), pos, state);
    }

    /** The slot last put into or taken from, 0 to 5, or -1. */
    public int lastSlot() {
        return lastSlot;
    }

    private void touched(int slot) {
        if (slot < 0 || slot >= SIZE || level == null) {
            return;
        }
        lastSlot = slot;
        BlockState state = getBlockState();
        for (int i = 0; i < SIZE; i++) {
            state = state.setValue(ChiseledRecordShelfBlock.SLOTS.get(i), !items.get(i).isEmpty());
        }
        level.setBlock(worldPosition, state, 3);
        level.gameEvent(GameEvent.BLOCK_CHANGE, worldPosition, GameEvent.Context.of(state));
        setChanged();
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack out = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        if (!out.isEmpty()) {
            touched(slot);
        }
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, 1);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            removeItem(slot, 1);
        } else if (RecordShelves.isDisc(stack)) {
            items.set(slot, stack);
            touched(slot);
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return RecordShelves.isDisc(stack) && items.get(slot).isEmpty();
    }

    @Override
    public boolean canTakeItem(Container into, int slot, ItemStack stack) {
        return into.hasAnyMatching(to -> to.isEmpty()
            || ItemStack.isSameItemSameComponents(stack, to) && to.getCount() + stack.getCount() <= into.getMaxStackSize(to));
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        lastSlot = tag.contains("last_interacted_slot") ? tag.getInt("last_interacted_slot") : -1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, true, registries);
        tag.putInt("last_interacted_slot", lastSlot);
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Items");
    }
}
