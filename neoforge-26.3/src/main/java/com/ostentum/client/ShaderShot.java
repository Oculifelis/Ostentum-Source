package com.ostentum.client;

import com.fundamentum.whip.Whips;
import com.fundamentum.whip.WhipsClient;
import com.ostentum.Ostentum;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.sdl.SDLVideo;

/**
 * Dev client only ({@code gradlew runClient -Pshadershot}, tag with {@code -Pshadershot=<tag>}): muted and off screen, in
 * a fresh flat creative world at night, screenshots the glowing whips and blades in first and third person at rest and
 * mid-crack, the held-whip grip with either hand, and the sky under each Ostentum moon, the siege and Fundamentum's
 * psychedelic moon; with shaders off, then (when Iris and a pack are installed) again with shaders on. Pictures go to
 * run/screenshots/shadershot-&lt;tag&gt;-&lt;off|on&gt;-*.png; log lines start with SHADERSHOT.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class ShaderShot {
    static final boolean ON = Boolean.getBoolean("ostentum.shadershot");
    private static final String TAG = System.getProperty("ostentum.shadershot.tag", "x");
    /** A tag with "grip" in it shoots only the grip, close up (narrow FOV, a bigger window, the body turned to show the hand). */
    private static final boolean GRIP_ONLY = TAG.contains("grip");

    private record Shot(String label, String item, CameraType camera, HumanoidArm arm, boolean swing, String sky) {}

    private static final String[][] GLOWING = {
        {"blaze", "ostentum:blaze_whip"}, {"glowberry", "ostentum:glow_berry_whip"}, {"ender", "ostentum:ender_whip"},
        {"endrift", "ostentum:end_rift_whip"}, {"amethyst", "ostentum:amethyst_whip"}, {"destruction", "ostentum:destruction_whip"},
        {"thunderstorm", "ostentum:thunderstorm_whip"}, {"sea", "ostentum:untamed_sea_whip"},
        {"hearthblade", "ostentum:hearthblade"}, {"echoblade", "ostentum:echo_blade"}};
    private static final String[][] GRIP = {{"leather", "ostentum:leather_whip"}, {"blaze", "ostentum:blaze_whip"},
        {"flora-moonwisp", "floraferox:moonwisp_whip"}};
    private static final String[][] SKIES = {{"none", ""}, {"harvest", "moon ostentum:harvest"}, {"enchanted", "moon ostentum:enchanted"},
        {"glimmer", "moon ostentum:glimmer"}, {"siege", "event ostentum:siege"}, {"psychedelic", "moon fundamentum:psychedelic"}, {"psychedelic-low", "moon fundamentum:psychedelic"}};

    private static List<Shot> shots(boolean shaders) {
        List<Shot> out = new ArrayList<>();
        for (String[] g : GLOWING) {
            boolean whip = g[1].endsWith("_whip");
            out.add(new Shot(g[0] + "-1st", g[1], CameraType.FIRST_PERSON, HumanoidArm.RIGHT, whip, null));
            out.add(new Shot(g[0] + "-front", g[1], CameraType.THIRD_PERSON_FRONT, HumanoidArm.RIGHT, whip, null));
        }
        if (GRIP_ONLY || TAG.contains("skyonly")) {   // "skyonly": the skies only
            out.clear();
        }
        if (!shaders) {
            for (String[] g : GRIP) {
                for (HumanoidArm arm : HumanoidArm.values()) {
                    String a = arm == HumanoidArm.RIGHT ? "right" : "left";
                    out.add(new Shot("grip-" + g[0] + "-1st-" + a, g[1], CameraType.FIRST_PERSON, arm, true, null));
                    out.add(new Shot("grip-" + g[0] + "-front-" + a, g[1], CameraType.THIRD_PERSON_FRONT, arm, true, null));
                    out.add(new Shot("grip-" + g[0] + "-back-" + a, g[1], CameraType.THIRD_PERSON_BACK, arm, true, null));
                }
            }
        }
        for (String[] s : GRIP_ONLY ? new String[0][] : SKIES) {
            out.add(new Shot("sky-" + s[0], "minecraft:air", CameraType.FIRST_PERSON, HumanoidArm.RIGHT, false, s[1]));
        }
        return out;
    }

    private static boolean started;
    private static boolean hidden;
    private static boolean shaders;
    private static List<Shot> list;
    private static int index;
    private static int step;
    private static int ticks;
    private static int failed;

    private ShaderShot() {}

    private static void say(String s) {
        System.out.println("SHADERSHOT " + s);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ON) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!hidden) {
            hidden = true;
            SDLVideo.SDL_SetWindowPosition(mc.getWindow().handle(), -4000, -4000);
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
            mc.options.pauseOnLostFocus = false;
            if (GRIP_ONLY) {
                SDLVideo.SDL_SetWindowSize(mc.getWindow().handle(), 1600, 900);
            }
            toggle(mc, false);
        }
        if (mc.level == null || mc.player == null) {
            if (!started && mc.gui.screen() instanceof TitleScreen) {
                started = true;
                create(mc);
            }
            return;
        }
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ticks++;
        if (step == 0) {                                        // settle, night, no rain
            if (ticks == 1) {
                list = TAG.endsWith("-ononly") ? new ArrayList<>() : shots(false);   // a tag ending -ononly skips the shaders-off pass
                cmd(server, "time set midnight");
                cmd(server, "weather clear 100000");
                cmd(server, "gamerule doDaylightCycle false");
                HudToggle.hide(mc, true);
            }
            if (ticks >= 140) {
                next(1);
            }
            return;
        }
        if (index >= list.size()) {
            if (!shaders && !GRIP_ONLY && ticks == 1 && toggle(mc, true)) {
                shaders = true;
                say("shaders on: " + packInUse());
                list = shots(true);
                index = 0;
                next(9);
                return;
            }
            if (ticks == 20) {
                say("DONE failed=" + failed);
                toggle(mc, false);
                mc.stop();
            }
            return;
        }
        Shot shot = list.get(index);
        String name = (shaders ? "on-" : "off-") + shot.label;
        switch (step) {
            case 9 -> {                                         // the pack compiles and settles
                if (ticks >= 200) {
                    next(1);
                }
            }
            case 1 -> {
                Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(shot.item)).orElse(null);
                if (item == null) {
                    say("SKIP " + name + ": no item " + shot.item);
                    index++;
                    next(1);
                    return;
                }
                mc.options.mainHand().set(shot.arm);
                mc.options.broadcastOptions();               // the server's copy of the main arm follows
                mc.options.setCameraType(shot.camera);
                HudToggle.hide(mc, shot.camera != CameraType.FIRST_PERSON || shot.sky != null);   // a hidden GUI hides the first-person hand too
                mc.options.fov().set(GRIP_ONLY && shot.camera != CameraType.FIRST_PERSON ? 30 : 70);
                cmd(server, shot.label.startsWith("grip") ? "time set noon" : "time set midnight");   // the grip by day, glow by night
                if (shot.sky != null && !shot.sky.isEmpty()) {
                    String[] s = shot.sky.split(" ");
                    cmd(server, "time set midnight");
                    cmd(server, s[0].equals("event") ? "fundamentum event raise \"" + s[1] + "\" 2000" : "fundamentum moon raise " + s[1]);
                }
                server.execute(() -> {
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                    p.snapTo(p.getX(), p.getY(), p.getZ(), shot.sky != null ? 180.0F : 0.0F, shot.sky != null ? (shot.label.endsWith("-low") ? 2.0F : -35.0F) : 10.0F);
                });
                mc.player.setYRot(shot.sky != null ? 180.0F : 0.0F);
                mc.player.setXRot(shot.sky != null ? (shot.label.endsWith("-low") ? 2.0F : -35.0F) : 10.0F);
                next(2);
            }
            case 2 -> {
                if (GRIP_ONLY && shot.camera != CameraType.FIRST_PERSON) {   // the body turned so the holding hand faces the camera
                    float turn = (shot.camera == CameraType.THIRD_PERSON_FRONT) == (shot.arm == HumanoidArm.RIGHT) ? 45.0F : -45.0F;
                    mc.player.yBodyRot = mc.player.yBodyRotO = mc.player.getYRot() + turn;
                }
                int wait = shot.sky != null ? 220 : 30;           // a moon's tint fades in over a few seconds
                if (ticks == wait) {
                    grab(mc, name + (shot.sky != null ? "" : "-idle"));
                    if (shot.swing) {
                        server.execute(() -> say(name + " swing started=" + Whips.swing(server.getPlayerList().getPlayers().get(0))));
                        next(3);
                    } else {
                        next(4);
                    }
                }
            }
            case 3 -> {                                         // mid-swing, then about the crack
                boolean swinging = WhipsClient.swinging(mc.player);
                if (swinging && !swung) {
                    swung = true;
                    ticks = 0;
                } else if (swung && ticks == 6) {
                    grab(mc, name + "-crack");
                } else if (!swung && ticks > 40) {
                    say("FAIL " + name + ": no swing seen on the client");
                    failed++;
                    next(4);
                } else if (swung && ticks > 7) {
                    swung = false;
                    next(4);
                }
            }
            case 4 -> {
                if (ticks == 1 && shot.sky != null && !shot.sky.isEmpty()) {
                    cmd(server, "fundamentum moon clear");
                    cmd(server, "fundamentum event clear");
                }
                if (ticks >= (shot.sky != null ? 160 : 40)) {
                    index++;
                    next(1);
                }
            }
            default -> {
            }
        }
    }

    private static boolean swung;

    private static void cmd(MinecraftServer server, String command) {
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command));
    }

    /** Iris's shaders on or off, through its internals (dev test only); false when Iris is not here. */
    private static boolean toggle(Minecraft mc, boolean on) {
        try {
            Class.forName("net.irisshaders.iris.Iris").getMethod("toggleShaders", Minecraft.class, boolean.class).invoke(null, mc, on);
            return true;
        } catch (Throwable t) {
            say("no Iris toggle (" + t + ")");
            return false;
        }
    }

    private static boolean packInUse() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            return (Boolean) api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));
        } catch (Throwable t) {
            return false;
        }
    }

    private static void next(int s) {
        step = s;
        ticks = 0;
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "shadershot-" + TAG + "-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
            msg -> say("saved shadershot-" + TAG + "-" + name + ".png"));
    }

    private static void create(Minecraft mc) {
        say("creating a flat world");
        mc.options.renderDistance().set(8);
        mc.options.simulationDistance().set(6);
        String name = "shadershot" + System.currentTimeMillis() / 1000;
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261106L, false, false),
            provider -> provider.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }
}
