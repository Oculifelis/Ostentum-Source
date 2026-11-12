package com.ostentum.client;

import com.fundamentum.music.JukeboxProgress.Track;
import com.fundamentum.music.client.JukeboxTracker;
import com.ostentum.Ostentum;
import com.ostentum.music.ChiseledRecordShelfBlock;
import com.ostentum.music.ChiseledRecordShelfBlockEntity;
import com.ostentum.music.NowPlaying;
import com.ostentum.music.RecordShelves;
import java.util.Optional;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSongs;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Dev client only ({@code gradlew runClient -Pnowplayingshot}): muted and off screen, in a fresh flat world it wears the
 * Record Pendant, plays cat in a jukebox and screenshots the Now Playing HUD in survival (hearts and hotbar showing,
 * 1920x1080) with the default config at the song's start and half way through at GUI scale 2 and 3, then with other
 * settings (top right, disc icon, no backdrop, teal text, scale 1.5; then the bar alone); checks the tracker's nearest-jukebox
 * choice; then shows the shelves (Record Shelf, Chiseled Record Shelf empty, half and full) and an enchanting table
 * ringed with shelves, outside and with its screen open. Saves run/screenshots/nowplaying-*.png, logs NOWPLAYING lines,
 * closes the game.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class NowPlayingShot {
    static final boolean ON = Boolean.getBoolean("ostentum.nowplayingshot");

    private static boolean hidden;
    private static boolean started;
    private static int ticks;
    private static int failed;
    private static BlockPos feet;
    private static BlockPos box;

    private NowPlayingShot() {
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
            GLFW.glfwSetWindowSize(mc.getWindow().handle(), 1920, 1080);
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
            mc.options.pauseOnLostFocus = false;
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
        ticks++;
        if (ticks < 400 && mc.gui.screen() != null && !(mc.gui.screen() instanceof EnchantmentScreen)) {
            mc.gui.setScreen(null);
        }
        switch (ticks) {
            case 40 -> server.execute(() -> {
                ServerPlayer p = player(server);
                run(server, "time set noon");
                p.setGameMode(GameType.SURVIVAL);
                feet = p.blockPosition();
                p.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 15.0F);
                var curios = CuriosApi.getCuriosInventoryOrNull(p);
                var neck = curios == null ? null : curios.getCurios().get(NowPlaying.SLOT);
                if (NowPlaying.PENDANT == null) {
                    say("NOTE -Pnopendant build: no pendant, the HUD shows by withoutPendant (" + NowPlaying.WITHOUT_PENDANT.get() + ")");
                } else if (neck == null || neck.getStacks().getSlots() == 0) {
                    fail("no necklace slot on the player");
                } else {
                    neck.getStacks().setStackInSlot(0, new ItemStack(NowPlaying.PENDANT.get()));
                }
                ServerLevel level = p.level();
                box = feet.offset(1, 0, 4);
                level.setBlockAndUpdate(box, Blocks.JUKEBOX.defaultBlockState());
                if (level.getBlockEntity(box) instanceof JukeboxBlockEntity j) {
                    j.setTheItem(new ItemStack(Items.MUSIC_DISC_CAT));
                }
                p.getInventory().setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
                p.getInventory().setItem(1, new ItemStack(RecordShelves.CHISELED_RECORD_SHELF_ITEM.get()));
                p.getInventory().setItem(2, new ItemStack(RecordShelves.RECORD_SHELF_ITEM.get()));
            });
            case 41 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setYRot(0.0F);
                mc.player.setXRot(15.0F);
            }
            case 100 -> {
                say((NowPlayingHud.worn() || NowPlaying.PENDANT == null ? "PASS" : "FAIL") + " the pendant is worn (client sees it)");
                Optional<Track> t = NowPlayingHud.track();
                say((t.isPresent() && t.get().pos().equals(box) ? "PASS" : "FAIL") + " the HUD shows the jukebox: "
                    + t.map(x -> x.name().getString() + " " + x.timeText()).orElse("nothing"));
                // the nearest-jukebox maths: one farther but in range, one out of cat's 64-block range
                var cat = mc.level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG).getOrThrow(JukeboxSongs.CAT);
                var far = mc.level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG).getOrThrow(JukeboxSongs.STAL);
                BlockPos mid = feet.offset(20, 0, 20);
                BlockPos out = feet.offset(70, 0, 0);
                JukeboxTracker.started(mid, far);
                JukeboxTracker.started(out, far);
                Optional<Track> near = JukeboxTracker.nearest(mc.player.getEyePosition());
                say((near.isPresent() && near.get().pos().equals(box) ? "PASS" : "FAIL") + " nearest of three is the one 4 blocks away");
                JukeboxTracker.stopped(box);
                Optional<Track> next = JukeboxTracker.nearest(mc.player.getEyePosition());
                say((next.isPresent() && next.get().pos().equals(mid) ? "PASS" : "FAIL") + " with it stopped, the one 28 blocks away");
                JukeboxTracker.stopped(mid);
                say((JukeboxTracker.nearest(mc.player.getEyePosition()).isEmpty() ? "PASS" : "FAIL")
                    + " one 70 blocks away is out of hearing (" + JukeboxTracker.playing().size() + " tracked)");
                JukeboxTracker.stopped(out);
                JukeboxTracker.sync(box, cat, 60);                 // back to the real one, 3 s in
            }
            case 102 -> gui(mc, 2);
            case 110 -> shot(mc, "hud-1-start-gs2");
            case 120 -> {
                var cat = mc.level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG).getOrThrow(JukeboxSongs.CAT);
                JukeboxTracker.sync(box, cat, 92 * 20);            // half way: the client's clock jumps, the bar follows
            }
            case 130 -> {
                say((NowPlayingHud.track().map(t -> t.progress() > 0.49F).orElse(false) ? "PASS" : "FAIL") + " half way the bar is at "
                    + NowPlayingHud.track().map(Track::timeText).orElse("none"));
                int[] b = NowPlayingHud.box(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), NowPlayingHud.track().orElseThrow());
                say((b[0] == 4 && b[1] == 4 && b[3] <= 18 ? "PASS" : "FAIL") + " default: top left at 4,4, small: " + b[2] + "x" + b[3] + " GUI px");
                shot(mc, "hud-2-halfway-gs2");
            }
            case 132 -> gui(mc, 3);
            case 140 -> shot(mc, "hud-2-halfway-gs3");
            case 142 -> {
                // not the defaults: top right, the disc's icon, no backdrop, Glimmer teal text, a bit bigger
                gui(mc, 2);
                NowPlaying.ANCHOR.set(NowPlaying.Anchor.TOP_RIGHT);
                NowPlaying.X.set(-4);
                NowPlaying.Y.set(4);
                NowPlaying.SHOW_ICON.set(true);
                NowPlaying.BACKGROUND.set(false);
                NowPlaying.TEXT_COLOR.set("#7FF0D8");
                NowPlaying.TEXT_OPACITY.set(1.0);
                NowPlaying.SCALE.set(1.5);
            }
            case 150 -> {
                int gw = mc.getWindow().getGuiScaledWidth();
                int[] b = NowPlayingHud.box(gw, mc.getWindow().getGuiScaledHeight(), NowPlayingHud.track().orElseThrow());
                say((b[0] + b[2] == gw - 4 && b[1] == 4 ? "PASS" : "FAIL") + " options: moved to the top right, icon on, scale 1.5: "
                    + b[0] + "," + b[1] + " " + b[2] + "x" + b[3]);
                shot(mc, "hud-3-options-gs2");
            }
            case 152 -> {
                // only the bar, bottom centre above the hotbar
                NowPlaying.ANCHOR.set(NowPlaying.Anchor.BOTTOM_CENTER);
                NowPlaying.X.set(0);
                NowPlaying.Y.set(-60);
                NowPlaying.SHOW_ICON.set(false);
                NowPlaying.SHOW_TITLE.set(false);
                NowPlaying.SHOW_TIME.set(false);
                NowPlaying.SCALE.set(1.0);
                NowPlaying.TEXT_COLOR.set("#FFFFFF");
            }
            case 160 -> shot(mc, "hud-4-bar-only-gs2");
            case 162 -> {
                for (var v : java.util.List.of(NowPlaying.ANCHOR, NowPlaying.X, NowPlaying.Y, NowPlaying.SHOW_ICON, NowPlaying.SHOW_TITLE,
                    NowPlaying.SHOW_TIME, NowPlaying.SHOW_BAR, NowPlaying.BACKGROUND, NowPlaying.TEXT_COLOR, NowPlaying.TEXT_OPACITY, NowPlaying.SCALE)) {
                    reset(v);
                }
                HudToggle.hide(mc, true);
            }
            case 170 -> shot(mc, "hud-5-f1-hidden");
            case 180 -> {
                HudToggle.hide(mc, false);
                server.execute(() -> shelves(server));
            }
            case 181 -> {
                mc.player.setYRot(0.0F);
                mc.player.setXRot(20.0F);
            }
            case 240 -> shot(mc, "shelves");
            case 250 -> server.execute(() -> enchanting(server));
            case 251 -> {
                mc.player.setYRot(0.0F);
                mc.player.setXRot(12.0F);
            }
            case 330 -> shot(mc, "enchant-table");
            case 340 -> server.execute(() -> openTable(server));
            case 380 -> {
                say((mc.gui.screen() instanceof EnchantmentScreen ? "PASS" : "FAIL") + " the enchanting screen is open");
                if (mc.player.containerMenu instanceof EnchantmentMenu menu) {
                    say((menu.costs[2] == 30 ? "PASS" : "FAIL") + " 15 shelves give the top slot level " + menu.costs[2]
                        + " (" + menu.costs[0] + ", " + menu.costs[1] + ", " + menu.costs[2] + ")");
                }
                shot(mc, "enchant-screen");
            }
            case 400 -> {
                mc.gui.setScreen(null);
                say("DONE failed=" + failed);
                mc.stop();
            }
            default -> {
            }
        }
    }

    /** In front of the player: Record Shelf, then Chiseled Record Shelves empty, half (3) and full (6), fronts facing back. */
    private static void shelves(MinecraftServer server) {
        ServerPlayer p = player(server);
        p.setGameMode(GameType.CREATIVE);
        ServerLevel level = p.level();
        level.setBlockAndUpdate(box, Blocks.AIR.defaultBlockState());
        BlockPos base = feet.offset(-2, 0, 3);
        int[] fill = {-1, 0, 3, 6, -1};
        for (int i = 0; i < fill.length; i++) {
            for (int y = 0; y < 2; y++) {
                BlockPos at = base.offset(i, y, 0);
                if (fill[i] < 0) {
                    level.setBlockAndUpdate(at, RecordShelves.RECORD_SHELF.get().defaultBlockState());
                } else {
                    shelf(level, at, Direction.NORTH, fill[i]);
                }
            }
        }
        p.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
    }

    private static void shelf(ServerLevel level, BlockPos at, Direction facing, int discs) {
        BlockState s = RecordShelves.CHISELED_RECORD_SHELF.get().defaultBlockState().setValue(ChiseledRecordShelfBlock.FACING, facing);
        level.setBlockAndUpdate(at, s);
        if (level.getBlockEntity(at) instanceof ChiseledRecordShelfBlockEntity be) {
            ItemStack[] discsOf = {new ItemStack(Items.MUSIC_DISC_CAT), new ItemStack(Items.MUSIC_DISC_13), new ItemStack(Items.MUSIC_DISC_STAL),
                new ItemStack(com.ostentum.MoonDiscs.HARVEST_MOON.get()), new ItemStack(Items.MUSIC_DISC_PIGSTEP), new ItemStack(Items.MUSIC_DISC_OTHERSIDE)};
            for (int i = 0; i < discs; i++) {
                be.setItem(i, discsOf[i].copy());
            }
        }
    }

    /** An enchanting table 12 blocks off, ringed by 8 Record Shelves and 7 full Chiseled Record Shelves (15 in all). */
    private static void enchanting(MinecraftServer server) {
        ServerPlayer p = player(server);
        ServerLevel level = p.level();
        BlockPos table = feet.offset(0, 0, 12);
        level.setBlockAndUpdate(table, Blocks.ENCHANTING_TABLE.defaultBlockState());
        int n = 0;
        for (int dx = -2; dx <= 2 && n < 15; dx++) {
            for (int dz = -2; dz <= 2 && n < 15; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 2 || dz == -2 && dx == 0) {
                    continue;                                  // the ring only, with a gap on the player's side
                }
                for (int y = 0; y < 2 && n < 15; y++) {
                    BlockPos at = table.offset(dx, y, dz);
                    if (n % 2 == 0) {
                        Direction in = Math.abs(dx) == 2 ? (dx > 0 ? Direction.WEST : Direction.EAST) : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                        shelf(level, at, in, 6);
                    } else {
                        level.setBlockAndUpdate(at, RecordShelves.RECORD_SHELF.get().defaultBlockState());
                    }
                    n++;
                }
            }
        }
        say("placed " + n + " shelves round the table");
        p.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 6.5);
    }

    private static void openTable(MinecraftServer server) {
        ServerPlayer p = player(server);
        BlockPos table = feet.offset(0, 0, 12);
        p.experienceLevel = 30;
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new EnchantmentMenu(id, inv, ContainerLevelAccess.create(p.level(), table)),
            Component.translatable("container.enchant")));
        if (p.containerMenu instanceof EnchantmentMenu menu) {
            menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            menu.slotsChanged(menu.getSlot(0).container);
            say("server costs " + menu.costs[0] + ", " + menu.costs[1] + ", " + menu.costs[2]);
        } else {
            fail("no enchanting menu");
        }
    }

    private static void gui(Minecraft mc, int scale) {
        mc.options.guiScale().set(scale);
        mc.resizeGui();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void reset(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue v) {
        v.set(v.getDefault());
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "nowplaying-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
            msg -> say("saved nowplaying-" + name + ".png"));
    }

    private static void fail(String why) {
        say("FAIL " + why);
    }

    private static void say(String line) {
        if (line.startsWith("FAIL")) {
            failed++;
        }
        System.out.println("NOWPLAYING " + line);
    }

    private static void create(Minecraft mc) {
        say("creating a flat world");
        mc.options.renderDistance().set(6);
        mc.options.simulationDistance().set(5);
        String name = "nowplaying" + System.currentTimeMillis() / 1000;
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261106L, false, false),
            provider -> provider.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }
}
