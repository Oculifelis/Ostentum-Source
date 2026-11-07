package com.ostentum.weapon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Voidstring Bow (shulker shells, end rods, popped chorus fruit). Its arrows ignore gravity and curve gently toward the
 * nearest mob ahead, shulker-bullet style; a hit gives a second of Levitation. A fully drawn arrow that hits a mob swaps
 * your place with it ({@link #SWAP_COOLDOWN} ticks' cooldown, one extra durability); the Ender Dragon, the Wither and
 * the Warden are immune to the swap.
 */
public final class VoidstringBow {
    public static final DeferredItem<BowItem> BOW = Weapons.item("voidstring_bow", Bow::new, p -> p.durability(500).enchantable(1).rarity(Rarity.RARE));

    static final String TAG = "ostentum_void";
    static final String FULL = "ostentum_void_full";
    public static final int SWAP_COOLDOWN = 80;
    static final double SEEK = 16.0;

    static void init() {
        NeoForge.EVENT_BUS.addListener(VoidstringBow::steer);
        NeoForge.EVENT_BUS.addListener(VoidstringBow::hit);
    }

    static final class Bow extends BowItem {
        Bow(Properties properties) {
            super(properties);
        }

        @Override
        public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
            arrow.setNoGravity(true);
            arrow.addTag(TAG);
            if (arrow.isCritArrow()) {
                arrow.addTag(FULL);
            }
            return arrow;
        }
    }

    public static boolean swapImmune(LivingEntity entity) {
        return entity.getType() == EntityTypes.ENDER_DRAGON || entity.getType() == EntityTypes.WITHER || entity.getType() == EntityTypes.WARDEN;
    }

    /** Arrows in flight bend toward the nearest mob ahead of them, and fade out after ten seconds. */
    private static void steer(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level) || !arrow.entityTags().contains(TAG)) {
            return;
        }
        Vec3 motion = arrow.getDeltaMovement();
        if (motion.lengthSqr() < 0.01) {
            // landed
            return;
        }
        if (arrow.tickCount > 200) {
            arrow.discard();
            return;
        }
        if (arrow.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
        Vec3 heading = motion.normalize();
        Mob target = level.getEntitiesOfClass(Mob.class, arrow.getBoundingBox().inflate(SEEK),
                m -> m.isAlive() && m != arrow.getOwner() && heading.dot(m.getBoundingBox().getCenter().subtract(arrow.position()).normalize()) > 0.6)
            .stream().min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(arrow))).orElse(null);
        if (target != null) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(arrow.position()).normalize();
            arrow.setDeltaMovement(heading.lerp(to, 0.12).normalize().scale(motion.length()));
            arrow.needsSync = true;
        }
    }

    private static void hit(LivingDamageEvent.Post event) {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow) || !arrow.entityTags().contains(TAG)
            || !(arrow.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = event.getEntity();
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 0));
        if (arrow.entityTags().contains(FULL) && arrow.getOwner() instanceof Player player && player.level() == level) {
            swap(level, player, target);
        }
    }

    /** The full-draw swap: you and {@code target} trade places, unless it is immune or the bow is cooling down. */
    public static boolean swap(ServerLevel level, Player player, LivingEntity target) {
        InteractionHand hand = player.getMainHandItem().is(BOW.get()) ? InteractionHand.MAIN_HAND
            : player.getOffhandItem().is(BOW.get()) ? InteractionHand.OFF_HAND : null;
        if (hand == null || swapImmune(target) || !target.isAlive() || player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
            return false;
        }
        Vec3 mine = player.position();
        Vec3 theirs = target.position();
        player.teleportTo(theirs.x, theirs.y, theirs.z);
        target.teleportTo(mine.x, mine.y, mine.z);
        ItemStack bow = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(bow, SWAP_COOLDOWN);
        bow.hurtAndBreak(1, player, hand.asEquipmentSlot());
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, mine.x, mine.y + 1, mine.z, 20, 0.3, 0.6, 0.3, 0.05);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, theirs.x, theirs.y + 1, theirs.z, 20, 0.3, 0.6, 0.3, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    private VoidstringBow() {}
}
