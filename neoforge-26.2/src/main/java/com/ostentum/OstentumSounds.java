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

    private OstentumSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Ostentum.id(path)));
    }

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
