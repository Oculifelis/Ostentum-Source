package com.ostentum.weapon;

import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Fisher King's Trident (trident + fishing rod + gold block). Thrown into water, it rolls the fishing loot table once
 * (Luck of the Sea on it counts, as on a rod) and the catch flies back to the thrower. It deals {@link #AQUATIC_BONUS}x
 * damage to aquatic mobs, thrown or in hand.
 */
public final class FisherKingsTrident {
    public static final DeferredHolder<EntityType<?>, EntityType<Thrown>> THROWN = WeaponTrident.type("fisher_kings_trident", Thrown::new);
    public static final DeferredItem<WeaponTrident.Item> TRIDENT = Weapons.item("fisher_kings_trident",
        p -> new WeaponTrident.Item(p, THROWN), WeaponTrident::props);

    public static final float AQUATIC_BONUS = 1.5F;

    static void init() {
        NeoForge.EVENT_BUS.addListener(FisherKingsTrident::aquatic);
    }

    public static final class Thrown extends WeaponTrident.Thrown {
        public Thrown(EntityType<? extends Thrown> type, Level level) {
            super(type, level);
        }

        @Override
        net.minecraft.world.item.Item item() {
            return TRIDENT.get();
        }

        @Override
        void intoWater(ServerLevel level) {
            if (getOwner() instanceof LivingEntity owner) {
                fish(level, this, getWeaponItem(), owner);
            }
        }
    }

    /** One roll of the fishing table at {@code at}, thrown toward {@code owner}; returns the catch. */
    public static List<ItemStack> fish(ServerLevel level, Entity at, ItemStack tool, LivingEntity owner) {
        int luck = EnchantmentHelper.getFishingLuckBonus(level, tool, owner);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, at.position())
            .withParameter(LootContextParams.TOOL, tool)
            .withParameter(LootContextParams.THIS_ENTITY, at)
            .withParameter(LootContextParams.ATTACKING_ENTITY, owner)
            .withLuck(luck + (owner instanceof net.minecraft.world.entity.player.Player p ? p.getLuck() : 0))
            .create(LootContextParamSets.FISHING);
        List<ItemStack> items = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params);
        for (ItemStack stack : items) {
            ItemEntity drop = new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(), stack);
            double dx = owner.getX() - at.getX();
            double dy = owner.getY() - at.getY();
            double dz = owner.getZ() - at.getZ();
            drop.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
            level.addFreshEntity(drop);
        }
        SeaFx.fishCatch(level, at, owner, items.size());
        return items;
    }

    private static void aquatic(LivingIncomingDamageEvent event) {
        if (!event.getEntity().getType().builtInRegistryHolder().is(EntityTypeTags.AQUATIC)) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        boolean thrown = direct instanceof Thrown;
        boolean held = direct instanceof LivingEntity holder && direct == event.getSource().getEntity() && Weapons.holds(holder, TRIDENT);
        if (thrown || held) {
            event.setAmount(event.getAmount() * AQUATIC_BONUS);
            if (event.getEntity().level() instanceof ServerLevel level) {
                SeaFx.aquatic(level, event.getEntity());
            }
        }
    }

    private FisherKingsTrident() {}
}
