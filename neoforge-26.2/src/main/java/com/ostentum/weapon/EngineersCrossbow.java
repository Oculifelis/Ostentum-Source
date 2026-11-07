package com.ostentum.weapon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Engineer's Crossbow (netherite upgrade of a crossbow). Sneak + use while unloaded switches its mode.
 * <ul>
 *   <li>Combat: a {@link #MAGAZINE}-bolt magazine with a slow load; each use fires one bolt.</li>
 *   <li>Rope: shoots two bolts, and once both have landed a rope is strung between them for a minute. Holding the
 *   crossbow near the rope while off the ground rides it like a zipline, downhill or the way you look.</li>
 * </ul>
 */
public final class EngineersCrossbow {
    public static final DeferredItem<Crossbow> CROSSBOW = Weapons.item("engineers_crossbow", Crossbow::new, p -> p.durability(800).enchantable(1)
        .fireResistant().rarity(Rarity.EPIC));

    public static final int MAGAZINE = 5;
    static final int COMBAT_LOAD = 60;
    static final int ROPE_LOAD = 25;
    static final int ROPE_LIFE = 1200;
    static final String ROPE_TAG = "ostentum_rope:";

    /** A rope strung between two landed bolts. */
    public record Rope(ServerLevel level, Vec3 a, Vec3 b, long until) {
        /** The rope point nearest {@code p}, and how far along the rope it is (0 at a, 1 at b). */
        double along(Vec3 p) {
            Vec3 ab = b.subtract(a);
            return Math.max(0, Math.min(1, p.subtract(a).dot(ab) / ab.lengthSqr()));
        }

        Vec3 at(double t) {
            return a.add(b.subtract(a).scale(t));
        }
    }

    /** Each player's rope, and the landing points of a rope pair still in the air. */
    static final Map<UUID, Rope> ROPES = new ConcurrentHashMap<>();
    private static final Map<String, Vec3> LANDED = new ConcurrentHashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener(EngineersCrossbow::landed);
        NeoForge.EVENT_BUS.addListener(EngineersCrossbow::ride);
        NeoForge.EVENT_BUS.addListener(EngineersCrossbow::ropes);
    }

    public static boolean ropeMode(ItemStack stack) {
        return Weapons.data(stack).getBooleanOr("rope", false);
    }

    public static final class Crossbow extends WeaponCrossbow {
        Crossbow(Properties properties) {
            super(properties);
        }

        @Override
        int chargeTicks(ItemStack crossbow, LivingEntity user) {
            return ropeMode(crossbow) ? ROPE_LOAD : COMBAT_LOAD;
        }

        @Override
        List<ItemStack> load(ItemStack crossbow, LivingEntity shooter) {
            return ropeMode(crossbow) ? take(crossbow, shooter, 2, true) : take(crossbow, shooter, MAGAZINE, false);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            if (charged.isEmpty() && player.isShiftKeyDown()) {
                boolean rope = !ropeMode(stack);
                Weapons.edit(stack, tag -> tag.putBoolean("rope", rope));
                player.sendOverlayMessage(Component.translatable("item.ostentum.engineers_crossbow." + (rope ? "rope" : "combat")));
                level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
                return InteractionResult.SUCCESS;
            }
            if (charged.isEmpty()) {
                return super.use(level, player, hand);
            }
            if (level instanceof ServerLevel server) {
                List<ItemStack> bolts = new ArrayList<>(charged.itemCopies());
                if (ropeMode(stack)) {
                    stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
                    fire(server, player, hand, stack, bolts, 3.15F, new float[] {-7.0F, 7.0F});
                } else {
                    ItemStack first = bolts.remove(0);
                    stack.set(DataComponents.CHARGED_PROJECTILES, bolts.isEmpty() ? ChargedProjectiles.EMPTY : ChargedProjectiles.ofNonEmpty(bolts));
                    fire(server, player, hand, stack, List.of(first), 3.15F, new float[] {0.0F});
                    player.getCooldowns().addCooldown(stack, 6);
                }
            }
            return InteractionResult.CONSUME;
        }

        @Override
        void shotFired(ServerLevel level, LivingEntity shooter, Projectile shot, int index) {
            if (shot instanceof AbstractArrow arrow && ropeMode(shooter.getMainHandItem().is(this) ? shooter.getMainHandItem() : shooter.getOffhandItem())) {
                arrow.addTag(ROPE_TAG + shooter.getUUID() + ":" + index);
            }
        }
    }

    /** A rope bolt that lands; when both of a pair have, the shooter's rope is strung. */
    private static void landed(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)
            || !(event.getRayTraceResult() instanceof BlockHitResult hit)) {
            return;
        }
        String tag = arrow.entityTags().stream().filter(t -> t.startsWith(ROPE_TAG)).findFirst().orElse(null);
        if (tag == null) {
            return;
        }
        arrow.removeTag(tag);
        String[] parts = tag.substring(ROPE_TAG.length()).split(":");
        UUID owner = UUID.fromString(parts[0]);
        String other = ROPE_TAG + parts[0] + ":" + (parts[1].equals("0") ? "1" : "0");
        Vec3 here = hit.getLocation();
        Vec3 there = LANDED.remove(other);
        if (there == null) {
            LANDED.put(tag, here);
            return;
        }
        string(level, owner, there, here);
    }

    public static void string(ServerLevel level, UUID owner, Vec3 a, Vec3 b) {
        if (a.distanceToSqr(b) < 4.0) {
            return;
        }
        ROPES.put(owner, new Rope(level, a, b, level.getGameTime() + ROPE_LIFE));
        level.playSound(null, net.minecraft.core.BlockPos.containing(b), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Ropes fade after a minute; the live ones are drawn as a line of dust every few ticks. */
    private static void ropes(ServerTickEvent.Post event) {
        if (ROPES.isEmpty()) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(0x8A6A3A, 0.8F);
        for (Iterator<Rope> it = ROPES.values().iterator(); it.hasNext(); ) {
            Rope rope = it.next();
            long now = rope.level().getGameTime();
            if (now > rope.until()) {
                it.remove();
                continue;
            }
            if (now % 4 != 0) {
                continue;
            }
            int steps = (int) Math.ceil(rope.a().distanceTo(rope.b()) * 3);
            for (int i = 0; i <= steps; i++) {
                Vec3 p = rope.at((double) i / steps);
                rope.level().sendParticles(dust, true, false, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** The zipline: holding the crossbow off the ground within reach of a rope hangs you under it and slides you along. */
    private static void ride(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.onGround() || player.isShiftKeyDown() || ROPES.isEmpty()
            || !(player.getMainHandItem().is(CROSSBOW.get()) || player.getOffhandItem().is(CROSSBOW.get()))) {
            return;
        }
        Vec3 hands = player.position().add(0, player.getBbHeight() + 0.2, 0);
        for (Rope rope : ROPES.values()) {
            if (rope.level() != player.level()) {
                continue;
            }
            double t = rope.along(hands);
            Vec3 on = rope.at(t);
            if (t <= 0.0 || t >= 1.0 || on.distanceToSqr(hands) > 1.4 * 1.4) {
                continue;
            }
            Vec3 dir = rope.b().subtract(rope.a()).normalize();
            double speed = player.getDeltaMovement().dot(dir);
            speed += 0.08 * -dir.y + 0.02 * Math.signum(player.getLookAngle().dot(dir));
            speed = Math.max(-1.2, Math.min(1.2, speed));
            Vec3 pull = on.subtract(hands).scale(0.5);
            player.setDeltaMovement(dir.scale(speed).add(pull));
            player.hurtMarked = true;
            player.resetFallDistance();
            return;
        }
    }

    private EngineersCrossbow() {}
}
