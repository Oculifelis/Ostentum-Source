package com.ostentum.client;

import com.fundamentum.moon.MoonsClient;
import com.ostentum.Ostentum;
import com.ostentum.OstentumSounds;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * While an Ostentum moon is up over this client's level, its track is the music; during the Pillager Siege, the siege
 * track is, on a loop. Both cut in over whatever was playing ({@link Music#replaceCurrentMusic()}) and stop when the
 * moon sets or the siege ends, after which the game's own music takes over again.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class MoonMusic {
    /** Moon tracks come round again after 10 to 30 seconds of quiet, for as long as the moon is up. */
    private static final int MOON_MIN_DELAY = 200;
    private static final int MOON_MAX_DELAY = 600;

    private static final String SIEGE = "ostentum:siege";
    private static final Map<String, Music> MOONS = Map.of(
        "ostentum:harvest", new Music(OstentumSounds.MUSIC_HARVEST_MOON, MOON_MIN_DELAY, MOON_MAX_DELAY, true),
        "ostentum:enchanted", new Music(OstentumSounds.MUSIC_ENCHANTED_MOON, MOON_MIN_DELAY, MOON_MAX_DELAY, true),
        "ostentum:glimmer", new Music(OstentumSounds.MUSIC_GLIMMER_MOON, MOON_MIN_DELAY, MOON_MAX_DELAY, true));
    /** No gap at all: the siege track loops until dawn or until the raiders are gone. */
    private static final Music SIEGE_MUSIC = new Music(OstentumSounds.MUSIC_PILLAGER_SIEGE, 0, 0, true);

    private static final Set<Identifier> OURS = Set.of(OstentumSounds.MUSIC_HARVEST_MOON.getId(),
        OstentumSounds.MUSIC_ENCHANTED_MOON.getId(), OstentumSounds.MUSIC_GLIMMER_MOON.getId(),
        OstentumSounds.MUSIC_PILLAGER_SIEGE.getId());

    private MoonMusic() {}

    /** Low priority, so a biome or dimension music mod gets its say first and the moon still wins. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void select(SelectMusicEvent event) {
        Music wanted = wanted(event.getOriginalMusic());
        if (wanted != null) {
            event.overrideMusic(wanted);
            return;
        }
        // The moon has set or the siege is over: stop our track rather than let it play out.
        SoundInstance playing = event.getPlayingMusic();
        if (playing != null && OURS.contains(playing.getIdentifier())) {
            event.setMusic(null);
        }
    }

    private static Music wanted(Music original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }
        // Screen music (the credits, a menu) and the dragon fight keep theirs.
        if (original == Musics.END_BOSS || original == Musics.CREDITS || mc.gui.screen() != null && mc.gui.screen().getBackgroundMusic() != null) {
            return null;
        }
        if (SIEGE.equals(MoonsClient.event())) {
            return SIEGE_MUSIC;
        }
        return MOONS.get(MoonsClient.moon());
    }
}
