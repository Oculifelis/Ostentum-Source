package com.ostentum.weapon;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import com.ostentum.Ostentum;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * Voidstring Bow (shulker shells, end rods, an ender pearl). Its arrows ignore gravity and curve gently toward the
 * nearest mob ahead, shulker-bullet style; a hit gives a second of Levitation. A fully drawn arrow that hits swaps your
 * place with the target ({@link #SWAP_COOLDOWN} ticks' cooldown, one extra durability); one that kills moves you to
 * where it fell. Never into blocks: each end lands on the nearest spot its box fits.
 *
 * <p>1.9.4: players too, under the PvP rules (never the shooter; server pvp and team friendly fire respected), unless
 * the game rule {@code ostentum:voidstring_affects_players} is off, when players just take the arrow's damage. Entity
 * types in {@code #ostentum:voidstring_immune} (the bosses) never get the levitation, the swap or the kill teleport.
 */
public final class VoidstringBow {
    public static final DeferredItem<BowItem> BOW = Weapons.item("voidstring_bow", Bow::new, p -> p.durability(500).enchantable(1).rarity(Rarity.RARE));

    /** Entity types the bow's effect never touches. Packs may add minecraft:player. */
    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE, Ostentum.id("voidstring_immune"));

    private static final DeferredRegister<GameRule<?>> RULES = DeferredRegister.create(Registries.GAME_RULE, Ostentum.MODID);
    /** /gamerule ostentum:voidstring_affects_players (on): off, players take an arrow's damage and nothing else. */
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> AFFECTS_PLAYERS = RULES.register("voidstring_affects_players",
        () -> new GameRule<>(GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean, Codec.BOOL,
            b -> b ? 1 : 0, true, FeatureFlagSet.of()));

    static final String TAG = "ostentum_void";
    static final String FULL = "ostentum_void_full";
    public static final int SWAP_COOLDOWN = 80;
    static final double SEEK = 16.0;


    static void init() {
        IEventBus modBus = net.neoforged.fml.ModList.get().getModContainerById(Ostentum.MODID).orElseThrow().getEventBus();
        RULES.register(modBus);
        NeoForge.EVENT_BUS.addListener(VoidstringBow::steer);
        NeoForge.EVENT_BUS.addListener(VoidstringBow::hit);
    }

    static final class Bow extends BowItem {
        Bow(Properties properties) {
            super(properties);
        }

        /** The draw sound (1.9.5), once, on the first tick of pulling the string. */
        @Override
        public void onUseTick(net.minecraft.world.level.Level level, LivingEntity entity, ItemStack stack, int remaining) {
            super.onUseTick(level, entity, stack, remaining);
            if (!level.isClientSide() && getUseDuration(stack, entity) - remaining == 1) {
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), com.ostentum.OstentumSounds.VOIDSTRING_DRAW.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }

        /** Vanilla's release, with the Voidstring shot (1.9.5) in place of the arrow sound. */
        @Override
        public boolean releaseUsing(ItemStack stack, net.minecraft.world.level.Level level, LivingEntity entity, int remaining) {
            if (!(entity instanceof Player player)) {
                return false;
            }
            ItemStack projectile = player.getProjectile(stack);
            if (projectile.isEmpty()) {
                return false;
            }
            int held = net.neoforged.neoforge.event.EventHooks.onArrowLoose(stack, level, player, getUseDuration(stack, entity) - remaining, true);
            if (held < 0) {
                return false;
            }
            float pow = getPowerForTime(held);
            if (pow < 0.1F) {
                return false;
            }
            java.util.List<ItemStack> fired = draw(stack, projectile, player);
            if (level instanceof ServerLevel server && !fired.isEmpty()) {
                shoot(server, player, player.getUsedItemHand(), stack, fired, pow * 3.0F, 1.0F, pow == 1.0F, null);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), com.ostentum.OstentumSounds.VOIDSTRING_SHOOT.get(),
                SoundSource.PLAYERS, 1.0F, 0.95F + level.getRandom().nextFloat() * 0.1F);
            player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
            return true;
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
        return entity.getType().builtInRegistryHolder().is(IMMUNE);
    }

    public static boolean affectsPlayers(ServerLevel level) {
        return level.getGameRules().get(AFFECTS_PLAYERS.get());
    }

    /**
     * Whether the bow's effect (levitation, swap, kill teleport) may touch {@code target} when {@code shooter} hit it:
     * never the shooter or an immune type; a player only with the game rule on, PvP allowed and no friendly team.
     */
    public static boolean affects(ServerLevel level, @Nullable LivingEntity shooter, LivingEntity target) {
        if (target == shooter || swapImmune(target)) {
            return false;
        }
        if (target instanceof Player victim) {
            if (!affectsPlayers(level) || victim.isSpectator() || victim.isCreative()) {
                return false;
            }
            if (shooter instanceof Player attacker) {
                // the server's pvp switch and vanilla's team rule, spelled out (so a mod's fake player is judged the same)
                var team = attacker.getTeam();
                return level.isPvpAllowed() && (team == null || !team.isAlliedTo(victim.getTeam()) || team.isAllowFriendlyFire());
            }
        }
        return true;
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
            .stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(arrow))).orElse(null);
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
        LivingEntity shooter = arrow.getOwner() instanceof LivingEntity owner ? owner : null;
        land(level, shooter, event.getEntity(), arrow.entityTags().contains(FULL));
    }

    /**
     * A voidstring arrow hit {@code target} (already damaged): levitation and, fully drawn, the swap; or, if it died,
     * the kill teleport. Nothing for targets the effect can't touch. Returns whether the effect applied.
     */
    public static boolean land(ServerLevel level, @Nullable LivingEntity shooter, LivingEntity target, boolean full) {
        if (!affects(level, shooter, target)) {
            return false;
        }
        if (target.isDeadOrDying()) {
            if (full && shooter instanceof Player player && player.level() == level) {
                killTeleport(level, player, target);
            }
            return true;
        }
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 0));
        if (full && shooter instanceof Player player && player.level() == level) {
            swap(level, player, target);
        }
        return true;
    }

    private static @Nullable InteractionHand bowHand(Player player) {
        return player.getMainHandItem().is(BOW.get()) ? InteractionHand.MAIN_HAND : player.getOffhandItem().is(BOW.get()) ? InteractionHand.OFF_HAND : null;
    }

    /** The full-draw swap: you and {@code target} trade places, unless it is immune or the bow is cooling down. */
    public static boolean swap(ServerLevel level, Player player, LivingEntity target) {
        InteractionHand hand = bowHand(player);
        if (hand == null || !affects(level, player, target) || !target.isAlive() || target.isPassenger() || target.isVehicle()
            || player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
            return false;
        }
        Vec3 mine = player.position();
        Vec3 theirs = target.position();
        Vec3 playerTo = safeSpot(level, player, theirs);
        Vec3 targetTo = safeSpot(level, target, mine);
        if (playerTo == null || targetTo == null) {
            return false;
        }
        player.stopRiding();
        player.teleportTo(playerTo.x, playerTo.y, playerTo.z);
        target.teleportTo(targetTo.x, targetTo.y, targetTo.z);
        spent(level, player, hand, mine, theirs);
        return true;
    }

    /**
     * The kill teleport: the shooter moves to where {@code dead} fell (the nearest spot that fits), on the swap's
     * cooldown. Returns where the shooter went, or null when nothing happened.
     */
    public static @Nullable Vec3 killTeleport(ServerLevel level, Player player, LivingEntity dead) {
        InteractionHand hand = bowHand(player);
        if (hand == null || !affects(level, player, dead) || player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
            return null;
        }
        Vec3 mine = player.position();
        Vec3 to = safeSpot(level, player, dead.position());
        if (to == null) {
            return null;
        }
        player.stopRiding();
        player.teleportTo(to.x, to.y, to.z);
        player.resetFallDistance();
        spent(level, player, hand, mine, to);
        return to;
    }

    private static void spent(ServerLevel level, Player player, InteractionHand hand, Vec3 from, Vec3 to) {
        ItemStack bow = player.getItemInHand(hand);
        player.getCooldowns().addCooldown(bow, SWAP_COOLDOWN);
        bow.hurtAndBreak(1, player, hand.asEquipmentSlot());
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 20, 0.3, 0.6, 0.3, 0.05);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y + 1, to.z, 20, 0.3, 0.6, 0.3, 0.05);
        level.playSound(null, BlockPos.containing(to), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * The spot nearest {@code at} where {@code entity}'s box fits without touching a block: {@code at} itself, else up
     * to two blocks round it (spots with a floor under them first). Null when there is none.
     */
    public static @Nullable Vec3 safeSpot(ServerLevel level, LivingEntity entity, Vec3 at) {
        if (fits(level, entity, at)) {
            return at;
        }
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 base = Vec3.atBottomCenterOf(BlockPos.containing(at));
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Vec3 p = base.add(dx, dy, dz);
                    if (!fits(level, entity, p)) {
                        continue;
                    }
                    AABB under = new AABB(p.x - 0.2, p.y - 0.2, p.z - 0.2, p.x + 0.2, p.y - 0.01, p.z + 0.2);
                    double score = p.distanceToSqr(at) + (level.noCollision(under) ? 4.0 : 0.0);
                    if (score < bestScore) {
                        bestScore = score;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    /** Whether {@code entity}'s box at {@code at} touches no block. */
    public static boolean fits(ServerLevel level, LivingEntity entity, Vec3 at) {
        return level.noCollision(entity, entity.getDimensions(entity.getPose()).makeBoundingBox(at).deflate(1.0E-4));
    }

    private VoidstringBow() {}
}
