package com.ostentum.entity;

import com.fundamentum.summon.Summons;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A small flying summon that shoots {@link Puff}s. Fundamentum's summon system picks its targets and keeps it near its
 * player; this class only flies and shoots.
 */
abstract class FlyingMinion extends PathfinderMob implements RangedAttackMob {
    protected FlyingMinion(EntityType<? extends FlyingMinion> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    static AttributeSupplier.Builder attributes(double health, double damage) {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, health).add(Attributes.ATTACK_DAMAGE, damage)
            .add(Attributes.FLYING_SPEED, 0.6).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new RangedAttackGoal(this, 1.2, 30, 12.0F));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    abstract Puff.Kind puff();

    abstract SoundEvent shootSound();

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        Puff puff = new Puff(this.level(), this, this.puff());
        double dx = target.getX() - this.getX(), dy = target.getY(0.5) - puff.getY(), dz = target.getZ() - this.getZ();
        puff.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * (this.puff() == Puff.Kind.WATER ? 0.12 : 0.0), dz, 1.0F, 2.0F);
        this.playSound(this.shootSound(), 0.5F, 0.9F + this.random.nextFloat() * 0.2F);     // 1.9.3: was pitched up to 1.6, too shrill
        this.level().addFreshEntity(puff);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return super.canAttack(target) && !Summons.allies(this, target);
    }
}
