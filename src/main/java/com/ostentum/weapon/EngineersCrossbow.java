package com.ostentum.weapon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

/**
 * Engineer's Crossbow (netherite upgrade of a crossbow). Sneak + use while unloaded switches its mode.
 * <ul>
 *   <li>Combat: a {@link #MAGAZINE}-bolt magazine with a slow load; each use fires one bolt.</li>
 *   <li>Rope (1.9.4): each load is one anchor bolt. The first bolt that lands in a block sets the first anchor, the
 *   second strings a {@link Zipline} between the two (at most {@link Zipline#MAX_LENGTH} blocks; a longer one makes
 *   the second the new first anchor). Holding the crossbow, jump up to a rope to catch it and ride it.</li>
 * </ul>
 * While it is held, a HUD right of the hotbar shows the mode, the bolts loaded, the ammo it takes and how many you
 * carry, and in rope mode whether the first anchor is set (client.ZiplineClient).
 */
public final class EngineersCrossbow {
    public static final DeferredItem<Crossbow> CROSSBOW = Weapons.item("engineers_crossbow", Crossbow::new, p -> p.durability(800).enchantable(1)
        .fireResistant().rarity(Rarity.EPIC));

    public static final DeferredHolder<EntityType<?>, EntityType<Zipline>> ZIPLINE = Weapons.ENTITIES.registerEntityType("zipline",
        Zipline::new, MobCategory.MISC, b -> b.sized(0.25F, 0.25F).clientTrackingRange(10).updateInterval(20).noSummon().fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<Zipline.Rider>> RIDER = Weapons.ENTITIES.registerEntityType("zipline_rider",
        Zipline.Rider::new, MobCategory.MISC, b -> b.sized(0.2F, 0.2F).clientTrackingRange(10).updateInterval(1).noSummon().fireImmune());

    public static final int MAGAZINE = 5;
    static final int COMBAT_LOAD = 60;
    static final int ROPE_LOAD = 20;
    /** Ropes one player may have strung at once (loaded ones; the oldest goes). */
    public static final int MAX_ROPES = 4;
    /** Ticks after letting go before the same player can catch a rope again. */
    static final int RECATCH = 10;
    /** How far from the hands a rope can be caught. */
    static final double REACH = 1.3;
    static final String ROPE_TAG = "ostentum_rope:";

    private static final Map<UUID, Long> DROPPED = new ConcurrentHashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener(EngineersCrossbow::landed);
        NeoForge.EVENT_BUS.addListener(EngineersCrossbow::ride);
        NeoForge.EVENT_BUS.addListener(EngineersCrossbow::loggedOut);
    }

    public static boolean ropeMode(ItemStack stack) {
        return Weapons.data(stack).getBoolean("rope");
    }

    /** Whether the crossbow holds a first anchor waiting for its second. */
    public static boolean anchored(ItemStack stack) {
        return Weapons.data(stack).contains("anchor_block");
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
            return ropeMode(crossbow) ? take(crossbow, shooter, 1, false) : take(crossbow, shooter, MAGAZINE, false);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            if (charged.isEmpty() && player.isShiftKeyDown()) {
                boolean rope = !ropeMode(stack);
                Weapons.edit(stack, tag -> {
                    tag.putBoolean("rope", rope);
                    clearAnchor(tag);
                });
                player.displayClientMessage(Component.translatable("item.ostentum.engineers_crossbow." + (rope ? "rope" : "combat")), true);
                level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
                return InteractionResultHolder.success(stack);
            }
            if (charged.isEmpty()) {
                return super.use(level, player, hand);
            }
            if (level instanceof ServerLevel server) {
                List<ItemStack> bolts = new ArrayList<>(charged.getItems());
                ItemStack first = bolts.remove(0);
                stack.set(DataComponents.CHARGED_PROJECTILES, bolts.isEmpty() ? ChargedProjectiles.EMPTY : ChargedProjectiles.of(bolts));
                fire(server, player, hand, stack, List.of(first), 3.15F, new float[] {0.0F});
                player.getCooldowns().addCooldown(stack.getItem(), 6);
            }
            return InteractionResultHolder.consume(stack);
        }

