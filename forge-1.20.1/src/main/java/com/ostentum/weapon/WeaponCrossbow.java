package com.ostentum.weapon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A crossbow with its own load time and its own number of bolts per load, which vanilla's static charge code can't give.
 * Loading follows vanilla's sounds and ammo rules; {@link #fire} shoots any list of loaded projectiles at chosen angles.
 * 1.20.1: the loaded projectiles are vanilla's {@code ChargedProjectiles} NBT list ({@link #charged}, {@link #setCharged}).
 */
abstract class WeaponCrossbow extends CrossbowItem {
    /** Marks a free copy of a bolt (as Multishot's extra arrows): it can't be picked up. Stripped before it flies. */
    private static final String FREE = "ostentum_free_bolt";

    private boolean startSound;
    private boolean midSound;

    WeaponCrossbow(Properties properties) {
        super(properties);
    }

    /** Ticks a full load takes. */
    abstract int chargeTicks(ItemStack crossbow, LivingEntity user);

    /** The projectiles one load takes from {@code shooter} (some may be free copies, as with Multishot). */
    abstract List<ItemStack> load(ItemStack crossbow, LivingEntity shooter);

    float power(int timeHeld, ItemStack crossbow, LivingEntity user) {
        return Math.min(1.0F, (float) timeHeld / chargeTicks(crossbow, user));
    }

    /** The projectiles loaded in {@code crossbow} (copies). */
    static List<ItemStack> charged(ItemStack crossbow) {
        List<ItemStack> out = new ArrayList<>();
        CompoundTag tag = crossbow.getTag();
        if (tag != null && tag.contains("ChargedProjectiles", Tag.TAG_LIST)) {
            ListTag list = tag.getList("ChargedProjectiles", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                out.add(ItemStack.of(list.getCompound(i)));
            }
        }
        return out;
    }

    /** Loads {@code projectiles} into {@code crossbow} (none empties it), as vanilla's charge tags. */
    static void setCharged(ItemStack crossbow, List<ItemStack> projectiles) {
        CompoundTag tag = crossbow.getOrCreateTag();
        ListTag list = new ListTag();
        for (ItemStack projectile : projectiles) {
            list.add(projectile.save(new CompoundTag()));
        }
        tag.put("ChargedProjectiles", list);
        CrossbowItem.setCharged(crossbow, !projectiles.isEmpty());
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        // loading happens in onUseTick; vanilla would load again here on release
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        if (level.isClientSide()) {
            return;
        }
        float done = power(getUseDuration(stack) - ticksRemaining, stack, entity);
        if (done < 0.2F) {
            startSound = false;
            midSound = false;
        }
        if (done >= 0.2F && !startSound) {
            startSound = true;
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_START, SoundSource.PLAYERS, 0.5F, 1.0F);
        }
        if (done >= 0.5F && !midSound) {
            midSound = true;
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS, 0.5F, 1.0F);
        }
        if (done >= 1.0F && !isCharged(stack)) {
            List<ItemStack> loaded = load(stack, entity);
            if (!loaded.isEmpty()) {
                setCharged(stack, loaded);
                loaded(stack, loaded);
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_END, entity.getSoundSource(), 1.0F,
                    1.0F / (level.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F);
            }
        }
    }

    /** Called once a load has gone into the crossbow. */
    void loaded(ItemStack crossbow, List<ItemStack> projectiles) {}

    /** Takes {@code count} bolts of the shooter's ammo: the first for real, the rest free copies when {@code free}. */
    static List<ItemStack> take(ItemStack crossbow, LivingEntity shooter, int count, boolean free) {
        List<ItemStack> out = new ArrayList<>();
        ItemStack first = shooter.getProjectile(crossbow);
        if (first.isEmpty()) {
            return out;
        }
        ItemStack copy = first.copy();
        boolean infinite = shooter instanceof Player player && player.getAbilities().instabuild;
        for (int i = 0; i < count; i++) {
            boolean freeCopy = free && i > 0;
            ItemStack ammo = freeCopy ? copy : shooter.getProjectile(crossbow);
            if (ammo.isEmpty()) {
                break;
            }
            ItemStack drawn;
            if (freeCopy || infinite) {
                drawn = ammo.copyWithCount(1);
                if (freeCopy) {
                    drawn.getOrCreateTag().putBoolean(FREE, true);
                }
            } else {
                drawn = ammo.split(1);
                if (ammo.isEmpty() && shooter instanceof Player player) {
                    player.getInventory().removeItem(ammo);
                }
            }
            if (drawn.isEmpty()) {
                break;
            }
            out.add(drawn);
        }
        return out;
    }

    /** A projectile for one loaded {@code ammo}: a firework, or an arrow as vanilla's crossbow makes it. */
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack crossbow, ItemStack ammo, boolean isCrit) {
        if (ammo.is(Items.FIREWORK_ROCKET)) {
            return new FireworkRocketEntity(level, ammo, shooter, shooter.getX(), shooter.getEyeY() - 0.15F, shooter.getZ(), true);
        }
        ArrowItem item = (ArrowItem) (ammo.getItem() instanceof ArrowItem ? ammo.getItem() : Items.ARROW);
        AbstractArrow arrow = item.createArrow(level, ammo, shooter);
        if (isCrit) {
            arrow.setCritArrow(true);
        }
        arrow.setSoundEvent(SoundEvents.CROSSBOW_HIT);
        arrow.setShotFromCrossbow(true);
        int pierce = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, crossbow);
        if (pierce > 0) {
            arrow.setPierceLevel((byte) pierce);
        }
        return arrow;
    }

    /** Shoots {@code projectiles} at the given yaw offsets (degrees), one durability each (three for a firework), as vanilla's shoot does. */
    void fire(ServerLevel level, LivingEntity shooter, InteractionHand hand, ItemStack crossbow, List<ItemStack> projectiles, float power, float[] angles) {
        for (int i = 0; i < projectiles.size(); i++) {
            ItemStack projectile = projectiles.get(i);
            if (projectile.isEmpty()) {
                continue;
            }
            boolean free = projectile.getTag() != null && projectile.getTag().getBoolean(FREE);
            if (free) {
                projectile.getTag().remove(FREE);
                if (projectile.getTag().isEmpty()) {
                    projectile.setTag(null);
                }
            }
            float angle = angles[Math.min(i, angles.length - 1)];
            Projectile shot = createProjectile(level, shooter, crossbow, projectile, shooter instanceof Player);
            if (shot instanceof AbstractArrow arrow && (free || shooter instanceof Player player && player.getAbilities().instabuild)) {
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
            Vec3 up = shooter.getUpVector(1.0F);
            Quaternionf turn = new Quaternionf().setAngleAxis(angle * ((float) Math.PI / 180F), up.x, up.y, up.z);
            Vector3f dir = shooter.getViewVector(1.0F).toVector3f().rotate(turn);
            shot.shoot(dir.x(), dir.y(), dir.z(), power, 1.0F);
            level.addFreshEntity(shot);
            level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + i * 0.1F);
            shotFired(level, shooter, shot, i);
            crossbow.hurtAndBreak(projectile.is(Items.FIREWORK_ROCKET) ? 3 : 1, shooter, e -> e.broadcastBreakEvent(hand));
            if (crossbow.isEmpty()) {
                break;
            }
        }
    }

    /** Called for each projectile {@link #fire} spawns. */
    void shotFired(ServerLevel level, LivingEntity shooter, Projectile shot, int index) {}
}
