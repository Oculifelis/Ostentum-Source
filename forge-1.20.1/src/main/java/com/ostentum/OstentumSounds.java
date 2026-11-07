package com.ostentum;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Ostentum's music. The moon and siege tracks play as music while their moon or night event is up
 * ({@code client/MoonMusic}); the discs play in a jukebox. sounds.json is written by tools/ostentum_gen.py.
 */
public final class OstentumSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Ostentum.MODID);

    public static final RegistryObject<SoundEvent> MUSIC_HARVEST_MOON = sound("music.harvest_moon");
    public static final RegistryObject<SoundEvent> MUSIC_ENCHANTED_MOON = sound("music.enchanted_moon");
    public static final RegistryObject<SoundEvent> MUSIC_GLIMMER_MOON = sound("music.glimmer_moon");
    public static final RegistryObject<SoundEvent> MUSIC_PILLAGER_SIEGE = sound("music.pillager_siege");

    public static final RegistryObject<SoundEvent> DISC_FUNDAMENTUM = sound("music_disc.fundamentum");
    public static final RegistryObject<SoundEvent> DISC_HARVEST_MOON = sound("music_disc.harvest_moon");
    public static final RegistryObject<SoundEvent> DISC_ENCHANTED_MOON = sound("music_disc.enchanted_moon");
    public static final RegistryObject<SoundEvent> DISC_GLIMMER_MOON = sound("music_disc.glimmer_moon");

    private OstentumSounds() {}

    private static RegistryObject<SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Ostentum.id(path)));
    }

    static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