        @Override
        void shotFired(ServerLevel level, LivingEntity shooter, Projectile shot, int index) {
            if (shot instanceof AbstractArrow arrow && ropeMode(shooter.getMainHandItem().is(this) ? shooter.getMainHandItem() : shooter.getOffhandItem())) {
                arrow.addTag(ROPE_TAG + shooter.getUUID());
            }
        }
    }

    private static void clearAnchor(net.minecraft.nbt.CompoundTag tag) {
        tag.remove("anchor_block");
        tag.remove("anchor_x");
        tag.remove("anchor_y");
        tag.remove("anchor_z");
        tag.remove("anchor_dim");
    }

    /** The owner's rope-mode crossbow (main hand, off hand, then the inventory), or empty. */
    static ItemStack ropeCrossbow(Player player) {
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.is(CROSSBOW.get()) && ropeMode(stack)) {
                return stack;
            }
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(CROSSBOW.get()) && ropeMode(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** A rope bolt that lands in a block sets the first anchor, or strings the rope from it. */
    private static void landed(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)
            || !(event.getRayTraceResult() instanceof BlockHitResult hit)) {
            return;
        }
        String tag = arrow.getTags().stream().filter(t -> t.startsWith(ROPE_TAG)).findFirst().orElse(null);
        if (tag == null) {
            return;
        }
        arrow.removeTag(tag);
        UUID owner = UUID.fromString(tag.substring(ROPE_TAG.length()));
        if (level.getPlayerByUUID(owner) instanceof Player player) {
            anchor(level, player, hit.getLocation(), hit.getBlockPos());
        }
    }

    /**
     * An anchor bolt of {@code player}'s landed at {@code at} in block {@code block}: the first sets the anchor, the
     * second strings a rope from it. Returns the new rope, or null.
     */
    public static @Nullable Zipline anchor(ServerLevel level, Player player, Vec3 at, BlockPos block) {
        ItemStack crossbow = ropeCrossbow(player);
        if (crossbow.isEmpty()) {
            return null;
        }
        var data = Weapons.data(crossbow);
        String dim = level.dimension().location().toString();
        if (data.contains("anchor_block") && dim.equals(data.getString("anchor_dim"))) {
            Vec3 first = new Vec3(data.getDouble("anchor_x"), data.getDouble("anchor_y"), data.getDouble("anchor_z"));
            BlockPos firstBlock = BlockPos.of(data.getLong("anchor_block"));
            double length = first.distanceTo(at);
            if (length >= Zipline.MIN_LENGTH && length <= Zipline.MAX_LENGTH) {
                Weapons.edit(crossbow, EngineersCrossbow::clearAnchor);
                return string(level, player.getUUID(), first, firstBlock, at, block);
            }
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable(length > Zipline.MAX_LENGTH ? "item.ostentum.engineers_crossbow.too_far"
                    : "item.ostentum.engineers_crossbow.too_short"), true);
            }
        }
        Weapons.edit(crossbow, tag -> {
            tag.putLong("anchor_block", block.asLong());
            tag.putDouble("anchor_x", at.x);
            tag.putDouble("anchor_y", at.y);
            tag.putDouble("anchor_z", at.z);
            tag.putString("anchor_dim", dim);
        });
        level.playSound(null, block, SoundEvents.TRIPWIRE_ATTACH, SoundSource.PLAYERS, 0.8F, 1.2F);
        return null;
    }

    /** Strings a rope from {@code a} to {@code b} for {@code owner}; the owner's oldest goes past {@link #MAX_ROPES}. */
    public static @Nullable Zipline string(ServerLevel level, UUID owner, Vec3 a, BlockPos blockA, Vec3 b, BlockPos blockB) {
        double length = a.distanceTo(b);
        if (length < Zipline.MIN_LENGTH || length > Zipline.MAX_LENGTH) {
            return null;
        }
        Zipline line = Zipline.create(level, a, blockA, b, blockB, owner);
        if (!level.addFreshEntity(line)) {
            return null;
        }
        List<Zipline> mine = new ArrayList<>(level.getEntities(ZIPLINE.get(), z -> z.isAlive() && owner.equals(z.owner())));
        mine.sort(Comparator.comparingInt(z -> -z.tickCount));
        for (int i = 0; i < mine.size() - MAX_ROPES; i++) {
            mine.get(i).snap(level, false);
        }
        level.playSound(null, blockB, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
        return line;
    }

    static void dropped(Player player, long gameTime) {
        DROPPED.put(player.getUUID(), gameTime);
    }

    /** Holding the crossbow in the air near a rope catches it (not while sneaking, nor just after letting go). */
    private static void ride(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.onGround() || player.isShiftKeyDown() || player.isPassenger()
            || player.isSpectator() || player.getAbilities().flying
            || !(player.getMainHandItem().is(CROSSBOW.get()) || player.getOffhandItem().is(CROSSBOW.get()))) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.getGameTime() - DROPPED.getOrDefault(player.getUUID(), -100L) < RECATCH) {
            return;
        }
        Vec3 hands = player.position().add(0, Zipline.HANG, 0);
        Zipline.Catch c = Zipline.reach(level, hands, REACH);
        if (c != null) {
            Zipline.attach(level, player, c.line(), c.t());
        }
    }

    /** A rider who logs out lets go first, so the seat is never saved with them. */
    private static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getVehicle() instanceof Zipline.Rider rider && rider.level() instanceof ServerLevel level) {
            rider.letGo(level, false);
        }
    }

    private EngineersCrossbow() {}
}
