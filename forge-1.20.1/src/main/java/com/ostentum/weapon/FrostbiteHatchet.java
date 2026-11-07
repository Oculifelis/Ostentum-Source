package com.ostentum.weapon;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeTier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * Frostbite Hatchet (diamond + blue ice): a diamond axe that use throws as a returning boomerang. Still water under its
 * path freezes to frosted ice (which melts again, as Frost Walker's does), and mobs it hits start freezing as in powder
 * snow. It flies out for {@link #OUT} ticks or until it hits a block, then turns home through anything in its way.
 */
public final class FrostbiteHatchet {
    public static final RegistryObject<Item> HATCHET = Weapons.item("frostbite_hatchet", Hatchet::new, p -> p.rarity(Rarity.RARE)
        .axe(Tiers.DIAMOND, 5.0F, -3.0F));
    public static final RegistryObject<EntityType<Thrown>> THROWN = Weapons.ENTITIES.registerEntityType("frostbite_hatchet",
        Thrown::new, MobCategory.MISC, b -> b.sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(2).noSummon());

    static final int OUT = 14;
    static final float DAMAGE = 7.0F;

    static void init() {}

    static final class Hatchet extends Item {
        Hatchet(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level instanceof ServerLevel server) {
                stack.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(hand));
                if (stack.isEmpty()) {
                    return InteractionResultHolder.success(player.getItemInHand(hand));
                }
                Thrown thrown = new Thrown(THROWN.get(), level);
                thrown.setOwner(player);
                thrown.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
                thrown.setItem(stack.copyWithCount(1));
                thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.4F, 0.3F);
                server.addFreshEntity(thrown);
                server.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.3F);
                if (!player.getAbilities().instabuild) {
                    player.setItemInHand(hand, ItemStack.EMPTY);
                }
            }
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
    }

    /** The hatchet in flight. */
    public static final class Thrown extends ThrowableItemProjectile {
        private final Set<Integer> hit = new HashSet<>();
        private boolean returning;

        public Thrown(EntityType<? extends Thrown> type, Level level) {
            super(type, level);
        }

        @Override
        protected Item getDefaultItem() {
            return HATCHET.get();
        }

        @Override
        protected float getGravity() {
            return 0.0F;
        }

        public boolean returning() {
            return returning;
        }

        @Override
        public void tick() {
            if (!level().isClientSide() && level() instanceof ServerLevel level) {
                Entity owner = getOwner();
                if (owner == null || !owner.isAlive() || owner.level() != level || owner.distanceToSqr(this) > 64 * 64 || tickCount > 200) {
                    drop(level);
                    return;
                }
                if (tickCount >= OUT) {
                    returning = true;
                }
                if (returning) {
                    noPhysics = true;
                    Vec3 home = owner.getEyePosition().subtract(position());
                    if (home.lengthSqr() < 1.5 * 1.5) {
                        giveBack(level, owner);
                        return;
                    }
                    setDeltaMovement(home.normalize().scale(Math.min(1.6, 0.6 + tickCount * 0.04)));
                }
                freezeWater(level, blockPosition());
                if (tickCount % 4 == 0) {
                    level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.4F, 1.6F);
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 1, 0.1, 0.1, 0.1, 0.0);
            }
            super.tick();
        }

        @Override
        protected boolean canHitEntity(Entity target) {
            return super.canHitEntity(target) && target != getOwner() && !hit.contains(target.getId());
        }

        @Override
        protected void onHitEntity(EntityHitResult result) {
            Entity target = result.getEntity();
            hit.add(target.getId());
            if (level() instanceof ServerLevel level) {
                target.hurt(damageSources().thrown(this, getOwner()), DAMAGE);
                if (target instanceof LivingEntity living && living.canFreeze()) {
                    living.setTicksFrozen(Math.max(living.getTicksFrozen(), living.getTicksRequiredToFreeze() + 80));
                }
            }
        }

        @Override
        protected void onHitBlock(BlockHitResult result) {
            returning = true;
        }

        private void giveBack(ServerLevel level, Entity owner) {
            if (!(owner instanceof Player player) || player.getAbilities().instabuild || player.getInventory().add(getItem())) {
                level.playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.2F);
                discard();
            } else {
                drop(level);
            }
        }

        private void drop(ServerLevel level) {
            if (!(getOwner() instanceof Player player && player.getAbilities().instabuild)) {
                level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), getItem()));
            }
            discard();
        }
    }

    /** Still water just under the hatchet (up to 3 blocks down, 3x3) freezes to frosted ice. */
    static void freezeWater(ServerLevel level, BlockPos at) {
        for (int down = 0; down <= 3; down++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = at.offset(dx, -down, dz);
                    if (level.getFluidState(pos).is(Fluids.WATER) && level.getBlockState(pos).is(Blocks.WATER)
                        && level.getBlockState(pos.above()).isAir()) {
                        level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
                        level.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(level.getRandom(), 60, 120));
                    }
                }
            }
        }
    }

    private FrostbiteHatchet() {}
}
