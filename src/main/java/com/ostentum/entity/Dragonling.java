package com.ostentum.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Bottled Dragonling: a chibi ender dragon that spits small purple puffs (arcane damage). */
public class Dragonling extends FlyingMinion {
    public Dragonling(EntityType<? extends Dragonling> type, Level level) {
        super(type, level);
    }

    @Override
    Puff.Kind puff() {
        return Puff.Kind.DRAGON;
    }

    @Override
    SoundEvent shootSound() {
        return SoundEvents.SHULKER_SHOOT;     // 1.9.3: a soft arcane puff (the dragon's own shot was too big for it)
    }
}
