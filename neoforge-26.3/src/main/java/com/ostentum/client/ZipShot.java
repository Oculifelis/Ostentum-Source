package com.ostentum.client;

import com.ostentum.Ostentum;
import com.ostentum.weapon.EngineersCrossbow;
import com.ostentum.weapon.Zipline;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.sdl.SDLVideo;

/**
 * Dev client only (gradlew runClient -Pzipshot): the Engineer's Crossbow zipline. In a fresh flat world, off screen and
 * muted, three ropes are strung between stone posts and the player rides each (down a steep one, up a gentle one
 * holding forward, along a flat one), filmed from the side by a camera on a client-only marker: every tick is saved as
 * run/screenshots/zipshot-&lt;ride&gt;-NNNN.png. Then the HUD is shot in first person at GUI scale 2, 3 and 4, in combat
 * mode and in rope mode with an anchor set (zipshot-hud-&lt;mode&gt;-gsN.png). Logs "ZIPSHOT" lines and
 * "ZIPSHOT DONE failed=N", then closes the game.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class ZipShot {
    static final boolean ON = Boolean.getBoolean("ostentum.zipshot");

    private static boolean fwd;

    private static final class ScriptInput extends ClientInput {
        @Override
        public void tick() {
            keyPresses = new Input(fwd, false, false, false, false, false, false);
            moveVector = new Vec2(0, fwd ? 1 : 0);
        }
    }

    private static final ScriptInput INPUT = new ScriptInput();

    /** One ride: label, rope ends (relative to the spawn point), where on the rope to catch it, ticks, forward held. */
    private record Ride(String label, Vec3 a, Vec3 b, double t, int ticks, boolean fwd) {}

    private static final List<Ride> RIDES = List.of(
        new Ride("down", new Vec3(-12, 10, 0), new Vec3(12, 4, 0), 0.1, 80, false),
        new Ride("up", new Vec3(-12, 6, 10), new Vec3(12, 8, 10), 0.12, 150, true),
        new Ride("flat", new Vec3(-10, 7, 20), new Vec3(10, 7, 20), 0.15, 140, false));

    /** -Dostentum.zipshot.hudonly=true: skip the rides and shoot only the HUD (Fundamentum's item HUD). */
    private static final boolean HUD_ONLY = Boolean.getBoolean("ostentum.zipshot.hudonly");
    private static final String[] STYLES = {"text"};
    private static final int[] SCALES = HUD_ONLY ? new int[] {2, 3} : new int[] {2, 3, 4};

    private static boolean hidden;
    private static boolean started;
    private static int ticks;
    private static int ride = -1;
    private static int hud = -1;
    private static int frame;
    private static int failed;
    private static Marker camera;
    private static Vec3 origin;
    private static double startT;

    private ZipShot() {}

    private static void say(String s) {
        System.out.println("ZIPSHOT " + s);
        if (s.startsWith("FAIL")) {
            failed++;
        }
    }

    @SubscribeEvent
    public static void move(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ON || mc.player == null || ride < 0) {
            return;
        }
        INPUT.keyPresses = new Input(fwd, false, false, false, false, false, false);
        mc.player.zza = fwd ? 1.0F : 0.0F;
        mc.player.xxa = 0.0F;
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
            SDLVideo.SDL_SetWindowSize(mc.getWindow().handle(), 1920, 1080);
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        }
        if (mc.level == null || mc.player == null) {
            if (!started && mc.gui.screen() instanceof TitleScreen) {
                started = true;
                create(mc);
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        LocalPlayer p = mc.player;
        if (p.input != INPUT) {
            p.input = INPUT;
        }
        ticks++;
        if (ride < 0 && hud < 0) {
            if (ticks == 2) {
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "time set noon"));
            }
            if (ticks == 60) {
                origin = p.position();
                server.execute(() -> build(server));
            }
            if (ticks == 90) {
                if (HUD_ONLY) {
                    startHuds(mc, server, p);
                    return;
                }
                HudToggle.hide(mc, true);
                begin(mc, server, 0);
            }
            return;
        }
        if (ride >= 0) {
            riding(mc, server, p);
        } else {
            huds(mc, server, p);
        }
    }

    private static void riding(Minecraft mc, MinecraftServer server, LocalPlayer p) {
        Ride r = RIDES.get(ride);
        fwd = r.fwd && ticks > 10;
        // face along the rope toward b, so "forward" climbs toward it
        p.setYRot(-90.0F);
        p.setXRot(0.0F);
        follow(p);
        if (ticks > 3) {
            Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "zipshot-%s-%04d.png", r.label, frame++), mc.gameRenderer.mainRenderTarget(), 1, msg -> { });
        }
        if (ticks % 10 == 0) {
            say(String.format(Locale.ROOT, "client %s t%d player %.1f %.1f %.1f %s, camera %.1f %.1f %.1f", r.label, ticks, p.getX(), p.getY(), p.getZ(),
                p.getVehicle() instanceof Zipline.Rider ? "riding" : "not riding", camera.getX(), camera.getY(), camera.getZ()));
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                if (sp.getVehicle() instanceof Zipline.Rider rider) {
                    say(String.format(Locale.ROOT, "track %s t%d rope t %.3f speed %.3f%s", r.label, ticks, rider.t(), rider.speed(), rider.blocked() ? " blocked" : ""));
                } else {
                    say("track " + r.label + " t" + ticks + " not riding");
                }
            });
        }
        if (ticks >= r.ticks) {
            fwd = false;
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                if (sp.getVehicle() instanceof Zipline.Rider rider) {
                    double t = rider.t();
                    boolean ok = switch (r.label) {
                        case "down" -> t > 0.9;
                        case "up" -> t > startT + 0.15;
                        default -> Math.abs(t - 0.5) < 0.2;
                    };
                    say((ok ? "PASS" : "FAIL") + String.format(Locale.ROOT, " ride %s: caught at t %.2f, ended at t %.3f", r.label, startT, t));
                    rider.letGo((ServerLevel) sp.level(), false);
                } else {
                    say("FAIL ride " + r.label + ": not on the rope at the end");
                }
            });
            if (ride + 1 < RIDES.size()) {
                begin(mc, server, ride + 1);
            } else {
                startHuds(mc, server, p);
            }
        }
    }

    private static void startHuds(Minecraft mc, MinecraftServer server, LocalPlayer p) {
        ride = -1;
        hud = 0;
        ticks = 0;
        mc.setCameraEntity(p);
        HudToggle.hide(mc, false);
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.teleportTo(origin.x, origin.y, origin.z - 6);
            sp.snapTo(origin.x, origin.y, origin.z - 6, 0.0F, 10.0F);
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EngineersCrossbow.CROSSBOW.get()));
            sp.getInventory().add(new ItemStack(Items.ARROW, 23));
        });
    }

    /** HUD shots, per style: combat (3 bolts loaded in HUD-only runs) then rope mode, each at every GUI scale. */
    private static void huds(Minecraft mc, MinecraftServer server, LocalPlayer p) {
        int perStyle = SCALES.length * 2;
        String style = STYLES[hud / perStyle];
        int mode = hud % perStyle / SCALES.length;
        int scale = SCALES[hud % SCALES.length];
        if (ticks == 1) {
            if (hud % perStyle == 0 && HUD_ONLY) {
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                    ItemStack bow = new ItemStack(EngineersCrossbow.CROSSBOW.get());
                    bow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW),
                        new ItemStack(Items.ARROW), new ItemStack(Items.ARROW))));
                    sp.setItemInHand(InteractionHand.MAIN_HAND, bow);
                });
            }
            if (hud % perStyle == SCALES.length) {
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                    sp.getMainHandItem().remove(DataComponents.CHARGED_PROJECTILES);
                    CustomData.update(DataComponents.CUSTOM_DATA, sp.getMainHandItem(), tag -> {
                        tag.putBoolean("rope", true);
                        tag.putLong("anchor_block", BlockPos.containing(origin).asLong());
                        tag.putString("anchor_dim", sp.level().dimension().identifier().toString());
                    });
                });
            }
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        }
        if (ticks == 25) {
            boolean rope = EngineersCrossbow.ropeMode(p.getMainHandItem());
            List<net.minecraft.network.chat.Component> lines = com.fundamentum.client.ItemHudOverlay.lines();
            String first = lines.isEmpty() ? "" : lines.get(0).getString();
            say((lines.size() == 2 && first.startsWith(mode == 1 ? "Rope" : "Combat") ? "PASS" : "FAIL") + " item HUD lines: " + lines.stream()
                .map(net.minecraft.network.chat.Component::getString).toList());
            say((rope == (mode == 1) ? "PASS" : "FAIL") + " hud " + style + " " + (mode == 1 ? "rope" : "combat") + " at gui scale " + scale + " (window "
                + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + ", gui " + mc.getWindow().getGuiScaledWidth() + "x"
                + mc.getWindow().getGuiScaledHeight() + ")");
            String name = HUD_ONLY ? String.format(Locale.ROOT, "zipshot-hud-%s-%s-gs%d.png", style, mode == 1 ? "rope" : "combat", scale)
                : String.format(Locale.ROOT, "zipshot-hud-%s-gs%d.png", mode == 1 ? "rope" : "combat", scale);
            Screenshot.grab(mc.gameDirectory, name,
                mc.gameRenderer.mainRenderTarget(), 1, msg -> { });
        }
        if (ticks == 30) {
            hud++;
            ticks = 0;
            if (hud >= perStyle * STYLES.length) {
                HudToggle.hide(mc, true);
                say("HUD hidden with F1: " + (mc.gui.hud.isHidden() ? "PASS (the layer returns early on hideGui)" : "FAIL"));
                say("DONE failed=" + failed + " frames=" + frame);
                mc.stop();
                hud = perStyle * STYLES.length - 1;
                ticks = -100000;
            }
        }
    }

    /** Stone posts at each rope end and the ropes strung between them. */
    private static void build(MinecraftServer server) {
        ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
        ServerLevel level = (ServerLevel) sp.level();
        for (Ride r : RIDES) {
            Vec3[] ends = {origin.add(r.a), origin.add(r.b)};
            BlockPos[] blocks = new BlockPos[2];
            for (int i = 0; i < 2; i++) {
                blocks[i] = BlockPos.containing(ends[i]);
                for (int y = BlockPos.containing(origin).getY(); y <= blocks[i].getY(); y++) {
                    level.setBlock(new BlockPos(blocks[i].getX(), y, blocks[i].getZ()), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                }
                ends[i] = Vec3.atCenterOf(blocks[i]);
            }
            Zipline line = EngineersCrossbow.string(level, sp.getUUID(), ends[0], blocks[0], ends[1], blocks[1]);
            say((line != null ? "PASS" : "FAIL") + " rope " + r.label + " strung" + (line == null ? "" : String.format(Locale.ROOT, " (%.1f blocks)", line.length())));
        }
    }

    /** Puts the player on ride {@code index}'s rope, a fresh camera beside it. */
    private static void begin(Minecraft mc, MinecraftServer server, int index) {
        Ride r = RIDES.get(index);
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) sp.level();
            Vec3 a = Vec3.atCenterOf(BlockPos.containing(origin.add(r.a)));
            Vec3 b = Vec3.atCenterOf(BlockPos.containing(origin.add(r.b)));
            Vec3 at = Zipline.point(a, b, r.t);
            sp.teleportTo(at.x, at.y - Zipline.HANG, at.z);
            sp.setDeltaMovement(Vec3.ZERO);
            Zipline.Catch c = Zipline.reach(level, at, 1.0);
            Zipline.Rider rider = c == null ? null : Zipline.attach(level, sp, c.line(), c.t());
            startT = c == null ? -1 : c.t();
            say((rider != null ? "PASS" : "FAIL") + " caught the " + r.label + " rope");
        });
        if (camera == null) {
            camera = new Marker(EntityTypes.MARKER, mc.level);
            camera.setId(-434343);
            mc.level.addEntity(camera);
        }
        mc.setCameraEntity(camera);
        ride = index;
        ticks = 0;
        frame = 0;
        say("ride " + r.label);
    }

    /** The camera stays square to the ropes (which run along x), a few blocks off, keeping the player in frame. */
    private static void follow(LocalPlayer p) {
        Vec3 at = p.position();
        camera.setPos(at.x, at.y + 1.6, at.z - 8.5);
        camera.setYRot(0.0F);
        camera.setXRot(-4.0F);
        camera.yRotO = camera.getYRot();
        camera.xRotO = camera.getXRot();
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
    }

    private static void create(Minecraft mc) {
        say("creating a flat world");
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(6);
        mc.options.simulationDistance().set(6);
        String name = "zipshot" + System.currentTimeMillis() / 1000;
        LevelSettings settings = new LevelSettings(name, GameType.SURVIVAL,
            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261106L, false, false),
            provider -> provider.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }
}
