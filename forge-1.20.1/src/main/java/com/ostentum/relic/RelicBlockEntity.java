package com.ostentum.relic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds no data; it exists so client.RelicRenderer can draw the relic. */
public class RelicBlockEntity extends BlockEntity {
    public RelicBlockEntity(BlockPos pos, BlockState state) {
        super(Relics.RELIC.get(), pos, state);
    }

    /** The likeness rises well above the block, and the 3x3 spreads a block to each side. */
    @Override
    public AABB getRenderBoundingBox() {
        boolean large = getBlockState().getBlock() instanceof RelicBlock relic && relic.large();
        AABB box = new AABB(getBlockPos());
        return large ? box.inflate(1.5, 0.0, 1.5).expandTowards(0.0, 4.5, 0.0) : box.inflate(0.25, 0.0, 0.25).expandTowards(0.0, 1.5, 0.0);
    }
}
