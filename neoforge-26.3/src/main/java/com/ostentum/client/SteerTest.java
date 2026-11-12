package com.ostentum.client;

import com.fundamentum.mount.Mounts;
import com.ostentum.Ostentum;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Dev client only (-Dostentum.steertest=true, gradlew runClient -Psteertest): rides the four mounts Fundamentum steers
 * (pig, strider, llama, dolphin) in a fresh flat survival world on peaceful with scripted keys and mouse, and checks steps 2-7 and 10-12 of
 * the steering play-test plan. Keys go in at the ClientInput level (the player's input is swapped for a scripted one), so
 * the test does not depend on window focus. Every tick of every phase is logged as "STEERTEST track", each check as
 * "STEERTEST PASS|FAIL", and jitter, lag and client/server drift are written down as numbers, not judged.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class SteerTest {
    static final boolean ON = Boolean.getBoolean("ostentum.steertest");
    /** The mounts vanilla would not let a rider steer, which Fundamentum takes over. */
    private static final java.util.Set<String> STEERED = java.util.Set.of("pig", "strider", "llama", "dolphin");

    /** Keys held this tick; the scripted input reads them like KeyboardInput reads the keyboard. */
    private static boolean fwd, back, left, right, jump, sprint;

    private static final class ScriptInput extends ClientInput {
        @Override
        public void tick() {
            keyPresses = new Input(fwd, back, left, right, jump, false, sprint);
            float x = (left ? 1 : 0) - (right ? 1 : 0);
            float y = (fwd ? 1 : 0) - (back ? 1 : 0);
            Vec2 v = new Vec2(x, y);
            moveVector = v.lengthSquared() > 1 ? v.normalized() : v;
        }
    }

    private static final ScriptInput INPUT = new ScriptInput();

    /** One stretch of the script: what to do first, then every tick for {@code ticks} ticks, then what to check. */
    private record Phase(String label, int ticks, Consumer<Run> start, Consumer<Run> each, Consumer<Run> end) {}

    /** What a phase saw, tick by tick. */
    private static final class Run {
        final String label;
        final List<Vec3> client = new ArrayList<>();
        final List<Vec3> server = new ArrayList<>();
        final List<Float> bodyErr = new ArrayList<>();
        boolean riding = true;
        boolean burned;
        int firstBurn = -1;
        float startHealth = -1;
        float endHealth = -1;

        Run(String label) {
            this.label = label;
        }

        Vec3 first() {
            return client.isEmpty() ? Vec3.ZERO : client.get(0);
        }

        Vec3 last() {
            return client.isEmpty() ? Vec3.ZERO : client.get(client.size() - 1);
        }

        Vec3 moved() {
            return last().subtract(first());
        }

        double horizontal() {
            Vec3 d = moved();
            return Math.sqrt(d.x * d.x + d.z * d.z);
        }
    }

    private static final Deque<Phase> SCRIPT = new ArrayDeque<>();
    private static Phase phase;
    private static Run run;
    private static int left_ticks;
    private static int wait = 40;
    private static boolean started;
    private static boolean built;
    private static int passed;
    private static int failed;
    private static int ground;
    /** Forward speed per mount (blocks per tick), from step 4, for step 5's comparisons. */
    private static final java.util.Map<String, Double> SPEED = new java.util.HashMap<>();

    private SteerTest() {}

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
        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }
        LocalPlayer p = mc.player;
        if (p.input != INPUT) {
            p.input = INPUT;
        }
        if (!built) {
            built = true;
            build();
            say("script has " + SCRIPT.size() + " phases");
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (phase == null) {
            phase = SCRIPT.poll();
            if (phase == null) {
                say("DONE pass=" + passed + " fail=" + failed);
                mc.stop();
                return;
            }
            run = new Run(phase.label);
            left_ticks = phase.ticks;
            say("phase " + phase.label);
            try {
                phase.start.accept(run);
            } catch (Throwable t) {
                fail(phase.label + " start threw " + t);
            }
            return;
        }
        try {
            if (left_ticks > 0) {
                phase.each.accept(run);
                sample(mc, p, run);
                left_ticks--;
                return;
            }
            phase.end.accept(run);
        } catch (Throwable t) {
            fail(phase.label + " threw " + t);
        }
        fwd = back = left = right = jump = sprint = false;
        phase = null;
    }

    // ------------------------------------------------------------------------------------------------------- the world
    private static void create(Minecraft mc) {
        say("creating a flat world");
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(6);
        mc.options.simulationDistance().set(6);
        String name = "steertest" + System.currentTimeMillis() / 1000;
        LevelSettings settings = new LevelSettings(name, GameType.SURVIVAL,
            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261029L, false, false),
            provider -> provider.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }

    /** Lanes from z 0: flat at x 0, a 1-block step at x 10, a 2-block wall at x -10, a 6-deep pool at x 33, lava at x 53. */
    private static void arena(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
        int g = ground;
        fill(level, -20, g, -5, 60, g + 6, 65, Blocks.AIR.defaultBlockState());
        fill(level, 8, g, 5, 12, g, 20, Blocks.STONE.defaultBlockState());
        fill(level, -12, g, 5, -8, g + 1, 5, Blocks.STONE.defaultBlockState());
        fill(level, 27, g - 3, -1, 39, g + 2, 61, Blocks.GLASS.defaultBlockState());
        fill(level, 28, g - 3, 0, 38, g + 2, 60, Blocks.WATER.defaultBlockState());
        fill(level, 50, g - 1, 0, 56, g - 1, 25, Blocks.LAVA.defaultBlockState());
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            level.setBlock(pos, state, 2);
        }
    }

    // ------------------------------------------------------------------------------------------------------ the script
    private static void build() {
        server("build the arena", SteerTest::arena);
        // the Curios checks a FakePlayer cannot run (no slots), on this real player; they print OSTENTUM TEST lines
        server("mounts, hooks, wings and summons in use", server -> com.ostentum.SelfTest.curios(server.getPlayerList().getPlayers().get(0)));
        // a horse is steered by vanilla: Fundamentum must not mark it, and the rider must control it
        mount("horse", 0.5, 0, 1.5, 0);
        phase("horse: W for 10 s (vanilla steering, for comparison)", 200, r -> look(0, 0), r -> fwd = true, r -> {
            note("horse: cruising speed " + f(cruise(r)) + " b/t over " + f(r.horizontal()) + " blocks");
        });
        for (String m : List.of("pig", "strider", "llama", "dolphin")) {
            // (the horse above leaves its saddle on; mount() swaps saddles)
            boolean dolphin = m.equals("dolphin");
            // dolphins are checked in the pool; everything else on the flat lane
            double[] lane = dolphin ? new double[] {33.5, -2, 1.5} : new double[] {0.5, 0, 1.5};
            mount(m, lane[0], lane[1], lane[2], 0);
            still(m);
            turn(m);
            forward(m);
            backStrafe(m);
            if (!dolphin) {
                mount(m, 10.5, 0, 1.5, 0);
                step(m);
                mount(m, -9.5, 0, 1.5, 0);
                wall(m);
                mount(m, 0.5, 0, 1.5, 0);
                noJump(m);
            }
            if (dolphin) {
                mount(m, 33.5, -2, 1.5, 0);
                pitch(m, -45, "climbs", d -> d.y > 0.3);
                pitch(m, 45, "dives", d -> d.y < -0.3);
                pitch(m, 0, "keeps depth", d -> Math.abs(d.y) < 0.3);
                mount(m, 20.5, 0, 1.5, 0);
                dry(m);
            }
            if (m.equals("strider")) {
                server(m + ": a dry block to start on", server -> {
                    server.overworld().setBlock(new BlockPos(53, ground - 1, 2), Blocks.STONE.defaultBlockState(), 2);
                    server.getPlayerList().getPlayers().get(0).clearFire();
                });
                mount(m, 53.5, 0, 2.5, 0);
                lava(m);
            }
            server(m + ": put away", server -> Mounts.dismiss(server.getPlayerList().getPlayers().get(0)));
        }
    }

    /** Puts the saddle on, stands the player at (x, ground + dy, z) facing {@code yaw}, and summons with the V-key packet. */
    private static void mount(String m, double x, double dy, double z, float yaw) {
        server(m + ": saddle and place", server -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            Mounts.dismiss(sp);
            sp.stopRiding();
            var inv = CuriosApi.getCuriosInventoryOrNull(sp);
            inv.getCurios().get("mount").getStacks().setStackInSlot(0,
                new ItemStack(BuiltInRegistries.ITEM.getValue(Ostentum.id(m + "_saddle"))));
            sp.teleportTo(server.overworld(), x, ground + dy, z, java.util.Set.of(), yaw, 0, true);
        });
        phase(m + ": summon", 20, r -> {
            look(yaw, 0);
            ClientPacketDistributor.sendToServer(Mounts.TogglePayload.INSTANCE);
        }, r -> {}, r -> {
            LocalPlayer p = Minecraft.getInstance().player;
            Entity v = p.getVehicle();
            String got = v == null ? "nothing" : BuiltInRegistries.ENTITY_TYPE.getKey(v.getType()).getPath();
            check(m + ": seated on a " + m + " (on " + got + ")", got.equals(m));
            if (v != null) {
                Entity sv = Minecraft.getInstance().getSingleplayerServer().overworld().getEntity(v.getId());
                boolean vanilla = !STEERED.contains(m);
                check(m + ": " + (vanilla ? "left to vanilla (not marked)" : "marked for steering") + " by the summon (server "
                    + (sv != null && Mounts.steers(sv)) + ", client " + Mounts.steers(v) + ")",
                    sv != null && Mounts.steers(sv) != vanilla && Mounts.steers(v) != vanilla);
                check(m + ": the rider controls it", v.getControllingPassenger() == p);
            }
        });
    }

    // step 2
    private static void still(String m) {
        phase(m + " 2: no keys for 5 s", 100, r -> look(0, 0), r -> {}, r -> {
            check(m + " 2: stays put (moved " + f(r.horizontal()) + " client, " + f(serverMoved(r)) + " server)",
                r.horizontal() < 0.15 && serverMoved(r) < 0.15);
        });
    }

    // step 3
    private static void turn(String m) {
        float[] yaw = {0};
        phase(m + " 3: full slow circle", 120, r -> look(0, 0), r -> {
            yaw[0] += 3;
            look(yaw[0], 0);
        }, r -> {
            float worst = 0;
            for (int i = 2; i < r.bodyErr.size(); i++) {
                worst = Math.max(worst, r.bodyErr.get(i));
            }
            check(m + " 3: body follows the look within a tick (worst " + f(worst) + " deg; 3 deg = one tick)", worst <= 3.5);
            note(m + " 3: body error per tick " + r.bodyErr);
        });
    }

    // step 4
    private static void forward(String m) {
        phase(m + " 4: W for 10 s", 200, r -> look(0, 0), r -> fwd = true, r -> {
            Vec3 d = r.moved();
            double speed = cruise(r);
            SPEED.put(m, speed);
            double heading = Math.toDegrees(Math.atan2(-d.x, d.z));
            double snap = 0;
            List<Double> steps = new ArrayList<>();
            for (int i = 1; i < r.client.size(); i++) {
                double dz = r.client.get(i).z - r.client.get(i - 1).z;
                snap = Math.min(snap, dz);
                steps.add(dz);
            }
            check(m + " 4: goes where it looks (" + f(r.horizontal()) + " blocks, heading " + f(heading) + " deg)",
                speed > 0.03 && Math.abs(heading) < 15);
            check(m + " 4: never snaps back (worst step " + f(snap) + ")", snap > -0.2);
            note(m + " 4: speed " + f(speed) + " b/t, step spread " + f(spread(steps.subList(Math.min(40, steps.size()), steps.size())))
                + ", client/server drift worst " + f(drift(r)));
        });
    }

    // step 5
    private static void backStrafe(String m) {
        phase(m + " 5: S", 60, r -> look(0, 0), r -> back = true, r -> {
            double speed = r.horizontal() / r.client.size();
            double fwdSpeed = SPEED.getOrDefault(m, 0.0);
            check(m + " 5: S backs up slower than half forward (" + f(speed) + " vs " + f(fwdSpeed) + ")",
                r.moved().z < 0 && speed < fwdSpeed * 0.5);
        });
        phase(m + " 5: A", 60, r -> look(0, 0), r -> left = true, r -> {
            double speed = r.horizontal() / r.client.size();
            check(m + " 5: A strafes left, slower than forward (dx " + f(r.moved().x) + ", " + f(speed) + " b/t)",
                r.moved().x > 0.3 && speed < SPEED.getOrDefault(m, 0.0));
        });
        phase(m + " 5: D", 60, r -> look(0, 0), r -> right = true, r -> {
            check(m + " 5: D strafes right (dx " + f(r.moved().x) + ")", r.moved().x < -0.3);
        });
    }

    // step 6
    private static void step(String m) {
        phase(m + " 6: into a 1-block step", 80, r -> look(0, 0), r -> fwd = true, r -> {
            double top = r.client.stream().mapToDouble(v -> v.y).max().orElse(0) - ground;
            check(m + " 6: climbs the step (highest " + f(top) + " above ground)", top >= 0.9);
        });
    }

    private static void wall(String m) {
        phase(m + " 6: into a 2-block wall", 80, r -> look(0, 0), r -> fwd = true, r -> {
            check(m + " 6: stops at the wall (z " + f(r.last().z) + ", y " + f(r.last().y - ground) + ")",
                r.last().z < 5 && r.last().y < ground + 0.5);
        });
    }

    // step 7
    private static void noJump(String m) {
        phase(m + " 7: W with sprint and jump", 60, r -> look(0, 0), r -> {
            fwd = true;
            sprint = true;
            jump = true;
        }, r -> {
            double top = r.client.stream().mapToDouble(v -> v.y).max().orElse(0) - ground;
            check(m + " 7: no jump (highest " + f(top) + " above ground)", top < 0.6);
            note(m + " 7: cruising speed with sprint and jump " + f(cruise(r)) + " b/t (without " + f(SPEED.getOrDefault(m, 0.0)) + ")");
        });
    }

    // step 10
    private static void pitch(String m, float pitch, String what, java.util.function.Predicate<Vec3> ok) {
        phase(m + " 10: W at pitch " + pitch, 30, r -> look(0, pitch), r -> fwd = true, r -> {
            check(m + " 10: " + what + " at pitch " + pitch + " (dy " + f(r.moved().y) + ")", ok.test(r.moved()));
        });
    }

    // step 11
    private static void dry(String m) {
        phase(m + " 11: on land for 5 s", 100, r -> look(0, 0), r -> {}, r -> {
            check(m + " 11: out of water it stays alive and ridden (health " + f(r.startHealth) + " -> " + f(r.endHealth) + ")",
                r.riding && r.endHealth > 0);
            note(m + " 11: moved " + f(r.horizontal()) + " blocks on its own on land");
        });
    }

    // step 12
    private static void lava(String m) {
        phase(m + " 12: W on lava", 100, r -> look(0, 0), r -> fwd = true, r -> {
            double low = r.client.stream().mapToDouble(v -> v.y).min().orElse(0) - ground;
            check(m + " 12: walks on the lava (" + f(r.horizontal()) + " blocks, lowest " + f(low) + ")", r.horizontal() > 1 && low > -1.2);
            check(m + " 12: rider not burned" + (r.burned ? " (first alight at tick " + r.firstBurn + ")" : ""), !r.burned);
        });
    }

    // ------------------------------------------------------------------------------------------------------- plumbing
    private static void phase(String label, int ticks, Consumer<Run> start, Consumer<Run> each, Consumer<Run> end) {
        SCRIPT.add(new Phase(label, ticks, start, each, end));
    }

    /** A step on the integrated server's thread, then a short wait for it to land. */
    private static void server(String label, Consumer<MinecraftServer> fn) {
        phase(label, 10, r -> {
            MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
            server.execute(() -> {
                try {
                    fn.accept(server);
                } catch (Throwable t) {
                    fail(label + " threw " + t);
                }
            });
        }, r -> {}, r -> {});
    }

    private static void look(float yaw, float pitch) {
        LocalPlayer p = Minecraft.getInstance().player;
        p.setYRot(yaw);
        p.yRotO = yaw;
        p.setXRot(pitch);
        p.xRotO = pitch;
    }

    private static void sample(Minecraft mc, LocalPlayer p, Run r) {
        Entity v = p.getVehicle();
        r.riding &= v != null;
        if (p.isOnFire() && !r.burned) {
            r.firstBurn = r.client.size();
        }
        r.burned |= p.isOnFire();
        if (v == null) {
            say("track " + r.label + " t=" + r.client.size() + " not riding");
            return;
        }
        Vec3 c = v.position();
        Entity sv = mc.getSingleplayerServer() == null ? null : mc.getSingleplayerServer().overworld().getEntity(v.getId());
        Vec3 s = sv == null ? c : sv.position();
        r.client.add(c);
        r.server.add(s);
        float body = v instanceof LivingEntity l ? l.yBodyRot : v.getYRot();
        r.bodyErr.add(Math.abs(Mth.wrapDegrees(body - p.getYRot())));
        if (v instanceof LivingEntity l) {
            if (r.startHealth < 0) {
                r.startHealth = l.getHealth();
            }
            r.endHealth = l.getHealth();
        }
        say("track " + r.label + " t=" + r.client.size() + " c=" + v3(c) + " s=" + v3(s) + " yaw=" + f(p.getYRot())
            + " pitch=" + f(p.getXRot()) + " body=" + f(body) + " in=" + (fwd ? "W" : "") + (back ? "S" : "") + (left ? "A" : "")
            + (right ? "D" : "") + (jump ? "J" : "") + (sprint ? "R" : "") + " ground=" + v.onGround() + " water=" + v.isInWater()
            + " controlled=" + (v.getControllingPassenger() == p) + " steered=" + Mounts.steers(v) + " fire=" + p.getRemainingFireTicks()
            + " mountLava=" + v.isInLava());
    }

    /** Mean horizontal step per tick once at speed (after the first 20 ticks), so phases of any length compare. */
    private static double cruise(Run r) {
        double sum = 0;
        int n = 0;
        for (int i = 21; i < r.client.size(); i++) {
            Vec3 a = r.client.get(i - 1);
            Vec3 b = r.client.get(i);
            sum += Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.z - a.z) * (b.z - a.z));
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    private static double serverMoved(Run r) {
        if (r.server.isEmpty()) {
            return 0;
        }
        Vec3 d = r.server.get(r.server.size() - 1).subtract(r.server.get(0));
        return Math.sqrt(d.x * d.x + d.z * d.z);
    }

    private static double drift(Run r) {
        double worst = 0;
        for (int i = 0; i < r.client.size(); i++) {
            worst = Math.max(worst, r.client.get(i).distanceTo(r.server.get(i)));
        }
        return worst;
    }

    private static double spread(List<Double> xs) {
        double mean = xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Math.sqrt(xs.stream().mapToDouble(x -> (x - mean) * (x - mean)).average().orElse(0));
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        say((ok ? "PASS " : "FAIL ") + what);
    }

    private static void fail(String what) {
        check(what, false);
    }

    private static void note(String what) {
        say("NOTE " + what);
    }

    private static void say(String what) {
        System.out.println("STEERTEST " + what);
    }

    private static String f(double d) {
        return String.format(java.util.Locale.ROOT, "%.3f", d);
    }

    private static String v3(Vec3 v) {
        return "(" + f(v.x) + "," + f(v.y) + "," + f(v.z) + ")";
    }
}
