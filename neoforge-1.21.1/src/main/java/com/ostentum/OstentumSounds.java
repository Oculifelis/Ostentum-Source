package com.ostentum;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Ostentum's music. The moon and siege tracks play as music while their moon or night event is up
 * ({@code client/MoonMusic}); the discs play in a jukebox. sounds.json is written by tools/ostentum_gen.py.
 */
public final class OstentumSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Ostentum.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_HARVEST_MOON = sound("music.harvest_moon");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_ENCHANTED_MOON = sound("music.enchanted_moon");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_GLIMMER_MOON = sound("music.glimmer_moon");
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_PILLAGER_SIEGE = sound("music.pillager_siege");

    public static final DeferredHolder<SoundEvent, SoundEvent> DISC_FUNDAMENTUM = sound("music_disc.fundamentum");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISC_HARVEST_MOON = sound("music_disc.harvest_moon");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISC_ENCHANTED_MOON = sound("music_disc.enchanted_moon");
    public static final DeferredHolder<SoundEvent, SoundEvent> DISC_GLIMMER_MOON = sound("music_disc.glimmer_moon");
    /** The Voidstring Bow's release and draw (tools/sounds/voidstring.py, 1.9.5: shot take 1 and the draw, marked Good). */
    public static final DeferredHolder<SoundEvent, SoundEvent> VOIDSTRING_SHOOT = sound("item.voidstring_bow.shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOIDSTRING_DRAW = sound("item.voidstring_bow.draw");

    /** The Pathfinder's Glaive's own sounds (Ultima Ratio's synth, 1.9.4). */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLAIVE_ATTACK = sound("item.pathfinders_glaive.attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLAIVE_HIT = sound("item.pathfinders_glaive.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLAIVE_CHARGE = sound("item.pathfinders_glaive.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLAIVE_PLANT = sound("item.pathfinders_glaive.plant");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLAIVE_RECALL = sound("item.pathfinders_glaive.recall");

    // tridents and Recall Chime (UR fx, Oculifelis)
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_THROW = sound("item.abyssal_trident.throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_HIT = sound("item.abyssal_trident.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_HIT_GROUND = sound("item.abyssal_trident.hit_ground");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_RETURN = sound("item.abyssal_trident.return");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_RIPTIDE = sound("item.abyssal_trident.riptide");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABYSSAL_FIELD = sound("item.abyssal_trident.field");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORAL_THROW = sound("item.coral_trident.throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORAL_HIT = sound("item.coral_trident.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORAL_HIT_GROUND = sound("item.coral_trident.hit_ground");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORAL_RETURN = sound("item.coral_trident.return");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORAL_RIPTIDE = sound("item.coral_trident.riptide");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORAL_WHIRLPOOL = sound("item.coral_trident.whirlpool");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORAL_GROW = sound("item.coral_trident.grow");
    public static final DeferredHolder<SoundEvent, SoundEvent> FISHER_THROW = sound("item.fisher_kings_trident.throw");
    public static final DeferredHolder<SoundEvent, SoundEvent> FISHER_HIT = sound("item.fisher_kings_trident.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> FISHER_HIT_GROUND = sound("item.fisher_kings_trident.hit_ground");
    public static final DeferredHolder<SoundEvent, SoundEvent> FISHER_RETURN = sound("item.fisher_kings_trident.return");
    public static final DeferredHolder<SoundEvent, SoundEvent> FISHER_RIPTIDE = sound("item.fisher_kings_trident.riptide");
    public static final DeferredHolder<SoundEvent, SoundEvent> FISHER_CATCH = sound("item.fisher_kings_trident.catch");
    public static final DeferredHolder<SoundEvent, SoundEvent> FISHER_AQUATIC = sound("item.fisher_kings_trident.aquatic");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHIME_MARK = sound("item.recall_chime.mark");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHIME_WINDUP = sound("item.recall_chime.windup");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHIME_DEPART = sound("item.recall_chime.depart");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHIME_ARRIVE = sound("item.recall_chime.arrive");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHIME_FAIL = sound("item.recall_chime.fail");

    private OstentumSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Ostentum.id(path)));
    }

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
