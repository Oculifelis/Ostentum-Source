package com.ostentum.entity;

import com.fundamentum.summon.Summons;
import com.ostentum.Minions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Guardian Totem: a small totem that floats at its player's right shoulder. Once per Minecraft day it saves them from
 * death (see {@link Minions}); until the next sunrise it is spent and shows grey. It never fights and cannot be hurt.
 */
public class GuardianTotem extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> READY = SynchedEntityData.defineId(GuardianTotem.class, EntityDataSerializers.BOOLEAN);

    public GuardianTotem(EntityType<? extends GuardianTotem> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setPermanentlyInvulnerable(true);
    }

    static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(READY, true);
    }

    public boolean ready() {
        return this.entityData.get(READY);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        ServerPlayer owner = Summons.owner(this);
        if (owner == null) {
            return;
        }
        this.entityData.set(READY, Minions.totemReady(owner));
        float yaw = owner.yBodyRot * Mth.DEG_TO_RAD;
        // the right shoulder, a little behind: right is (-cos, -sin) and forward (-sin, cos) for a yaw
        Vec3 spot = new Vec3(owner.getX() - Mth.cos(yaw) * 0.7 + Mth.sin(yaw) * 0.25, owner.getY() + owner.getBbHeight() * 0.8,
            owner.getZ() - Mth.sin(yaw) * 0.7 - Mth.cos(yaw) * 0.25);
        Vec3 now = this.position();
        this.setPos(now.add(spot.subtract(now).scale(0.35)));
        this.setDeltaMovement(Vec3.ZERO);
        this.setYRot(owner.getYRot());
        this.yBodyRot = owner.yBodyRot;
        this.yHeadRot = owner.getYHeadRot();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
