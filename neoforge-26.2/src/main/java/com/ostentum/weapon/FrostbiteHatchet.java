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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Frostbite Hatchet (diamond + blue ice): a diamond axe that use throws as a returning boomerang. Still water under its
 * path freezes to frosted ice (which melts again, as Frost Walker's does), and mobs it hits start freezing as in powder
 * snow. It flies out for {@link #OUT} ticks or until it hits a block, then turns home through anything in its way.
 */
public final class FrostbiteHatchet {
    public static final DeferredItem<Item> HATCHET = Weapons.item("frostbite_hatchet", Hatchet::new, p -> p.rarity(Rarity.RARE)
        .axe(ToolMaterial.DIAMOND, 5.0F, -3.0F));
    public static final DeferredHolder<EntityType<?>, EntityType<Thrown>> THROWN = Weapons.ENTITIES.registerEntityType("frostbite_hatchet",
        Thrown::new, MobCategory.MISC, b -> b.sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(2).noSummon());

    static final int OUT = 14;
    static final float DAMAGE = 7.0F;

    static void init() {}

    static final class Hatchet extends Item {
        Hatchet(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (level instanceof ServerLevel server) {
                stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
                if (stack.isEmpty()) {
                    return InteractionResult.SUCCESS;
                }
                Thrown thrown = new Thrown(THROWN.get(), level);
                thrown.setOwner(player);
                thrown.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
                thrown.setItem(stack.copyWithCount(1));
                thrown.origin = ReturnSlot.of(player, hand);
                thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.4F, 0.3F);
                server.addFreshEntity(thrown);
                server.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
                if (!player.hasInfiniteMaterials()) {
                    player.setItemInHand(hand, ItemStack.EMPTY);
                }
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** The hatchet in flight. */
    public static final class Thrown extends ThrowableItemProjectile {
        /** Out along the throw, then home to its owner; it never turns out again. Saved by name with its age and slot. */
        public enum Flight { OUTBOUND, RETURNING }

        private final Set<Integer> hit = new HashSet<>();
        private Flight flight = Flight.OUTBOUND;
        /** Ticks flown, saved (tickCount is not, so a reloaded hatchet would fly out again and never time out). */
        int age;
        /** The inventory slot it was thrown from ({@link ReturnSlot}); it goes back there. */
        int origin = ReturnSlot.NONE;

        public Thrown(EntityType<? extends Thrown> type, Level level) {
            super(type, level);
        }

        @Override
        protected Item getDefaultItem() {
            return HATCHET.get();
        }

        @Override
        protected double getDefaultGravity() {
            return 0.0;
        }

        public boolean returning() {
            return flight == Flight.RETURNING;
        }

        public Flight flight() {
            return flight;
        }

        /** The one change of flight: out to home. */
        void turnHome() {
            flight = Flight.RETURNING;
        }

        @Override
        public void tick() {
            if (!level().isClientSide() && level() instanceof ServerLevel level) {
                Entity owner = getOwner();
                age++;
                if (owner == null || !owner.isAlive() || owner.level() != level || owner.distanceToSqr(this) > 64 * 64 || age > 200) {
                    drop(level);
                    return;
                }
                if (age >= OUT) {
                    turnHome();
                }
                if (flight == Flight.RETURNING) {
                    noPhysics = true;
                    Vec3 home = owner.getEyePosition().subtract(position());
                    if (home.lengthSqr() < 1.5 * 1.5) {
                        giveBack(level, owner);
                        return;
                    }
                    setDeltaMovement(home.normalize().scale(Math.min(1.6, 0.6 + age * 0.04)));
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
                target.hurtServer(level, damageSources().thrown(this, getOwner()), DAMAGE);
                if (target instanceof LivingEntity living && living.canFreeze()) {
                    living.setTicksFrozen(Math.max(living.getTicksFrozen(), living.getTicksRequiredToFreeze() + 80));
                }
            }
        }

        @Override
        protected void onHitBlock(BlockHitResult result) {
            turnHome();
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output);
            output.putString("flight", flight.name());
            output.putInt("age", age);
            output.putInt("origin", origin);
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            flight = parse(input.getStringOr("flight", ""));
            age = input.getIntOr("age", 0);
            origin = input.getIntOr("origin", ReturnSlot.NONE);
        }

        /** A saved flight; one from before 1.9.5 (none saved) flies out and turns home as a fresh throw would. */
        private static Flight parse(String name) {
            for (Flight f : Flight.values()) {
                if (f.name().equals(name)) {
                    return f;
                }
            }
            return Flight.OUTBOUND;
        }

        /** Home: back into the slot it left (or the first free one); dropped at the player's feet when the inventory is full. */
        void giveBack(ServerLevel level, Entity owner) {
            if (!(owner instanceof Player player) || player.hasInfiniteMaterials() || ReturnSlot.give(player, getItem(), origin)) {
                level.playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.2F);
                discard();
            } else {
                drop(level);
            }
        }

        private void drop(ServerLevel level) {
            if (!(getOwner() instanceof Player player && player.hasInfiniteMaterials())) {
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
