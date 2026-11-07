package com.ostentum.weapon;

import com.ostentum.mixin.ThrownTridentAccess;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * What the three Ostentum tridents share: an item that throws its own entity type (so each is drawn with its own
 * texture) and a thrown trident with hooks for landing, hitting a block and first touching water. Riptide, Loyalty,
 * Channeling and Impaling all work as on vanilla's trident.
 */
public abstract class WeaponTrident {
    static <T extends Thrown> DeferredHolder<EntityType<?>, EntityType<T>> type(String name, EntityType.EntityFactory<T> factory) {
        return Weapons.ENTITIES.registerEntityType(name, factory, MobCategory.MISC, b -> b.sized(0.5F, 0.5F).eyeHeight(0.13F)
            .clientTrackingRange(4).updateInterval(20).noSummon());
    }

    /** Vanilla trident's item properties. */
    static com.ostentum.Props props(com.ostentum.Props p) {
        return p.rarity(net.minecraft.world.item.Rarity.RARE).durability(250).attributes(TridentItem.createAttributes())
            .component(net.minecraft.core.component.DataComponents.TOOL, TridentItem.createToolProperties()).enchantable(1);
    }

    /** The trident item: vanilla's throw, spawning {@code type} instead of vanilla's entity. */
    static final class Item extends TridentItem {
        private final Supplier<? extends EntityType<? extends Thrown>> type;

        Item(Properties properties, Supplier<? extends EntityType<? extends Thrown>> type) {
            super(properties);
            this.type = type;
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
            if (!(entity instanceof Player player) || getUseDuration(stack, entity) - remainingTime < THROW_THRESHOLD_TIME
                || EnchantmentHelper.getTridentSpinAttackStrength(stack, player) > 0.0F || stack.getDamageValue() >= stack.getMaxDamage() - 1) {
                // riptide (and every refusal) stays vanilla's
                super.releaseUsing(stack, level, entity, remainingTime);
                return;
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            if (level instanceof ServerLevel server) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
                Thrown trident = Thrown.create(type.get(), server, player, stack.copyWithCount(1));
                trident.origin = ReturnSlot.of(player, player.getUsedItemHand());
                trident.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, SHOOT_POWER, 1.0F);
                if (player.hasInfiniteMaterials()) {
                    trident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                }
                server.addFreshEntity(trident);
                level.playSound(null, trident, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                if (!player.hasInfiniteMaterials()) {
                    player.getInventory().removeItem(stack);
                }
            }
        }

        @Override
        public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
            Thrown trident = type.get().create(level);
            trident.setPos(position.x(), position.y(), position.z());
            trident.setup(stack.copyWithCount(1));
            trident.pickup = AbstractArrow.Pickup.ALLOWED;
            return trident;
        }
    }

    /** A thrown Ostentum trident. */
    public abstract static class Thrown extends ThrownTrident {
        private boolean landed;
        private boolean wet;
        /** The inventory slot its thrower threw it from ({@link ReturnSlot}); picked up or returning, it goes back there. */
        int origin = ReturnSlot.NONE;

        Thrown(EntityType<? extends Thrown> type, Level level) {
            super(type, level);
        }

        static <T extends Thrown> T create(EntityType<T> type, ServerLevel level, LivingEntity owner, ItemStack stack) {
            T trident = type.create(level);
            trident.setOwner(owner);
            trident.setPos(owner.getX(), owner.getEyeY() - 0.1F, owner.getZ());
            trident.setup(stack);
            return trident;
        }

        /** Takes {@code stack} as the trident it is, with its loyalty and glint. */
        void setup(ItemStack stack) {
            setPickupItemStack(stack.copy());
            byte loyalty = level() instanceof ServerLevel server
                ? (byte) Mth.clamp(EnchantmentHelper.getTridentReturnToOwnerAcceleration(server, stack, this), 0, 127) : 0;
            entityData.set(ThrownTridentAccess.ostentum$loyalty(), loyalty);
            entityData.set(ThrownTridentAccess.ostentum$foil(), stack.hasFoil());
        }

        /** The thrower takes it back into the slot it left (loyalty's return or a pickup); anyone else as vanilla. */
        @Override
        protected boolean tryPickup(Player player) {
            if (origin != ReturnSlot.NONE && ownedBy(player) && !player.hasInfiniteMaterials()
                && (pickup == AbstractArrow.Pickup.ALLOWED || isNoPhysics())) {
                return ReturnSlot.give(player, getPickupItem(), origin);
            }
            return super.tryPickup(player);
        }

        /** {@link #tryPickup} for the headless test. */
        boolean pickUp(Player player) {
            return tryPickup(player);
        }

        @Override
        protected ItemStack getDefaultPickupItem() {
            return new ItemStack(item());
        }

        abstract net.minecraft.world.item.Item item();

        @Override
        public void tick() {
            SeaFx.SOUNDING.set(this);
            try {
                super.tick();
            } finally {
                SeaFx.SOUNDING.remove();
            }
            if (level() instanceof ServerLevel level && !isRemoved()) {
                if (!wet && isInWater()) {
                    wet = true;
                    SeaFx.splash(level, this);
                    intoWater(level);
                }
                if (!landed && inGround) {
                    landed = true;
                    SeaFx.land(level, this);
                    land(level);
                }
            } else if (level().isClientSide() && !isRemoved() && !inGround) {
                SeaFx.trail(this);
            }
        }

        @Override
        protected net.minecraft.sounds.SoundEvent getDefaultHitGroundSoundEvent() {
            return SeaFx.style(this).hitGround();
        }

        @Override
        protected void onHitEntity(EntityHitResult hit) {
            SeaFx.SOUNDING.set(this);
            try {
                super.onHitEntity(hit);
            } finally {
                SeaFx.SOUNDING.remove();
            }
            if (!landed && level() instanceof ServerLevel level) {
                landed = true;
                SeaFx.land(level, this);
                land(level);
            }
        }

        @Override
        protected void onHitBlock(BlockHitResult hit) {
            super.onHitBlock(hit);
            if (level() instanceof ServerLevel level) {
                hitBlock(level, hit);
            }
        }

        /** Once per throw, the first tick the trident is in water. */
        void intoWater(ServerLevel level) {}

        /** Once per throw, where it first stops: in a block or on a mob. */
        void land(ServerLevel level) {}

        void hitBlock(ServerLevel level, BlockHitResult hit) {}
    }

    private WeaponTrident() {}
}
