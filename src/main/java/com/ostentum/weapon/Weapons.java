package com.ostentum.weapon;

import com.ostentum.Ostentum;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * The special weapons (docs/WEAPONS-PLAN.md, 1.9.0). Each weapon is one class in this package with its items, its
 * behaviour and, where it has one, its thrown entity, so any of them can move to another mod with its assets, data and
 * lang lines. This class only touches them in order and keeps the few helpers they share: item registration, a server
 * tick scheduler and the per-stack custom data.
 */
public final class Weapons {
    static final com.ostentum.Entities ENTITIES = new com.ostentum.Entities(Ostentum.MODID);
    private static final DeferredRegister<com.mojang.serialization.Codec<? extends net.minecraftforge.common.loot.IGlobalLootModifier>> LOOT =
        DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Ostentum.MODID);

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
        OstentumFx.init(modBus);
        SeaFx.init();
        ENTITIES.register(modBus);
        LOOT.register("weapon_loot", () -> WeaponLoot.CODEC);
        LOOT.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(Weapons::tick);
        MinecraftForge.EVENT_BUS.addListener(Weapons::joined);
        MinecraftForge.EVENT_BUS.addListener(Weapons::levelTick);
        SpearArcs.register();
    }

    /**
     * 1.20.1 has no tick event for entities that aren't living (arrows, clouds): entities that {@code wants} accepts as
     * they join a server level are ticked by {@code tick} at the end of each level tick while they live.
     */
    static <E extends Entity> void tickEntities(Class<E> type, Predicate<E> wants, Consumer<E> tick) {
        TICKERS.add(new Ticker<>(type, wants, tick));
    }

    private record Ticker<E extends Entity>(Class<E> type, Predicate<E> wants, Consumer<E> tick) {
        boolean offer(Entity entity) {
            return type.isInstance(entity) && wants.test(type.cast(entity));
        }

        void run(Entity entity) {
            tick.accept(type.cast(entity));
        }
    }

    private static final List<Ticker<?>> TICKERS = new ArrayList<>();
    private static final Map<Entity, List<Ticker<?>>> TICKED = new java.util.WeakHashMap<>();

    private static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        for (Ticker<?> ticker : TICKERS) {
            if (ticker.offer(event.getEntity())) {
                TICKED.computeIfAbsent(event.getEntity(), e -> new ArrayList<>()).add(ticker);
            }
        }
    }

    private static void levelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || TICKED.isEmpty()) {
            return;
        }
        for (Map.Entry<Entity, List<Ticker<?>>> e : new ArrayList<>(TICKED.entrySet())) {
            Entity entity = e.getKey();
            if (entity.isRemoved()) {
                TICKED.remove(entity);
                continue;
            }
            if (entity.level() == level) {
                for (Ticker<?> ticker : e.getValue()) {
                    ticker.run(entity);
                }
            }
        }
    }

    static <I extends Item> RegistryObject<I> item(String name, Function<Item.Properties, ? extends I> factory, UnaryOperator<com.ostentum.Props> props) {
        return com.ostentum.Props.item(Ostentum.ITEMS, name, factory, props);
    }

    /** How far a weapon crossbow's load is, 0 to 1 (1.20.1: its pulling models' "pull"). */
    public static float crossbowPull(ItemStack stack, LivingEntity user) {
        return stack.getItem() instanceof WeaponCrossbow crossbow
            ? crossbow.power(stack.getUseDuration() - user.getUseItemRemainingTicks(), stack, user) : 0.0F;
    }

    /** Whether {@code entity} holds {@code item} in its main hand. */
    static boolean holds(LivingEntity entity, RegistryObject<? extends Item> item) {
        return entity.getMainHandItem().is(item.get());
    }

    /** The stack's own data (a copy of its NBT tag, 1.21's custom_data). */
    static CompoundTag data(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new CompoundTag() : tag.copy();
    }

    static void edit(ItemStack stack, Consumer<CompoundTag> change) {
        change.accept(stack.getOrCreateTag());
    }

    private record Task(ServerLevel level, long at, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();

    /** Runs {@code run} on the server thread {@code ticks} ticks from now (dropped if the server stops first). */
    static void later(ServerLevel level, int ticks, Runnable run) {
        TASKS.add(new Task(level, level.getGameTime() + ticks, run));
    }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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
