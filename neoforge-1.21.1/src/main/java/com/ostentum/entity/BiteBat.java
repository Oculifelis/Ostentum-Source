package com.ostentum.entity;

import com.fundamentum.summon.Summons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Bat Wing: a vanilla-looking bat that flutters round its player and bites what they fight. A vanilla bat only flies
 * at random, so this one steers itself: to its target when it has one, otherwise in a circle above its player.
 */
public class BiteBat extends Bat {
    private int biteCooldown;

    public BiteBat(EntityType<? extends Bat> type, Level level) {
        super(type, level);
        this.setResting(false);
    }

    @Override
    protected void customServerAiStep() {
        ServerLevel level = (ServerLevel) this.level();
        this.setResting(false);
        LivingEntity target = this.getTarget();
        ServerPlayer owner = Summons.owner(this);
        Vec3 goal;
        if (target != null && target.isAlive()) {
            goal = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
        } else if (owner != null) {
            float a = this.tickCount * 0.08F + this.getId();
            goal = owner.position().add(Mth.cos(a) * 1.6, 2.3, Mth.sin(a) * 1.6);
        } else {
            return;
        }
        Vec3 d = goal.subtract(this.position());
        Vec3 v = this.getDeltaMovement();
        Vec3 next = v.add((Math.signum(d.x) * 0.5 - v.x) * 0.1, (Math.signum(d.y) * 0.7 - v.y) * 0.1, (Math.signum(d.z) * 0.5 - v.z) * 0.1);
        this.setDeltaMovement(next);
        float yaw = (float) (Mth.atan2(next.z, next.x) * Mth.RAD_TO_DEG) - 90.0F;
        this.setYRot(this.getYRot() + Mth.wrapDegrees(yaw - this.getYRot()));
        this.zza = 0.5F;
        if (this.biteCooldown > 0) {
            this.biteCooldown--;
        } else if (target != null && this.getBoundingBox().inflate(0.3).intersects(target.getBoundingBox())) {
            this.doHurtTarget(target);
            this.biteCooldown = 20;
        }
    }
}
