package com.ostentum.music;

import net.minecraft.util.Prediction;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SelectableSlotContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla's chiseled bookshelf for music discs: six slots (two rows of three) on its front, one disc each. Click a slot
 * with a disc to put it in, with an empty hand to take it out; the block state shows which slots are filled (the same
 * six properties as the chiseled bookshelf), a comparator reads the last slot touched (1 to 6), and the discs drop when
 * it breaks. It counts as one bookshelf for an enchanting table while it holds at least {@link #DISCS_FOR_POWER} discs;
 * read from the block state, so the table's rune particles agree on the client.
 */
public class ChiseledRecordShelfBlock extends BaseEntityBlock implements SelectableSlotContainer {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final List<BooleanProperty> SLOTS = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES;
    /** Discs needed before the shelf powers an enchanting table. */
    public static final int DISCS_FOR_POWER = 3;

    public ChiseledRecordShelfBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any().setValue(FACING, Direction.NORTH);
        for (BooleanProperty slot : SLOTS) {
            state = state.setValue(slot, false);
        }
        this.registerDefaultState(state);
    }

    @Override
    public int getRows() {
        return 2;
    }

    @Override
    public int getColumns() {
        return 3;
    }

    /** Filled slots, from the block state. */
    public static int discs(BlockState state) {
        int n = 0;
        for (BooleanProperty slot : SLOTS) {
            if (state.hasProperty(slot) && state.getValue(slot)) {
                n++;
            }
        }
        return n;
    }

    /** NeoForge's enchanting power hook: one bookshelf's worth once it holds three discs, nothing before. */
    @Override
    public float getEnchantPowerBonus(BlockState state, BlockGetter level, BlockPos pos) {
        return discs(state) >= DISCS_FOR_POWER ? 1.0F : 0.0F;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
        BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ChiseledRecordShelfBlockEntity shelf)) {
            return InteractionResult.PASS;
        }
        if (!RecordShelves.isDisc(stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        OptionalInt slot = this.getHitSlot(hit, state.getValue(FACING));
        if (slot.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (state.getValue(SLOTS.get(slot.getAsInt()))) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            shelf.setItem(slot.getAsInt(), stack.consumeAndReturn(1, player));
            level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_INSERT, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ChiseledRecordShelfBlockEntity shelf)) {
            return InteractionResult.PASS;
        }
        OptionalInt slot = this.getHitSlot(hit, state.getValue(FACING));
        if (slot.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!state.getValue(SLOTS.get(slot.getAsInt()))) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            ItemStack disc = shelf.removeItem(slot.getAsInt(), 1);
            level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 1.0F, 1.2F);
            if (!player.getInventory().add(disc)) {
                player.drop(disc, false, Prediction.SERVER_ONLY);
            }
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChiseledRecordShelfBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        SLOTS.forEach(builder::add);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.isClientSide()) {
            return 0;
        }
        return level.getBlockEntity(pos) instanceof ChiseledRecordShelfBlockEntity shelf ? shelf.lastSlot() + 1 : 0;
    }
}
