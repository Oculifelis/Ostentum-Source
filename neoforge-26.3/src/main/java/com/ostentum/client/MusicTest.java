package com.ostentum.client;

import com.fundamentum.moon.MoonsClient;
import com.ostentum.Ostentum;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Dev client only (-Dostentum.musictest=true, gradlew runClient -Pmusictest): checks the moon and siege music and the
 * four discs on a muted client. SoundEngine is put on DEBUG, so every music request shows in the log as "Playing sound
 * X for event Y" (music starts silently at zero volume) and every record as "Skipped playing sound X, volume was zero.";
 * the test's own lines start with "MUSICTEST". It raises each moon and the siege with Fundamentum's commands, logs
 * what the music manager plays, clears them, puts each disc in a jukebox, and kills mobs to spot-check the disc drops.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class MusicTest {
    static final boolean ON = Boolean.getBoolean("ostentum.musictest");
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("ostentum.musictest");

    private record Step(int delay, String label, Consumer<Minecraft> action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int idx;
    private static int wait = 60;
    private static boolean started;
    private static boolean built;

    private MusicTest() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ON) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!started && mc.gui.screen() instanceof TitleScreen) {
                started = true;
                create(mc);
            }
            return;
        }
        if (mc.gui.screen() != null && mc.gui.screen().getBackgroundMusic() == null) {
            mc.gui.setScreen(null);
        }
        if (!built) {
            built = true;
            build();
            say("script has " + STEPS.size() + " steps");
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (idx >= STEPS.size()) {
            say("DONE");
            mc.stop();
            return;
        }
        Step s = STEPS.get(idx++);
        try {
            s.action.accept(mc);
        } catch (Throwable t) {
            say("FAIL step " + s.label + " threw " + t);
        }
        wait = s.delay;
    }

    private static void create(Minecraft mc) {
        say("creating a flat world");
        org.apache.logging.log4j.core.config.Configurator.setLevel("net.minecraft.client.sounds.SoundEngine", org.apache.logging.log4j.Level.DEBUG);
        mc.options.pauseOnLostFocus = false;
        mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        mc.options.renderDistance().set(6);
        mc.options.simulationDistance().set(6);
        String name = "musictest" + System.currentTimeMillis() / 1000;
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
            new LevelSettings.DifficultySettings(Difficulty.EASY, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261101L, false, false),
            provider -> provider.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }

    private static void step(int delay, String label, Consumer<Minecraft> action) {
        STEPS.add(new Step(delay, label, action));
    }

    private static void cmd(int delay, String command) {
        step(delay, "/" + command, mc -> {
            say("cmd /" + command);
            mc.player.connection.sendCommand(command);
        });
    }

    private static void log(String where) {
        step(1, "log " + where, mc -> {
            var music = net.neoforged.neoforge.client.ClientHooks.selectMusic(mc.getSituationalMusic(), null);
            say(where + ": moon=" + MoonsClient.moon() + " event=" + MoonsClient.event() + " selected="
                + (music == null ? "none" : music.sound().value().location()) + " current=" + mc.getMusicManager().getCurrentMusicTranslationKey());
        });
    }

    private static void countDiscs(String what) {
        step(5, "count " + what, mc -> {
            var server = mc.getSingleplayerServer();
            var player = server.getPlayerList().getPlayers().get(0);
            var counts = new java.util.TreeMap<String, Integer>();
            for (ItemEntity e : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(40))) {
                String id = BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString();
                if (id.contains("music_disc")) {
                    counts.merge(id, e.getItem().getCount(), Integer::sum);
                }
            }
            say("drops after " + what + ": " + counts);
        });
        cmd(5, "kill @e[type=item]");
    }

    private static void build() {
        cmd(5, "gamerule advance_time false");
        cmd(5, "gamerule spawn_mobs false");
        cmd(5, "weather clear");
        cmd(5, "time set noon");
        step(1, "frequency", mc -> {
            mc.options.musicFrequency().set(net.minecraft.client.sounds.MusicManager.MusicFrequency.CONSTANT);
            mc.getMusicManager().setMinutesBetweenSongs(net.minecraft.client.sounds.MusicManager.MusicFrequency.CONSTANT);
            say("SoundEngine debug=" + org.slf4j.LoggerFactory.getLogger("net.minecraft.client.sounds.SoundEngine").isDebugEnabled());
        });
        step(200, "settle", mc -> {});
        log("noon, nothing up");
        // jukebox: every disc
        for (String disc : new String[] {"harvest_moon", "enchanted_moon", "glimmer_moon", "fundamentum"}) {
            cmd(5, "setblock ~ ~ ~3 minecraft:air");
            cmd(5, "setblock ~ ~ ~3 minecraft:jukebox");
            cmd(80, "item replace block ~ ~ ~3 container.0 with ostentum:music_disc_" + disc);
        }
        cmd(5, "setblock ~ ~ ~3 minecraft:air");
        // each moon: raised at midnight, its track takes over, cleared, its track stops
        cmd(5, "time set midnight");
        step(150, "night settle", mc -> {});
        log("midnight, no moon");
        for (String moon : new String[] {"harvest", "enchanted", "glimmer"}) {
            cmd(160, "fundamentum moon raise ostentum:" + moon);
            log("moon " + moon + " up");
            cmd(5, "fundamentum moon query");
            // disc drop spot-check: hostiles killed by the player under this moon (2% each)
            for (int batch = 0; batch < 5; batch++) {
                for (int i = 0; i < 50; i++) {
                    cmd(0, "summon minecraft:zombie ~" + (i % 10 - 5) + " ~ ~" + (6 + i / 10) + " {NoAI:1b,Tags:[\"mt\"],Silent:1b}");
                }
                cmd(5, "execute as @e[type=zombie,tag=mt] run damage @s 1000 minecraft:player_attack by @p");
                step(25, "settle", mc -> {});
            }
            countDiscs("250 kills under " + moon);
            log("moon " + moon + " still up");
            cmd(5, "fundamentum moon clear");
            step(40, "cleared", mc -> {});
            log("moon " + moon + " cleared");
            step(150, "after", mc -> {});
            log("moon " + moon + " cleared +150");
        }
        // the siege, raised for 600 ticks, then ending on its own
        cmd(160, "fundamentum event raise \"ostentum:siege\" 600");
        log("siege up");
        cmd(5, "fundamentum event query");
        step(300, "siege running", mc -> {});
        log("siege +300");
        step(400, "siege over", mc -> {});
        cmd(5, "fundamentum event query");
        log("siege after its 600 ticks");
        step(150, "after", mc -> {});
        log("siege ended +150");
        // the siege again, cleared by command
        cmd(160, "fundamentum event raise \"ostentum:siege\"");
        log("siege up again");
        cmd(40, "fundamentum event clear");
        log("siege cleared");
        // the Fundamentum disc: the siege captain (25%)
        for (int i = 0; i < 24; i++) {
            cmd(0, "summon minecraft:pillager ~" + (i % 6 - 3) + " ~ ~" + (6 + i / 6) + " {NoAI:1b,Tags:[\"ostentum_siege_captain\",\"mt\"],Silent:1b}");
        }
        cmd(5, "execute as @e[type=pillager,tag=mt] run damage @s 1000 minecraft:player_attack by @p");
        step(25, "settle", mc -> {});
        countDiscs("24 siege captains killed");
        cmd(5, "time set noon");
        step(150, "noon", mc -> {});
        log("noon again");
    }

    private static void say(String what) {
        LOG.info("MUSICTEST {}", what);
    }
}
