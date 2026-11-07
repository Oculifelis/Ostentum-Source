package com.ostentum.weapon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.neoforged.neoforge.common.SimpleTier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Duelist's Saber, sold only by master weaponsmiths. 1.9.4: hold use to guard (a shield-like stance that slows you,
 * {@code assets/ostentum/player_pose/duelists_saber.json}). While guarding, melee and projectile damage from the front
 * is halved; in the first {@link #PERFECT} ticks of a guard it is cancelled instead (the perfect parry): melee
 * attackers are staggered (slowed and weakened) for a second, projectiles bounce back, and the next hit within a second
 * is a 1.5x crit (the riposte). Letting go of a guard that caught nothing costs a {@link #WHIFF}-tick cooldown.
 * A spark and a soft ring mark the perfect window opening; a parry rings out with a ring of sparks.
 */
public final class DuelistsSaber {
    static final Tier MATERIAL = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 700, 7.0F, 2.5F, 18, () -> net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.COPPER_INGOT));

    public static final DeferredItem<Item> SABER = Weapons.item("duelists_saber", Saber::new, p -> p.rarity(Rarity.RARE)
        .sword(MATERIAL, 3.0F, -2.2F));

    /** Perfect-parry window at the start of a guard, riposte time and whiff cooldown, in ticks. */
    public static final int PERFECT = 10;
    static final int RIPOSTE = 20;
    public static final int WHIFF = 10;
    /** Damage taken through the guard (after the perfect window). */
    public static final float GUARDED = 0.5F;

    /** When each guarding player's guard went up, and whether it has caught anything yet. */
    private static final Map<UUID, Long> GUARD = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> CAUGHT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> RIPOSTE_UNTIL = new ConcurrentHashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener(DuelistsSaber::incoming);
        NeoForge.EVENT_BUS.addListener(DuelistsSaber::trade);
    }

    /** A master weaponsmith sells it: 28 emeralds, 3 uses, 30 xp (26.x's villager_trade/weaponsmith/5 data, an event on 1.21.1). */
    private static void trade(net.neoforged.neoforge.event.village.VillagerTradesEvent event) {
        if (event.getType() == net.minecraft.world.entity.npc.VillagerProfession.WEAPONSMITH) {
            event.getTrades().get(5).add((trader, random) -> new net.minecraft.world.item.trading.MerchantOffer(
                new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.EMERALD, 28), new ItemStack(SABER.get()), 3, 30, 0.2F));
        }
    }

    static final class Saber extends Item {
        Saber(Properties properties) {
            super(properties);
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return UseAnim.BLOCK;
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity user) {
            return 72000;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            player.startUsingItem(hand);
            if (level instanceof ServerLevel server) {
                guard(server, player);
            }
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
            if (entity instanceof Player player && level instanceof ServerLevel) {
                release(player, stack);
            }
        }

        @Override
        public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
            // switched away or interrupted: the guard is down, without the whiff cooldown
            if (entity instanceof Player player) {
                GUARD.remove(player.getUUID());
                CAUGHT.remove(player.getUUID());
            }
        }
    }

    /** The guard goes up: the perfect window opens now, marked by a spark and a soft ring. */
    public static void guard(ServerLevel level, Player player) {
        GUARD.put(player.getUUID(), level.getGameTime());
        CAUGHT.put(player.getUUID(), false);
        Vec3 spark = blade(player);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, spark.x, spark.y, spark.z, 6, 0.08, 0.08, 0.08, 0.12);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.45F, 1.9F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.7F);
    }

    /** Test hook: the guard as if it went up at {@code gameTime}. */
    static void guardSince(Player player, long gameTime) {
        GUARD.put(player.getUUID(), gameTime);
    }

    /** The guard comes down: a guard that caught nothing costs the whiff cooldown. Returns whether it did. */
    public static boolean release(Player player, ItemStack stack) {
        Long since = GUARD.remove(player.getUUID());
        Boolean caught = CAUGHT.remove(player.getUUID());
        if (since != null && !Boolean.TRUE.equals(caught)) {
            player.getCooldowns().addCooldown(stack.getItem(), WHIFF);
            return true;
        }
        return false;
    }

    /** Whether {@code player} holds the saber's guard up at all. */
    public static boolean guarding(Player player) {
        return GUARD.containsKey(player.getUUID()) && player.isUsingItem() && player.getUseItem().is(SABER.get());
    }

    /** Whether the guard is in its perfect-parry window at {@code gameTime}: its first PERFECT ticks. */
    public static boolean perfect(Player player, long gameTime) {
        Long since = GUARD.get(player.getUUID());
        return since != null && gameTime >= since && gameTime - since < PERFECT;
    }

    public static boolean riposteReady(Player player, long gameTime) {
        return RIPOSTE_UNTIL.getOrDefault(player.getUUID(), -1L) >= gameTime;
    }

    private static Vec3 blade(Player player) {
        return player.getEyePosition().add(player.getLookAngle().scale(0.8)).add(0, -0.2, 0);
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
        // the defender's guard
        if (event.getEntity() instanceof Player player && guarding(player) && source.getDirectEntity() != null && fromFront(player, source)) {
            boolean melee = source.getDirectEntity() == source.getEntity() && source.getDirectEntity() instanceof LivingEntity;
            boolean shot = source.getDirectEntity() instanceof Projectile;
            if (!melee && !shot) {
                return;
            }
            CAUGHT.put(player.getUUID(), true);
            Vec3 spark = blade(player);
            if (!perfect(player, now)) {
                // a plain guard: half the damage gets through
                event.setAmount(event.getAmount() * GUARDED);
                level.sendParticles(ParticleTypes.CRIT, spark.x, spark.y, spark.z, 4, 0.1, 0.1, 0.1, 0.2);
                level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7F, 1.1F);
                return;
            }
            // the perfect parry
            event.setCanceled(true);
            RIPOSTE_UNTIL.put(player.getUUID(), now + RIPOSTE);
            if (melee) {
                LivingEntity attacker = (LivingEntity) source.getDirectEntity();
                attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 1));
                attacker.knockback(0.4, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
                attacker.hurtMarked = true;
            } else {
                Projectile projectile = (Projectile) source.getDirectEntity();
                projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.0));
                projectile.setOwner(player);
                projectile.hurtMarked = true;
            }
            ring(level, player, spark);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, spark.x, spark.y, spark.z, 10, 0.15, 0.15, 0.15, 0.2);
            level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 2.0F);
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.9F, 1.5F);
            level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.3F);
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

    /** A ring of sparks round the blade, square to where the parrying player looks. */
    private static void ring(ServerLevel level, Player player, Vec3 at) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 side = look.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(look).normalize();
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            Vec3 p = at.add(side.scale(Math.cos(a) * 0.55)).add(up.scale(Math.sin(a) * 0.55));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
        }
    }

    private DuelistsSaber() {}
}
