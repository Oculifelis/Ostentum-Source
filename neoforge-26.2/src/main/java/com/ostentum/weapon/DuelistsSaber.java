package com.ostentum.weapon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Duelist's Saber, sold only by master weaponsmiths. Use opens a parry window of {@link #WINDOW} ticks: melee or
 * projectile damage from the front is cancelled, melee attackers are staggered (slowed and weakened) for a second and
 * projectiles bounce back. The next hit within a second of a parry is a 1.5x crit (the riposte). A parry that catches
 * nothing costs a one-second cooldown.
 */
public final class DuelistsSaber {
    static final ToolMaterial MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 700, 7.0F, 2.5F, 18, ItemTags.COPPER_TOOL_MATERIALS);

    public static final DeferredItem<Item> SABER = Weapons.item("duelists_saber", Saber::new, p -> p.rarity(Rarity.RARE)
        .sword(MATERIAL, 3.0F, -2.2F));

    /** Parry window, riposte time and whiff cooldown, in ticks. */
    public static final int WINDOW = 7;
    static final int RIPOSTE = 20;
    static final int WHIFF = 20;

    private static final Map<UUID, Long> OPENED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RIPOSTE_UNTIL = new ConcurrentHashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener(DuelistsSaber::incoming);
    }

    static final class Saber extends Item {
        Saber(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (level instanceof ServerLevel server) {
                open(player, server.getGameTime());
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.6F);
                long opened = server.getGameTime();
                Weapons.later(server, WINDOW + 1, () -> {
                    // still the same window and nothing parried in it: the whiff cooldown
                    if (OPENED.getOrDefault(player.getUUID(), -1L) == opened) {
                        OPENED.remove(player.getUUID());
                        player.getCooldowns().addCooldown(player.getItemInHand(hand), WHIFF);
                    }
                });
            }
            return InteractionResult.SUCCESS;
        }
    }

    public static void open(Player player, long gameTime) {
        OPENED.put(player.getUUID(), gameTime);
    }

    /** Whether a parry window opened at some tick still covers {@code gameTime}: ticks 0 to WINDOW - 1 after opening. */
    public static boolean parrying(Player player, long gameTime) {
        Long opened = OPENED.get(player.getUUID());
        return opened != null && gameTime >= opened && gameTime - opened < WINDOW;
    }

    public static boolean riposteReady(Player player, long gameTime) {
        return RIPOSTE_UNTIL.getOrDefault(player.getUUID(), -1L) >= gameTime;
    }

    /** Whether the damage comes from in front of the player (within about 70 degrees of where they look). */
    static boolean fromFront(Player player, DamageSource source) {
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return false;
        }
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 to = from.subtract(player.position()).multiply(1, 0, 1).normalize();
        return look.dot(to) > 0.35;
    }

    private static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        DamageSource source = event.getSource();
        // the defender's parry
        if (event.getEntity() instanceof Player player && Weapons.holds(player, SABER) && parrying(player, now)
            && source.getDirectEntity() != null && fromFront(player, source)) {
            boolean melee = source.getDirectEntity() == source.getEntity() && source.getDirectEntity() instanceof LivingEntity;
            boolean shot = source.getDirectEntity() instanceof Projectile;
            if (melee || shot) {
                event.setCanceled(true);
                OPENED.remove(player.getUUID());
                RIPOSTE_UNTIL.put(player.getUUID(), now + RIPOSTE);
                if (melee) {
                    LivingEntity attacker = (LivingEntity) source.getDirectEntity();
                    attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 2));
                    attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 1));
                    attacker.knockback(0.4, player.getX() - attacker.getX(), player.getZ() - attacker.getZ(), player.damageSources().playerAttack(player), 0.0F);
                } else {
                    Projectile projectile = (Projectile) source.getDirectEntity();
                    projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.0));
                    projectile.setOwner(player);
                    projectile.hurtMarked = true;
                }
                Vec3 spark = player.getEyePosition().add(player.getLookAngle().scale(0.8));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, spark.x, spark.y, spark.z, 10, 0.15, 0.15, 0.15, 0.2);
                level.sendParticles(ParticleTypes.CRIT, spark.x, spark.y, spark.z, 8, 0.1, 0.1, 0.1, 0.3);
                level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.8F);
                level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
            }
            return;
        }
        // the attacker's riposte
        if (source.getEntity() instanceof Player player && source.getDirectEntity() == player && Weapons.holds(player, SABER)
            && riposteReady(player, now)) {
            RIPOSTE_UNTIL.remove(player.getUUID());
            event.setAmount(event.getAmount() * 1.5F);
            LivingEntity target = event.getEntity();
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 16, 0.3, 0.4, 0.3, 0.3);
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }

    private DuelistsSaber() {}
}
