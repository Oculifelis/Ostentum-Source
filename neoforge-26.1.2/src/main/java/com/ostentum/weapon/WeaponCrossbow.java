package com.ostentum.weapon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;

/**
 * A crossbow with its own load time and its own number of bolts per load, which vanilla's static charge code can't give.
 * Loading follows vanilla's sounds and ammo rules; {@link #fire} shoots any list of loaded projectiles at chosen angles.
 */
abstract class WeaponCrossbow extends CrossbowItem {
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

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        return power(getUseDuration(stack, entity) - remainingTime, stack, entity) >= 1.0F && isCharged(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        if (level.isClientSide()) {
            return;
        }
        float done = power(stack.getUseDuration(entity) - ticksRemaining, stack, entity);
        if (done < 0.2F) {
            startSound = false;
            midSound = false;
        }
        if (done >= 0.2F && !startSound) {
            startSound = true;
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_START.value(), SoundSource.PLAYERS, 0.5F, 1.0F);
        }
        if (done >= 0.5F && !midSound) {
            midSound = true;
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.PLAYERS, 0.5F, 1.0F);
        }
        if (done >= 1.0F && !isCharged(stack)) {
            List<ItemStack> loaded = load(stack, entity);
            if (!loaded.isEmpty()) {
                stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(loaded));
                loaded(stack, loaded);
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), entity.getSoundSource(), 1.0F,
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
        for (int i = 0; i < count; i++) {
            ItemStack ammo = free ? (i == 0 ? first : copy) : shooter.getProjectile(crossbow);
            if (ammo.isEmpty()) {
                break;
            }
            ItemStack drawn = useAmmo(crossbow, ammo, shooter, free && i > 0);
            if (drawn.isEmpty()) {
                break;
            }
            out.add(drawn);
        }
        return out;
    }

    /** Shoots {@code projectiles} at the given yaw offsets (degrees), one durability each, as vanilla's shoot does. */
    void fire(ServerLevel level, LivingEntity shooter, InteractionHand hand, ItemStack crossbow, List<ItemStack> projectiles, float power, float[] angles) {
        for (int i = 0; i < projectiles.size(); i++) {
            ItemStack projectile = projectiles.get(i);
            if (projectile.isEmpty()) {
                continue;
            }
            float angle = angles[Math.min(i, angles.length - 1)];
            int index = i;
            Projectile shot = createProjectile(level, shooter, crossbow, projectile, shooter instanceof Player);
            Projectile.spawnProjectile(shot, level, projectile, p -> shootProjectile(shooter, p, index, power, 1.0F, angle, null));
            shotFired(level, shooter, shot, index);
            crossbow.hurtAndBreak(getDurabilityUse(projectile), shooter, hand.asEquipmentSlot());
            if (crossbow.isEmpty()) {
                break;
            }
        }
    }

    /** Called for each projectile {@link #fire} spawns. */
    void shotFired(ServerLevel level, LivingEntity shooter, Projectile shot, int index) {}
}
