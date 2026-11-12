package com.ostentum.weapon;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Bastion Crossbow, only from bastion treasure chests. It loads fast and fires {@link #BOLTS} bolts in a spread (one
 * costs ammo). It can load a fire charge, which flies as a flame bolt that lights campfires, candles, TNT and Nether
 * portal frames from range. Bolts that land leave a short patch of soul fire that burns whatever stands in it.
 */
public final class BastionCrossbow {
    public static final DeferredItem<Crossbow> CROSSBOW = Weapons.item("bastion_crossbow", Crossbow::new, p -> p.durability(465).enchantable(1)
        .rarity(Rarity.RARE));

    public static final int BOLTS = 3;
    static final int LOAD = 12;
    static final String BOLT_TAG = "ostentum_bastion";
    static final String FLAME_TAG = "ostentum_flame_bolt";
    static final String PATCH_TAG = "ostentum_soul_patch";

    static final Predicate<ItemStack> AMMO = net.minecraft.world.item.ProjectileWeaponItem.ARROW_ONLY.or(stack -> stack.is(Items.FIRE_CHARGE));

    static void init() {
        NeoForge.EVENT_BUS.addListener(BastionCrossbow::impact);
        NeoForge.EVENT_BUS.addListener(BastionCrossbow::patch);
    }

    public static final class Crossbow extends WeaponCrossbow {
        Crossbow(Properties properties) {
            super(properties);
        }

        @Override
        public Predicate<ItemStack> getAllSupportedProjectiles() {
            return AMMO;
        }

        @Override
        public Predicate<ItemStack> getSupportedHeldProjectiles() {
            return AMMO.or(stack -> stack.is(Items.FIREWORK_ROCKET));
        }

        @Override
        int chargeTicks(ItemStack crossbow, LivingEntity user) {
            return LOAD;
        }

        @Override
        List<ItemStack> load(ItemStack crossbow, LivingEntity shooter) {
            ItemStack ammo = shooter.getProjectile(crossbow);
            return take(crossbow, shooter, ammo.is(Items.FIRE_CHARGE) ? 1 : BOLTS, true);
        }

        @Override
        void loaded(ItemStack crossbow, List<ItemStack> projectiles) {
            if (projectiles.get(0).is(Items.FIRE_CHARGE)) {
                crossbow.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of("fire_charge"), List.of()));
            }
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            if (charged.isEmpty()) {
                return super.use(level, player, hand);
            }
            if (level instanceof ServerLevel server) {
                stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
                stack.remove(DataComponents.CUSTOM_MODEL_DATA);
                float power = charged.contains(Items.FIREWORK_ROCKET) ? 1.6F : 3.15F;
                fire(server, player, hand, stack, charged.itemCopies().toList(), power, new float[] {0.0F, -10.0F, 10.0F});
            }
            return InteractionResult.CONSUME;
        }

        @Override
        protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack heldItem, ItemStack projectile, boolean isCrit) {
            if (projectile.is(Items.FIRE_CHARGE)) {
                SmallFireball flame = new SmallFireball(level, shooter, shooter.getLookAngle());
                flame.setPos(shooter.getX(), shooter.getEyeY() - 0.15, shooter.getZ());
                flame.addTag(FLAME_TAG);
                return flame;
            }
            Projectile shot = super.createProjectile(level, shooter, heldItem, projectile, isCrit);
            shot.addTag(BOLT_TAG);
            return shot;
        }
    }

    private static void impact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile.level() instanceof ServerLevel level) || !(event.getRayTraceResult() instanceof BlockHitResult hit)) {
            return;
        }
        if (projectile.entityTags().contains(FLAME_TAG)) {
            light(level, hit.getBlockPos(), projectile.getOwner() instanceof LivingEntity owner ? owner : null);
        } else if (projectile instanceof AbstractArrow && projectile.entityTags().contains(BOLT_TAG)) {
            projectile.removeTag(BOLT_TAG);
            soulPatch(level, hit.getLocation(), projectile.getOwner() instanceof LivingEntity owner ? owner : null);
        }
    }

    /** Lights what the flame bolt hit: campfires and candles, TNT (primed). Portal frames light from the fire it leaves. */
    public static boolean light(ServerLevel level, BlockPos pos, LivingEntity owner) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TntBlock) {
            if (TntBlock.prime(level, pos)) {
                level.removeBlock(pos, false);
                return true;
            }
            return false;
        }
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)
            && !(state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED))) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), Block.UPDATE_ALL_IMMEDIATE);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    /** A short-lived ring of soul fire flames where a bolt landed. */
    public static AreaEffectCloud soulPatch(ServerLevel level, Vec3 at, LivingEntity owner) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
        cloud.setRadius(1.2F);
        cloud.setDuration(60);
        cloud.setRadiusPerTick(0.0F);
        cloud.setWaitTime(0);
        cloud.setCustomParticle(ParticleTypes.SOUL_FIRE_FLAME);
        cloud.setOwner(owner);
        cloud.addTag(PATCH_TAG);
        level.addFreshEntity(cloud);
        return cloud;
    }

    /** Whatever stands in a soul patch catches fire. */
    private static void patch(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AreaEffectCloud cloud) || cloud.tickCount % 5 != 0 || !(cloud.level() instanceof ServerLevel level)
            || !cloud.entityTags().contains(PATCH_TAG)) {
            return;
        }
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, cloud.getBoundingBox().inflate(0.2, 0.5, 0.2))) {
            if (living != cloud.getOwner() && !living.fireImmune()) {
                living.setRemainingFireTicks(Math.max(living.getRemainingFireTicks(), 60));
            }
        }
    }

    private BastionCrossbow() {}
}
