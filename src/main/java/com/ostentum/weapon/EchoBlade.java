package com.ostentum.weapon;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Echo Blade: a netherite sword smithed with a Resonant Hilt (found only in Ancient City chests) and an echo shard.
 * Every hit repeats as a sculk echo {@link #DELAY} ticks later for half the damage. While its wielder sneaks, its hits
 * and kills make no vibrations, so sculk sensors and shriekers stay quiet.
 */
public final class EchoBlade {
    public static final DeferredItem<Item> HILT = Weapons.item("resonant_hilt", Item::new, p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<Item> BLADE = Weapons.item("echo_blade", Item::new, p -> p.rarity(Rarity.EPIC).fireResistant()
        .sword(ToolMaterial.NETHERITE, 3.0F, -2.4F));

    public static final int DELAY = 30;
    public static final float SHARE = 0.5F;

    private static boolean echoing;

    static void init() {
        NeoForge.EVENT_BUS.addListener(EchoBlade::hit);
        NeoForge.EVENT_BUS.addListener(EchoBlade::quiet);
    }

    private static void hit(LivingDamageEvent.Post event) {
        if (echoing || !(event.getSource().getEntity() instanceof Player player) || event.getSource().getDirectEntity() != player
            || !Weapons.holds(player, BLADE) || !(player.level() instanceof ServerLevel level) || event.getInflictedDamage() <= 0) {
            return;
        }
        LivingEntity target = event.getEntity();
        float echo = event.getInflictedDamage() * SHARE;
        Weapons.later(level, DELAY, () -> echo(level, player, target, echo));
    }

    /** The echo itself: the same hit again, at {@code amount}, past the target's hurt cooldown. */
    public static void echo(ServerLevel level, Player player, LivingEntity target, float amount) {
        if (!target.isAlive() || target.level() != level) {
            return;
        }
        echoing = true;
        try {
            target.damageCooldownTime = 0;
            target.hurtServer(level, level.damageSources().playerAttack(player), amount);
        } finally {
            echoing = false;
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, target.getX(), target.getY(0.5), target.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    static boolean silent(Player player) {
        return player.isShiftKeyDown() && Weapons.holds(player, BLADE);
    }

    /** No vibrations from a sneaking wielder, or from a mob they just hit (its hurt and death events). */
    private static void quiet(VanillaGameEvent event) {
        if (event.getCause() instanceof Player player && silent(player)) {
            event.setCanceled(true);
        } else if (event.getCause() instanceof LivingEntity victim && victim.getLastHurtByMob() instanceof Player player && silent(player)
            && victim.tickCount - victim.getLastHurtByMobTimestamp() < 40) {
            event.setCanceled(true);
        }
    }

    private EchoBlade() {}
}
