package com.ostentum.weapon;

import com.ostentum.Ostentum;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The special weapons (docs/WEAPONS-PLAN.md, 1.9.0). Each weapon is one class in this package with its items, its
 * behaviour and, where it has one, its thrown entity, so any of them can move to another mod with its assets, data and
 * lang lines. This class only touches them in order and keeps the few helpers they share: item registration, a server
 * tick scheduler and the per-stack custom data.
 */
public final class Weapons {
    static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Ostentum.MODID);
    private static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>> LOOT =
        DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Ostentum.MODID);

    /** Touches every weapon class (their static fields add the items to {@link Ostentum#ITEMS}) and hooks their events. */
    public static void init(IEventBus modBus) {
        WeathervaneSpear.init();
        PathfindersGlaive.init();
        DuelistsSaber.init();
        Hearthblade.init();
        EchoBlade.init();
        FrostbiteHatchet.init();
        BloomBow.init();
        VoidstringBow.init();
        EngineersCrossbow.init();
        BastionCrossbow.init();
        FisherKingsTrident.init();
        CoralTrident.init();
        AbyssalTrident.init();
        QuakeHammer.init();
        DelversPick.init();
        RecallChime.init();
        PhantomVeil.init();
        ENTITIES.register(modBus);
        LOOT.register("weapon_loot", () -> WeaponLoot.CODEC);
        LOOT.register(modBus);
        NeoForge.EVENT_BUS.addListener(Weapons::tick);
    }

    static <I extends Item> DeferredItem<I> item(String name, Function<Item.Properties, ? extends I> factory, UnaryOperator<Item.Properties> props) {
        return Ostentum.ITEMS.registerItem(name, factory, props);
    }

    /** Whether {@code entity} holds {@code item} in its main hand. */
    static boolean holds(LivingEntity entity, DeferredItem<? extends Item> item) {
        return entity.getMainHandItem().is(item.get());
    }

    /** The stack's own custom data (a copy). */
    static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    static void edit(ItemStack stack, Consumer<CompoundTag> change) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, change);
    }

    private record Task(ServerLevel level, long at, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();

    /** Runs {@code run} on the server thread {@code ticks} ticks from now (dropped if the server stops first). */
    static void later(ServerLevel level, int ticks, Runnable run) {
        TASKS.add(new Task(level, level.getGameTime() + ticks, run));
    }

    private static void tick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (task.level.getGameTime() >= task.at) {
                due.add(task);
                it.remove();
            }
        }
        due.forEach(task -> task.run.run());
    }

    private Weapons() {}
}
