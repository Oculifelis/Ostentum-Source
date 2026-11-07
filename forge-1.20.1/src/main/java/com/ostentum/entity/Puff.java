package com.ostentum.entity;

import com.fundamentum.damage.SharedDamage;
import com.fundamentum.summon.Summons;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What the flying summons shoot: a purple dragon puff (arcane damage) or a squirt of water (water damage, slight arc).
 * It is drawn only as particles. Damage is the shooter's attack damage, so Fundamentum's summon_damage scales it.
 */
public class Puff extends Projectile {
    public enum Kind { DRAGON, WATER }

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(Puff.class, EntityDataSerializers.BYTE);

    public Puff(EntityType<? extends Puff> type, Level level) {
        super(type, level);
    }

    public Puff(Level level, LivingEntity owner, Kind kind) {
        this(ModEntities.PUFF.get(), level);
        this.setOwner(owner);
        this.entityData.set(KIND, (byte) kind.ordinal());
        this.setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
    }

    public Kind kind() {
        return Kind.values()[Math.floorMod(this.entityData.get(KIND), Kind.values().length)];
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, (byte) 0);
    }

    @Override
    protected double getDefaultGravity() {
        return this.kind() == Kind.WATER ? 0.03 : 0.0;
    }

    private ParticleOptions particle() {
        return this.kind() == Kind.WATER ? ParticleTypes.SPLASH : ParticleTypes.DRAGON_BREATH;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 move = this.getDeltaMovement();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !net.minecraftforge.event.EventHooks.onProjectileImpact(this, hit)) {
            this.hitTargetOrDeflectSelf(hit);
        }
        if (this.isRemoved()) {
            return;
        }
        if (this.level().isClientSide()) {
            for (int i = 0; i < 2; i++) {
                this.level().addParticle(this.particle(), this.getX() - move.x * i * 0.5, this.getY() - move.y * i * 0.5,
                    this.getZ() - move.z * i * 0.5, 0.0, 0.0, 0.0);
            }
            if (this.kind() == Kind.WATER) {
                this.level().addParticle(ParticleTypes.DRIPPING_WATER, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            }
        } else if (this.tickCount > 60 || (this.kind() == Kind.DRAGON && this.isInWater())) {
            this.discard();
            return;
        }
        this.updateRotation();
        this.setDeltaMovement(move.scale(0.99));
        this.applyGravity();
        this.setPos(this.getX() + move.x, this.getY() + move.y, this.getZ() + move.z);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(this.getOwner() != null && Summons.allies(this.getOwner(), entity));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level() instanceof ServerLevel level && this.getOwner() instanceof LivingEntity owner) {
            float damage = owner.getAttribute(Attributes.ATTACK_DAMAGE) != null ? (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE) : 3.0F;
            result.getEntity().hurt(SharedDamage.source(level, this.kind() == Kind.WATER ? SharedDamage.WATER : SharedDamage.ARCANE, this, owner), damage);
            this.burst(level);
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level() instanceof ServerLevel level) {
            this.burst(level);
            this.discard();
        }
    }

    private void burst(ServerLevel level) {
        level.sendParticles(this.particle(), this.getX(), this.getY(), this.getZ(), 8, 0.15, 0.15, 0.15, 0.02);
    }
}
