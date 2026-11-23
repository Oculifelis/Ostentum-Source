package com.ostentum.music;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ListBackedContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Chiseled Record Shelf's six discs, one per slot, and the last slot touched (for the comparator). A container,
 * so hoppers can fill and empty it and the discs drop when the block breaks (BlockEntity#preRemoveSideEffects).
 */
public class ChiseledRecordShelfBlockEntity extends BlockEntity implements ListBackedContainer {
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

    /** Matches the block state's six slot properties to the items, and remembers the slot touched. */
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
    public NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean acceptsItemType(ItemStack stack) {
        return RecordShelves.isDisc(stack);
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
    public void setItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            removeItem(slot, 1);
        } else if (acceptsItemType(stack)) {
            items.set(slot, stack);
            touched(slot);
        }
    }

    /** NeoForge transfers (hoppers, pipes): set the slot now, update the block state once the transaction commits. */
    @Override
    public void setItem(int slot, ItemStack stack, boolean insideTransaction) {
        if (!insideTransaction) {
            setItem(slot, stack);
        } else if (stack.isEmpty() || acceptsItemType(stack)) {
            items.set(slot, stack);
        }
    }

    private final net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Integer> lastSlotJournal =
        new net.neoforged.neoforge.transfer.transaction.SnapshotJournal<>() {
            @Override
            protected Integer createSnapshot() {
                return lastSlot;
            }

            @Override
            protected void revertToSnapshot(Integer snapshot) {
                lastSlot = snapshot;
            }

            @Override
            protected void onRootCommit(Integer original) {
                if (!isRemoved()) {
                    touched(lastSlot);
                }
            }
        };

    @Override
    public void onTransfer(int slot, int amountChange, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
        lastSlotJournal.updateSnapshots(transaction);
        lastSlot = slot;
    }

    @Override
    public boolean canTakeItem(Container into, int slot, ItemStack stack) {
        return into.hasAnyMatching(to -> to.isEmpty()
            || ItemStack.isSameItemSameComponents(stack, to) && to.getCount() + stack.getCount() <= into.getMaxStackSize(to));
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        lastSlot = input.getIntOr("last_interacted_slot", -1);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items, true);
        output.putInt("last_interacted_slot", lastSlot);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("Items");
    }
}
