package com.ostentum;

import com.fundamentum.curios.Slots;
import com.fundamentum.damage.SharedDamage;
import com.fundamentum.summon.Summons;
import com.ostentum.entity.GuardianTotem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.TickEvent;

/**
 * What Ostentum adds to Fundamentum's summons for the vanilla creatures it calls: half-size bees that come back after
 * they sting, a baby iron golem and baby creeper (sized here, while the owner wears the matching item), the allay's
 * note attack, ice-damage snowballs, a creeper pop that breaks no blocks, and the Guardian Totem's once-a-day save.
 */
public final class Minions {
    private Minions() {}

    /** Player persistent-data key: the day number the Guardian Totem last saved them. */
    private static final String TOTEM_USED = "ostentum:totem_used_day";

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(Minions::size);
        MinecraftForge.EVENT_BUS.addListener(Minions::tick);
        MinecraftForge.EVENT_BUS.addListener(Minions::snowball);
        MinecraftForge.EVENT_BUS.addListener(Minions::explosion);
        // before Fundamentum's own death listener, which would dismiss the party
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Minions::guardian);
    }

    private static boolean wears(Player player, String item) {
        for (ItemStack stack : Slots.allEquipped(player, Slots.SUMMON)) {
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(Ostentum.id(item))) {
                return true;
            }
        }
        return false;
    }

    /** A freshly summoned minion: shrink the babies, and let the allay fight only with its note. */
    private static void size(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide() || !(entity instanceof Mob mob) || !Summons.isMinion(mob)) {
            return;
        }
        ServerPlayer owner = Summons.owner(mob);
        if (owner == null) {
            return;
        }
        if (mob instanceof IronGolem && wears(owner, "golem_heart")) {
            scale(mob, 0.4);
        } else if (mob instanceof Creeper && wears(owner, "creeper_sprout")) {
            scale(mob, 0.5);
        } else if (mob instanceof Bee && wears(owner, "hive_charm")) {
            scale(mob, 0.5);
        } else if (mob instanceof Allay) {
            mob.goalSelector.removeAllGoals(g -> g instanceof MeleeAttackGoal);
        }
    }

    private static void scale(Mob mob, double value) {
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(value);
        }
    }

    private static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (Mob mob : Summons.minions(player)) {
            if (mob instanceof Bee bee) {
                bee(level, bee);
            } else if (mob instanceof Allay allay) {
                allay(level, player, allay);
            }
        }
    }

    /** Bees get angry at their target; one that has stung pops away a second later and Fundamentum calls it back. */
    private static void bee(ServerLevel level, Bee bee) {
        LivingEntity target = bee.getTarget();
        if (target != null && !bee.isAngry()) {
            bee.setPersistentAngerTarget(target.getUUID());
            bee.startPersistentAngerTimer();
        }
        if (bee.hasStung() && bee.tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.POOF, bee.getX(), bee.getY(0.5), bee.getZ(), 5, 0.1, 0.1, 0.1, 0.02);
            bee.discard();
        }
    }

    /** The allay follows its player and, every 25 ticks, hurts its target with a sharp note (air damage). */
    private static void allay(ServerLevel level, ServerPlayer player, Allay allay) {
        allay.getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, player.getUUID());
        LivingEntity target = allay.getTarget();
        if (target == null || !target.isAlive() || allay.tickCount % 25 != 0 || allay.distanceToSqr(target) > 10 * 10 || !allay.hasLineOfSight(target)) {
            return;
        }
        float damage = (float) allay.getAttributeValue(Attributes.ATTACK_DAMAGE);
        target.hurt(SharedDamage.source(level, SharedDamage.AIR, allay, allay), damage);
        for (int i = 1; i <= 4; i++) {
            double t = i / 4.0;
            level.sendParticles(ParticleTypes.NOTE, allay.getX() + (target.getX() - allay.getX()) * t, allay.getY(0.5) + (target.getY(0.6) - allay.getY(0.5)) * t,
                allay.getZ() + (target.getZ() - allay.getZ()) * t, 0, level.getRandom().nextDouble(), 0.0, 0.0, 1.0);
        }
        level.playSound(null, allay.blockPosition(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.NEUTRAL, 0.6F, 1.0F);     // 1.9.3: the allay's own chirp (the bell at 1.6 was too shrill)
    }

    /** A minion snow golem's snowballs deal ice damage. */
    private static void snowball(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof Snowball ball && ball.getOwner() instanceof SnowGolem golem && Summons.isMinion(golem)
            && event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target
            && ball.level() instanceof ServerLevel level && !Summons.allies(golem, target)) {
            target.hurt(SharedDamage.source(level, SharedDamage.ICE, ball, golem), 3.0F);
        }
    }

    /** A minion creeper's pop hurts but breaks no blocks. */
    private static void explosion(ExplosionEvent.Detonate event) {
        if (event.getExplosion().getDirectSourceEntity() instanceof Creeper creeper && Summons.isMinion(creeper)) {
            event.getAffectedBlocks().clear();
        }
    }

    static long day(Level level) {
        return level.getDayTime() / 24000L;
    }

    /** True while a player's Guardian Totem has not saved them yet this Minecraft day. */
    public static boolean totemReady(Player player) {
        return (player.getPersistentData().contains(TOTEM_USED) ? player.getPersistentData().getLong(TOTEM_USED) : -1L) != day(player.level());
    }

    /** The Guardian Totem's save: like a Totem of Undying, without using anything up. */
    private static void guardian(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !totemReady(player)
            || Summons.minions(player).stream().noneMatch(m -> m instanceof GuardianTotem)) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(1.0F);
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        player.level().broadcastEntityEvent(player, EntityEvent.TALISMAN_ACTIVATE);
        player.getPersistentData().putLong(TOTEM_USED, day(player.level()));
    }
}
