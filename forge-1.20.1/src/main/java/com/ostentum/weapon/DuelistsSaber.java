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
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeTier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Duelist's Saber, sold only by master weaponsmiths. Use opens a parry window of {@link #WINDOW} ticks: melee or
 * projectile damage from the front is cancelled, melee attackers are staggered (slowed and weakened) for a second and
 * projectiles bounce back. The next hit within a second of a parry is a 1.5x crit (the riposte). A parry that catches
 * nothing costs a one-second cooldown.
 */
public final class DuelistsSaber {
    static final Tier MATERIAL = new ForgeTier(2, 700, 7.0F, 2.5F, 18, BlockTags.NEEDS_IRON_TOOL, () -> net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.COPPER_INGOT));

    public static final RegistryObject<Item> SABER = Weapons.item("duelists_saber", Saber::new, p -> p.rarity(Rarity.RARE)
        .sword(MATERIAL, 3.0F, -2.2F));

    /** Parry window, riposte time and whiff cooldown, in ticks. */
    public static final int WINDOW = 7;
    static final int RIPOSTE = 20;
    static final int WHIFF = 20;

    private static final Map<UUID, Long> OPENED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RIPOSTE_UNTIL = new ConcurrentHashMap<>();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(DuelistsSaber::incoming);
        MinecraftForge.EVENT_BUS.addListener(DuelistsSaber::riposte);
        MinecraftForge.EVENT_BUS.addListener(DuelistsSaber::trade);
    }

    /** A master weaponsmith sells it: 28 emeralds, 3 uses, 30 xp (26.x's villager_trade/weaponsmith/5 data, an event on 1.21.1). */
    private static void trade(net.minecraftforge.event.village.VillagerTradesEvent event) {
        if (event.getType() == net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH) {
            event.getTrades().get(5).add((trader, random) -> new net.minecraft.world.item.trading.MerchantOffer(
                new ItemStack(net.minecraft.world.item.Items.EMERALD, 28), new ItemStack(SABER.get()), 3, 30, 0.2F));
        }
    }

    static final class Saber extends Item {
        Saber(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (level instanceof ServerLevel server) {
                open(player, server.getGameTime());
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.6F);
                long opened = server.getGameTime();
                Weapons.later(server, WINDOW + 1, () -> {
                    // still the same window and nothing parried in it: the whiff cooldown
                    if (OPENED.getOrDefault(player.getUUID(), -1L) == opened) {
                        OPENED.remove(player.getUUID());
                        player.getCooldowns().addCooldown(player.getItemInHand(hand).getItem(), WHIFF);
                    }
                });
            }
            return InteractionResultHolder.success(player.getItemInHand(hand));
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

    /** The defender's parry (1.20.1: LivingAttackEvent, before any damage is worked out). */
    private static void incoming(LivingAttackEvent event) {
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
                    attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                    attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 1));
                    attacker.knockback(0.4, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
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
                level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.3F);
            }
        }
    }

    /** The attacker's riposte (1.20.1: LivingHurtEvent, which can change the amount). */
    private static void riposte(LivingHurtEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        DamageSource source = event.getSource();
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
