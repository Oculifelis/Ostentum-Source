package com.ostentum.relic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds no data; it exists so client.RelicRenderer can draw the relic. */
public class RelicBlockEntity extends BlockEntity {
    public RelicBlockEntity(BlockPos pos, BlockState state) {
        super(Relics.RELIC.get(), pos, state);
    }
}
