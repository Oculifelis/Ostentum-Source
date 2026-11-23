package com.ostentum.relic;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A relic: its base and likeness are drawn by the block entity's renderer, facing whoever placed it. The large one
 * (the Ender Dragon's) is the centre of a 3x3 and places its eight {@link RelicFrameBlock}s itself; it needs the eight
 * spaces around it free.
 */
public class RelicBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The base's outline: the pedestal's 14-wide foot and its body (1x1 base 10.25 high); the 3x3 fills its blocks. */
    private static final VoxelShape SMALL = Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(2, 2, 2, 14, 10.25, 14));
    static final VoxelShape LARGE = Block.box(0, 0, 0, 16, 16, 16);

    private final boolean large;
    private final MapCodec<RelicBlock> codec;

    public RelicBlock(Properties properties, boolean large) {
        super(properties);
        this.large = large;
        this.codec = simpleCodec(p -> new RelicBlock(p, large));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public boolean large() {
        return large;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (large) {
            BlockPos centre = ctx.getClickedPos();
            for (BlockPos p : BlockPos.betweenClosed(centre.offset(-1, 0, -1), centre.offset(1, 0, 1))) {
                if (!p.equals(centre) && !ctx.getLevel().getBlockState(p).canBeReplaced(ctx)) {
                    Player player = ctx.getPlayer();
                    if (player != null && ctx.getLevel().isClientSide()) {
                        player.displayClientMessage(Component.translatable("block.ostentum.dragon_relic.no_room"), true);
                    }
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        if (large && !level.isClientSide()) {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
                if (!p.equals(pos)) {
                    level.setBlock(p, Relics.DRAGON_RELIC_FRAME.get().defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return large ? LARGE : SMALL;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RelicBlockEntity(pos, state);
    }
}
