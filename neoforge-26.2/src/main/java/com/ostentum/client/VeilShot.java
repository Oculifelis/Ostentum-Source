package com.ostentum.client;

import com.ostentum.Ostentum;
import com.ostentum.weapon.PhantomVeil;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Dev client only (gradlew runClient -Pveilshot): the Phantom Veil's cloth (Fundamentum's Cloth). In a fresh flat
 * survival world, off screen and muted, the player wears the veil and runs a script (idle, walk, sprint, stop, fall,
 * crouch, crouch-walk, then crouch again in an iron chestplate) twice, filmed from behind and from the side by a camera
 * on a client-only marker. Every tick is saved as run/screenshots/veilshot-&lt;view&gt;-NNNN.png (join them with ffmpeg).
 * Logs "VEILSHOT" lines with the cloth's angles and "VEILSHOT DONE failed=N", then closes the game.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class VeilShot {
    static final boolean ON = Boolean.getBoolean("ostentum.veilshot");

    private static boolean fwd, sprint, crouch;

    private static final class ScriptInput extends ClientInput {
        @Override
        public void tick() {
            keyPresses = new Input(fwd, false, false, false, false, crouch, sprint);
            moveVector = new Vec2(0, fwd ? 1 : 0);
        }
    }

    private static final ScriptInput INPUT = new ScriptInput();

    /** One stretch: label, ticks, keys. "fall" lifts the player 14 blocks first, "armour" puts a chestplate on. */
    private record Phase(String label, int ticks, boolean fwd, boolean sprint, boolean crouch) {}

    private static final List<Phase> SCRIPT = List.of(
        new Phase("idle", 50, false, false, false),
        new Phase("walk", 50, true, false, false),
        new Phase("sprint", 50, true, true, false),
        new Phase("stop", 30, false, false, false),
        new Phase("fall", 50, false, false, false),
        new Phase("crouch", 30, false, false, true),
        new Phase("crouch-walk", 40, true, false, true),
        new Phase("armour", 40, false, false, true),
        new Phase("end", 20, false, false, false));

    private static final String[] VIEWS = {"behind", "side"};

    private static boolean hidden;
    private static boolean started;
    private static int ticks;
    private static int view;
    private static int phase = -1;
    private static int frame;
    private static int failed;
    private static Marker camera;
    private static float maxPitch;

    private VeilShot() {}

    private static void say(String s) {
        System.out.println("VEILSHOT " + s);
        if (s.startsWith("FAIL")) {
            failed++;
        }
    }

    /**
     * With the camera on the marker the player is not the "controlled camera", so vanilla skips copying the input into
     * its movement: set it here before the player ticks.
     */
    @SubscribeEvent
    public static void move(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ON || mc.player == null || phase < 0) {
            return;
        }
        mc.player.zza = fwd ? (crouch ? 0.3F : 1.0F) : 0.0F;
        mc.player.xxa = 0.0F;
        mc.player.setSprinting(sprint && fwd);
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
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
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
        if (phase < 0) {                                         // settle, wear the veil
            if (ticks == 2) {
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "time set noon"));
            }
            if (ticks == 60) {
                server.execute(() -> veil(server.getPlayerList().getPlayers().get(0)));
            }
            if (ticks == 100) {
                HudToggle.hide(mc, true);
                begin(mc, server);
            }
            return;
        }
        Phase ph = SCRIPT.get(phase);
        fwd = ph.fwd;
        sprint = ph.sprint;
        crouch = ph.crouch;
        p.setYRot(0.0F);
        p.setXRot(0.0F);
        follow(p);
        var sim = com.fundamentum.client.Cloth.sim(p, "ostentum:phantom_veil", WeaponsClient.VeilModel.SEGMENTS);
        maxPitch = Math.max(maxPitch, sim.pitch(3, 1));
        if (ticks % 10 == 0) {
            say("track " + VIEWS[view] + " " + ph.label + " t" + ticks + String.format(java.util.Locale.ROOT,
                " pitch %.1f %.1f %.1f %.1f roll %.1f", sim.pitch(0, 1), sim.pitch(1, 1), sim.pitch(2, 1), sim.pitch(3, 1), sim.roll(3, 1)));
        }
        String name = String.format(java.util.Locale.ROOT, "veilshot-%s-%04d.png", VIEWS[view], frame++);
        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, msg -> { });
        if (ticks >= ph.ticks) {
            if (ph.label.equals("sprint")) {
                say((maxPitch > 35 ? "PASS" : "FAIL") + " sprinting trails the hem (max hem pitch " + maxPitch + ")");
            }
            if (ph.label.equals("crouch-walk")) {
                say((sim.pitch(0, 1) >= com.fundamentum.client.ClothSim.CROUCH - 0.01 ? "PASS" : "FAIL") + " crouched cloth clears the body");
            }
            phase++;
            ticks = 0;
            if (phase >= SCRIPT.size()) {
                view++;
                if (view >= VIEWS.length) {
                    say("DONE failed=" + failed + " frames=" + frame);
                    mc.stop();
                    phase = SCRIPT.size() - 1;
                    return;
                }
                begin(mc, server);
                return;
            }
            Phase next = SCRIPT.get(phase);
            if (next.label.equals("fall")) {
                server.execute(() -> {
                    ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                    sp.teleportTo(sp.getX(), sp.getY() + 14, sp.getZ());
                });
            } else if (next.label.equals("armour")) {
                server.execute(() -> server.getPlayerList().getPlayers().get(0).setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE)));
            } else if (next.label.equals("end")) {
                server.execute(() -> server.getPlayerList().getPlayers().get(0).setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY));
            }
        }
    }

    /** Back to the start line, a fresh camera for this view, the script from the top. */
    private static void begin(Minecraft mc, MinecraftServer server) {
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.teleportTo(0.5, sp.getY(), 0.5);
            sp.snapTo(0.5, sp.getY(), 0.5, 0.0F, 0.0F);
        });
        if (camera == null) {
            camera = new Marker(EntityTypes.MARKER, mc.level);
            camera.setId(-424242);
            mc.level.addEntity(camera);
        }
        mc.setCameraEntity(camera);
        phase = 0;
        ticks = 0;
        frame = 0;
        maxPitch = 0;
        say("view " + VIEWS[view]);
    }

    /** The camera keeps its place beside or behind the player, looking at their back or side. */
    private static void follow(LocalPlayer p) {
        Vec3 at = p.position();
        if (view == 0) {
            camera.setPos(at.x + 0.6, at.y + 1.3, at.z - 3.6);
            camera.setYRot(-9.0F);
        } else {
            camera.setPos(at.x + 3.6, at.y + 1.2, at.z + 0.2);
            camera.setYRot(90.0F);
        }
        camera.setXRot(12.0F);
        camera.yRotO = camera.getYRot();
        camera.xRotO = camera.getXRot();
    }

    private static void veil(ServerPlayer p) {
        var curios = CuriosApi.getCuriosInventoryOrNull(p);
        var back = curios == null ? null : curios.getCurios().get(PhantomVeil.SLOT);
        if (back == null || back.getStacks().getSlots() == 0) {
            say("FAIL no back slot on the player");
            return;
        }
        back.getStacks().setStackInSlot(0, new ItemStack(PhantomVeil.VEIL.get()));
        say((PhantomVeil.wears(p) ? "PASS" : "FAIL") + " veil worn");
    }

    private static void create(Minecraft mc) {
        say("creating a flat world");
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(6);
        mc.options.simulationDistance().set(6);
        String name = "veilshot" + System.currentTimeMillis() / 1000;
        LevelSettings settings = new LevelSettings(name, GameType.SURVIVAL,
            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261106L, false, false),
            provider -> provider.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }
}
