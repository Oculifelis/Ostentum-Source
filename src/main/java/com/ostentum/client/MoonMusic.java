package com.ostentum.client;

import com.fundamentum.moon.MoonsClient;
import com.ostentum.OstentumSounds;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import com.ostentum.mixin.MusicManagerAccess;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * While an Ostentum moon is up over this client's level, its track is the music; during the Pillager Siege, the siege
 * track is, on a loop. Both cut in over whatever was playing ({@link Music#replaceCurrentMusic()}) and stop when the
 * moon sets or the siege ends, after which the game's own music takes over again.
 */
public final class MoonMusic {
    /** Moon tracks come round again after 10 to 30 seconds of quiet, for as long as the moon is up. */
    private static final int MOON_MIN_DELAY = 200;
    private static final int MOON_MAX_DELAY = 600;

    private static final String SIEGE = "ostentum:siege";
    private static Map<String, Music> moons;
    /** No gap at all: the siege track loops until dawn or until the raiders are gone. */
    private static Music siegeMusic;

    private static final Set<ResourceLocation> OURS = Set.of(OstentumSounds.MUSIC_HARVEST_MOON.getId(),
        OstentumSounds.MUSIC_ENCHANTED_MOON.getId(), OstentumSounds.MUSIC_GLIMMER_MOON.getId(),
        OstentumSounds.MUSIC_PILLAGER_SIEGE.getId());

    private MoonMusic() {}

    private static Music music(RegistryObject<SoundEvent> sound, int min, int max) {
        return new Music(sound.getHolder().orElseThrow(), min, max, true);
    }

    /**
     * Called from {@code MinecraftMusicMixin} at the end of {@code Minecraft.getSituationalMusic} (1.20.1 has no
     * SelectMusicEvent). Returns the music to play instead of {@code original}.
     */
    public static Music select(Music original) {
        if (moons == null) {
            moons = Map.of(
                "ostentum:harvest", music(OstentumSounds.MUSIC_HARVEST_MOON, MOON_MIN_DELAY, MOON_MAX_DELAY),
                "ostentum:enchanted", music(OstentumSounds.MUSIC_ENCHANTED_MOON, MOON_MIN_DELAY, MOON_MAX_DELAY),
                "ostentum:glimmer", music(OstentumSounds.MUSIC_GLIMMER_MOON, MOON_MIN_DELAY, MOON_MAX_DELAY));
            siegeMusic = music(OstentumSounds.MUSIC_PILLAGER_SIEGE, 0, 0);
        }
        Music wanted = wanted(original);
        if (wanted != null) {
            return wanted;
        }
        // The moon has set or the siege is over: stop our track rather than let it play out. The music manager stops
        // the current track when the situational music differs and asks to replace it.
        SoundInstance playing = ((MusicManagerAccess) Minecraft.getInstance().getMusicManager()).ostentum$currentMusic();
        if (playing != null && OURS.contains(playing.getLocation()) && original != null && !original.replaceCurrentMusic()) {
            return new Music(original.getEvent(), original.getMinDelay(), original.getMaxDelay(), true);
        }
        return original;
    }

    private static Music wanted(Music original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }
        // Screen music (the credits, a menu) and the dragon fight keep theirs.
        if (original == Musics.END_BOSS || original == Musics.CREDITS || mc.screen != null && mc.screen.getBackgroundMusic() != null) {
            return null;
        }
        if (SIEGE.equals(MoonsClient.event())) {
            return siegeMusic;
        }
        return moons.get(MoonsClient.moon());
    }
}
