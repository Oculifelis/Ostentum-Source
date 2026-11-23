package com.ostentum.client;

import com.fundamentum.whip.Whips;
import com.fundamentum.whip.WhipsClient;
import com.fundamentum.whip.WhipsConfig;
import com.ostentum.Ostentum;
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
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Dev client only (-Dostentum.whipshot=true, gradlew runClient -Pwhipshot): in a fresh flat creative world, holds each
 * listed whip, saves a screenshot at rest and one mid-swing (run/screenshots/whipshot-*.png), in first person and from
 * the front, with Fundamentum's whips.flatHandles off and on, then closes the game. The window is moved off screen.
 * Logs "WHIPSHOT" lines; a whip that is missing or never swings is a FAIL.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class WhipShot {
    static final boolean ON = Boolean.getBoolean("ostentum.whipshot");

    private record Shot(String label, String item, CameraType camera, boolean flat, boolean night) {
        Shot(String label, String item, CameraType camera, boolean flat) {
            this(label, item, camera, flat, false);
        }
    }

    /**
     * 1.8.5 whip-fixes check (Fundamentum 1.22.3): the swing comes down in front, the crack where aimed, Ars Moriendi's
     * vine whip coiled at rest, Fieldframe's electric whips without white sparks, and one whip of each other mod.
     */
    private static final List<Shot> SHOTS = List.of(
        new Shot("vine-front", "ostentum:vine_whip", CameraType.THIRD_PERSON_FRONT, false),
        new Shot("vine-1st", "ostentum:vine_whip", CameraType.FIRST_PERSON, false),
        new Shot("thunderstorm-front", "ostentum:thunderstorm_whip", CameraType.THIRD_PERSON_FRONT, false),
        new Shot("am-pampinus-front", "arsmoriendi:pampinus_sponsae", CameraType.THIRD_PERSON_FRONT, false),
        new Shot("am-pampinus-1st", "arsmoriendi:pampinus_sponsae", CameraType.FIRST_PERSON, false),
        new Shot("ff-arc-front", "fieldframe:arc_whip", CameraType.THIRD_PERSON_FRONT, false),
        new Shot("ff-tesla-1st", "fieldframe:tesla_whip", CameraType.FIRST_PERSON, false),
        new Shot("av-elemental-front", "arcanavaria:elemental_whip", CameraType.THIRD_PERSON_FRONT, false),
        new Shot("flora-moonwisp-front", "floraferox:moonwisp_whip", CameraType.THIRD_PERSON_FRONT, false),
        new Shot("ur-crimlash-front", "ultimaratio:crimlash", CameraType.THIRD_PERSON_FRONT, false));

    private static boolean started;
    private static boolean hidden;
    private static int index;
    private static int step;
    private static int ticks;
    private static int failed;

    private WhipShot() {}

    private static void say(String s) {
        System.out.println("WHIPSHOT " + s);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ON) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!hidden) {
            hidden = true;
            GLFW.glfwSetWindowPos(mc.getWindow().handle(), -4000, -4000);
        }
        if (mc.level == null || mc.player == null) {
            if (!started && mc.gui.screen() instanceof TitleScreen) {
                started = true;
                create(mc);
            }
            return;
        }
        if (mc.gui.screen() != null && index < SHOTS.size()) {
            mc.gui.setScreen(null);
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ticks++;
        if (step == 0) {                                        // let the world settle (a new world starts in the morning)
            if (ticks < 100) {
                return;
            }
            next(1);
            return;
        }
        if (index >= SHOTS.size()) {
            if (ticks == 20) {                                  // Fundamentum's Config screen, where the switch lives
                WhipsConfig.FLAT_HANDLES.set(false);
                ModList.get().getModContainerById("fundamentum").ifPresent(c -> IConfigScreenFactory.getForMod(c.getModInfo())
                    .ifPresentOrElse(f -> mc.gui.setScreen(f.createScreen(c, null)), () -> {
                        say("FAIL fundamentum has no config screen");
                        failed++;
                    }));
            } else if (ticks == 60) {
                grab(mc, "fundamentum-config");
                say("config screen: " + (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName()));
            } else if (ticks == 80) {
                say("DONE failed=" + failed);
                mc.stop();
            }
            return;
        }
        Shot shot = SHOTS.get(index);
        switch (step) {
            case 1 -> {                                         // hold it, set the camera and the flat-handle option
                Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(shot.item)).orElse(null);
                if (item == null) {
                    say("FAIL " + shot.label + ": no item " + shot.item);
                    failed++;
                    index++;
                    next(1);
                    return;
                }
                WhipsConfig.FLAT_HANDLES.set(shot.flat);
                mc.options.setCameraType(shot.camera);
                server.execute(() -> {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                        shot.night ? "time set midnight" : "time set noon");
                    ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                    p.snapTo(p.getX(), p.getY(), p.getZ(), 0.0F, 10.0F);
                });
                mc.player.setYRot(0.0F);
                mc.player.setXRot(10.0F);
                next(2);
            }
            case 2 -> {                                         // at rest
                if (ticks == 30) {
                    grab(mc, shot.label + "-idle");
                    server.execute(() -> {
                        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                        say(shot.label + " swing started=" + Whips.swing(sp));
                    });
                    next(3);
                }
            }
            case 3 -> {                                         // mid-swing, a few ticks after the client sees it
                boolean swinging = WhipsClient.swinging(mc.player);
                if (swinging && ticks >= 3 && !swingShot) {
                    grab(mc, shot.label + "-swing");
                    say("PASS " + shot.label + " swinging on the client, flatHandles=" + WhipsConfig.FLAT_HANDLES.getAsBoolean());
                    swingShot = true;
                } else if (swingShot && ticks >= 9) {             // about the crack (Fundamentum 1.22.3: out in front)
                    grab(mc, shot.label + "-crack");
                    swingShot = false;
                    next(4);
                } else if (ticks > 40) {
                    say("FAIL " + shot.label + ": no swing seen on the client");
                    failed++;
                    next(4);
                }
            }
            case 4 -> {                                         // let the swing and cooldown run out
                if (ticks >= 40) {
                    index++;
                    next(1);
                }
            }
            default -> {
            }
        }
    }

    private static boolean swingShot;

    private static void next(int s) {
        step = s;
        ticks = 0;
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "whipshot-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
            msg -> say("saved whipshot-" + name + ".png"));
    }

    private static void create(Minecraft mc) {
        say("creating a flat world");
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(6);
        mc.options.simulationDistance().set(6);
        String name = "whipshot" + System.currentTimeMillis() / 1000;
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261101L, false, false),
            provider -> provider.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }
}
