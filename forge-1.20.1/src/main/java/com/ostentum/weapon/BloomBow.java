package com.ostentum.weapon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Bloom Bow (amethyst + flowers). Its arrows bonemeal a 3x3 patch where they land, and arrows that hit the shooter's
 * pets or allies heal them ({@link #HEAL}) instead of hurting them.
 */
public final class BloomBow {
    public static final RegistryObject<BowItem> BOW = Weapons.item("bloom_bow", Bow::new, p -> p.durability(384).enchantable(1).rarity(Rarity.UNCOMMON));

    static final String TAG = "ostentum_bloom";
    static final float HEAL = 4.0F;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(BloomBow::impact);
    }

    static final class Bow extends BowItem {
        Bow(Properties properties) {
            super(properties);
        }

        @Override
        public AbstractArrow customArrow(AbstractArrow arrow) {
            arrow.addTag(TAG);
            return arrow;
        }
    }

    /** Whether {@code target} is the shooter's own: a pet it tamed, or anything it is allied with. */
    public static boolean friendly(Entity shooter, Entity target) {
        if (shooter == null || target == shooter) {
            return false;
        }
        return target instanceof TamableAnimal pet && shooter instanceof LivingEntity owner && pet.isOwnedBy(owner) || target.isAlliedTo(shooter);
    }

    private static void impact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !arrow.getTags().contains(TAG)
            || !(arrow.level() instanceof ServerLevel level)) {
            return;
        }
        if (event.getRayTraceResult() instanceof EntityHitResult hit) {
            if (hit.getEntity() instanceof LivingEntity target && friendly(arrow.getOwner(), target)) {
                event.setCanceled(true);
                target.heal(HEAL);
                level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY(1.0), target.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
                level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
                arrow.discard();
            }
        } else if (event.getRayTraceResult() instanceof BlockHitResult hit) {
            arrow.removeTag(TAG);
            bloom(level, hit.getBlockPos());
        }
    }

    /** Bonemeals the 3x3 around {@code centre}: each column's block, or the one above it if that does not grow. */
    public static int bloom(ServerLevel level, BlockPos centre) {
        int grown = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = centre.offset(dx, 0, dz);
                if (BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), level, pos, null)
                    || BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), level, pos.above(), null)) {
                    grown++;
                    level.levelEvent(1505, pos, 15);
                }
            }
        }
        if (grown > 0) {
            level.playSound(null, centre, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return grown;
    }

    private BloomBow() {}
}
