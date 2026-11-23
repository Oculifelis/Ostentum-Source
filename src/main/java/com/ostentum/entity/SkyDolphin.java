package com.ostentum.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Pink Pearl: a baby pink dolphin that swims through the air and squirts water (water damage). */
public class SkyDolphin extends FlyingMinion {
    public SkyDolphin(EntityType<? extends SkyDolphin> type, Level level) {
        super(type, level);
    }

    @Override
    Puff.Kind puff() {
        return Puff.Kind.WATER;
    }

    @Override
    SoundEvent shootSound() {
        return SoundEvents.BUCKET_EMPTY;     // 1.9.3: a water squirt
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.DOLPHIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.DOLPHIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.DOLPHIN_DEATH;
    }
}
