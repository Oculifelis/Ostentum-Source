package com.ostentum.relic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * One of the eight outer blocks of the Ender Dragon's 3x3 relic: solid, invisible (the centre's renderer draws the whole
 * base). Breaking one breaks the relic; once the centre is gone, each frame block turns to air at the next neighbour
 * update, so the rest clear themselves in a ripple.
 */
public class RelicFrameBlock extends Block {
    public RelicFrameBlock(Properties properties) {
        super(properties);
    }

    /** The relic's centre within one block (frames of a neighbouring relic are never that close to its centre). */
    static @Nullable BlockPos centre(BlockGetter level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
            if (level.getBlockState(p).is(Relics.DRAGON_RELIC.get())) {
                return p.immutable();
            }
        }
        return null;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos centre = centre(level, pos);
        if (centre != null && !level.isClientSide()) {
            level.destroyBlock(centre, !player.preventsBlockDrops(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return centre(level, pos) == null ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return RelicBlock.LARGE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Relics.DRAGON_RELIC_ITEM.get());
    }
}
