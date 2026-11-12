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
import net.minecraftforge.registries.RegistryObject;

/**
 * What the three Ostentum tridents share: an item that throws its own entity type (so each is drawn with its own
 * texture) and a thrown trident with hooks for landing, hitting a block and first touching water. Riptide, Loyalty,
 * Channeling and Impaling all work as on vanilla's trident.
 */
public abstract class WeaponTrident {
    static <T extends Thrown> RegistryObject<EntityType<T>> type(String name, EntityType.EntityFactory<T> factory) {
        return Weapons.ENTITIES.registerEntityType(name, factory, MobCategory.MISC, b -> b.sized(0.5F, 0.5F)
            .clientTrackingRange(4).updateInterval(20).noSummon());
    }

    /** Vanilla trident's item properties (1.20.1's TridentItem makes its own attack attributes). */
    static com.ostentum.Props props(com.ostentum.Props p) {
        return p.rarity(net.minecraft.world.item.Rarity.RARE).durability(250).enchantable(1);
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
            if (!(entity instanceof Player player) || getUseDuration(stack) - remainingTime < THROW_THRESHOLD_TIME
                || EnchantmentHelper.getRiptide(stack) > 0 || stack.getDamageValue() >= stack.getMaxDamage() - 1) {
                // riptide (and every refusal) stays vanilla's
                super.releaseUsing(stack, level, entity, remainingTime);
                return;
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            if (level instanceof ServerLevel server) {
                stack.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(player.getUsedItemHand()));
                Thrown trident = Thrown.create(type.get(), server, player, stack.copyWithCount(1));
                trident.origin = ReturnSlot.of(player, player.getUsedItemHand());
                trident.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, SHOOT_POWER, 1.0F);
                if (player.getAbilities().instabuild) {
                    trident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                }
                server.addFreshEntity(trident);
                level.playSound(null, trident, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
                if (!player.getAbilities().instabuild) {
                    player.getInventory().removeItem(stack);
                }
            }
        }

        /** In hand it is drawn as the vanilla trident model with its own texture (client.WeaponsClient.TridentItems). */
        @Override
        public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
            consumer.accept(com.ostentum.client.WeaponsClient.tridentExtensions());
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
            ((ThrownTridentAccess) this).ostentum$setTridentItem(stack.copy());
            entityData.set(ThrownTridentAccess.ostentum$loyalty(), (byte) Mth.clamp(EnchantmentHelper.getLoyalty(stack), 0, 127));
            entityData.set(ThrownTridentAccess.ostentum$foil(), stack.hasFoil());
        }

        /** The trident stack this throw carries (1.21's getWeaponItem). */
        public ItemStack getWeaponItem() {
            return ((ThrownTridentAccess) this).ostentum$tridentItem();
        }

        /** The thrower takes it back into the slot it left (loyalty's return or a pickup); anyone else as vanilla. */
        @Override
        protected boolean tryPickup(Player player) {
            if (origin != ReturnSlot.NONE && ownedBy(player) && !player.getAbilities().instabuild
                && (pickup == AbstractArrow.Pickup.ALLOWED || isNoPhysics())) {
                return ReturnSlot.give(player, getPickupItem(), origin);
            }
            return super.tryPickup(player);
        }

        /** {@link #tryPickup} for the headless test. */
        boolean pickUp(Player player) {
            return tryPickup(player);
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
