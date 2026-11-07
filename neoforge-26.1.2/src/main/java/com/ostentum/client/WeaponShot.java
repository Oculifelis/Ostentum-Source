package com.ostentum.client;

import com.ostentum.Ostentum;
import com.ostentum.weapon.PathfindersGlaive;
import com.ostentum.weapon.PhantomVeil;
import java.util.List;
import java.util.function.Consumer;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
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
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Dev client only (-Dostentum.weaponshot=true, gradlew runClient -Pweaponshot): in a fresh flat creative world, holds
 * each 1.9.0 weapon from the front, wears the Phantom Veil front and back, throws the three tridents and plants the
 * glaive, saving run/screenshots/weaponshot-*.png, then closes the game. The window is moved off screen. Logs
 * "WEAPONSHOT" lines; a missing item, an unworn veil or a trident entity that never spawns is a FAIL.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class WeaponShot {
    static final boolean ON = Boolean.getBoolean("ostentum.weaponshot");

    /** One picture: what is held and worn, the camera, and an optional setup run on the server first. */
    private record Shot(String label, String item, CameraType camera, Consumer<ServerPlayer> setup) {
        Shot(String label, String item) {
            this(label, item, CameraType.THIRD_PERSON_FRONT, p -> {});
        }
    }

    private static final List<Shot> SHOTS = List.of(
        new Shot("weathervane", "ostentum:weathervane_spear"),
        new Shot("glaive", "ostentum:pathfinders_glaive"),
        new Shot("saber", "ostentum:duelists_saber"),
        new Shot("hearthblade", "ostentum:hearthblade"),
        new Shot("echo", "ostentum:echo_blade"),
        new Shot("hatchet", "ostentum:frostbite_hatchet"),
        new Shot("bloom", "ostentum:bloom_bow"),
        new Shot("voidstring", "ostentum:voidstring_bow"),
        new Shot("engineers", "ostentum:engineers_crossbow"),
        new Shot("bastion", "ostentum:bastion_crossbow"),
        new Shot("fisher-king", "ostentum:fisher_kings_trident"),
        new Shot("coral-1st", "ostentum:coral_trident", CameraType.FIRST_PERSON, p -> {}),
        new Shot("abyssal", "ostentum:abyssal_trident"),
        new Shot("quake", "ostentum:quake_hammer"),
        new Shot("delver", "ostentum:delvers_pick"),
        new Shot("chime", "ostentum:recall_chime"),
        new Shot("veil-front", "minecraft:air", CameraType.THIRD_PERSON_FRONT, p -> veil(p, true)),
        new Shot("veil-back", "minecraft:air", CameraType.THIRD_PERSON_BACK, p -> veil(p, true)),
        new Shot("veil-sneak-back", "minecraft:air", CameraType.THIRD_PERSON_BACK, p -> p.setShiftKeyDown(true)),
        new Shot("tridents-thrown", "minecraft:air", CameraType.THIRD_PERSON_BACK, p -> {
            p.setShiftKeyDown(false);
            veil(p, false);
            throwAll(p);
        }),
        new Shot("glaive-planted", "minecraft:air", CameraType.THIRD_PERSON_BACK, p -> {
            ItemStack glaive = new ItemStack(PathfindersGlaive.GLAIVE.get());
            PathfindersGlaive.plant(p.level(), p, glaive, p.blockPosition().offset(0, -1, 4));
            say((PathfindersGlaive.planted(glaive) ? "PASS" : "FAIL") + " glaive planted");
        }));

    private static boolean started;
    private static boolean hidden;
    private static int index;
    private static int step;
    private static int ticks;
    private static int failed;

    private WeaponShot() {}

    private static void say(String s) {
        System.out.println("WEAPONSHOT " + s);
        if (s.startsWith("FAIL")) {
            failed++;
        }
    }

    private static void veil(ServerPlayer p, boolean on) {
        var curios = CuriosApi.getCuriosInventoryOrNull(p);
        var back = curios == null ? null : curios.getCurios().get(PhantomVeil.SLOT);
        if (back == null || back.getStacks().getSlots() == 0) {
            say("FAIL no back slot on the player");
            return;
        }
        back.getStacks().setStackInSlot(0, on ? new ItemStack(PhantomVeil.VEIL.get()) : ItemStack.EMPTY);
        if (on) {
            say((PhantomVeil.wears(p) ? "PASS" : "FAIL") + " veil worn");
        }
    }

    /** The three tridents thrown ahead of the player, side by side, so their entity renderers show. */
    private static void throwAll(ServerPlayer p) {
        String[] names = {"fisher_kings_trident", "coral_trident", "abyssal_trident"};
        for (int i = 0; i < names.length; i++) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Ostentum.MODID, names[i]));
            Entity thrown = item instanceof net.minecraft.world.item.ProjectileItem projectile
                ? projectile.asProjectile(p.level(), p.position().add(i - 1, 1.6, 2), new ItemStack(item), net.minecraft.core.Direction.SOUTH)
                : null;
            if (thrown == null) {
                say("FAIL " + names[i] + " makes no projectile");
                continue;
            }
            thrown.setDeltaMovement(0, 0.25, 0.8);
            p.level().addFreshEntity(thrown);
            say("PASS " + names[i] + " thrown as " + BuiltInRegistries.ENTITY_TYPE.getKey(thrown.getType())
                + (thrown instanceof ThrownTrident ? "" : " (not a trident!)"));
        }
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
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                create(mc);
            }
            return;
        }
        if (mc.screen != null && index < SHOTS.size()) {
            mc.setScreen(null);
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ticks++;
        if (step == 0) {                                        // let the world settle
            if (ticks == 2) {
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "time set noon"));
            }
            if (ticks >= 100) {
                next(1);
            }
            return;
        }
        if (index >= SHOTS.size()) {
            if (ticks == 20) {
                say("DONE failed=" + failed);
                mc.stop();
            }
            return;
        }
        Shot shot = SHOTS.get(index);
        if (step == 1) {
            Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(shot.item)).orElse(null);
            if (item == null) {
                say("FAIL " + shot.label + ": no item " + shot.item);
                index++;
                return;
            }
            mc.options.setCameraType(shot.camera);
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                p.snapTo(p.getX(), p.getY(), p.getZ(), 0.0F, 10.0F);
                shot.setup.accept(p);
            });
            mc.player.setYRot(0.0F);
            mc.player.setXRot(10.0F);
            next(2);
        } else if (step == 2 && ticks == (shot.label.equals("tridents-thrown") ? 6 : 30)) {
            grab(mc, shot.label);
            index++;
            next(1);
        }
    }

    private static void next(int s) {
        step = s;
        ticks = 0;
    }

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "weaponshot-" + name + ".png", mc.getMainRenderTarget(), 1,
            msg -> say("saved weaponshot-" + name + ".png"));
    }

    private static void create(Minecraft mc) {
        say("creating a flat world");
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(6);
        mc.options.simulationDistance().set(6);
        String name = "weaponshot" + System.currentTimeMillis() / 1000;
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261104L, false, false),
            provider -> provider.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }
}
