package com.ostentum.client;

import com.fundamentum.client.Tooltips;
import com.ostentum.Ostentum;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientTooltipFlag;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Dev client only (gradlew runClient -Ptooltipshot): Fundamentum 1.25.3's folded tooltips on Ostentum's items. In a fresh
 * flat creative world, off screen and muted, it builds each item's tooltip three ways (Shift up, Shift down through the
 * tooltip flag, and as the search tree does with no player), checks the "Hold [Shift] for details" line is there exactly
 * when it should be, and screenshots each tooltip with and without Shift to run/screenshots/tooltipshot-*.png. Logs
 * "TOOLTIPSHOT PASS|FAIL" lines and "TOOLTIPSHOT DONE failed=N", then closes the game.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class TooltipShot {
    static final boolean ON = Boolean.getBoolean("ostentum.tooltipshot");

    /** Item, and whether its Fundamentum lines should fold. */
    private record Case(String item, boolean folds) {}

    private static final List<Case> CASES = List.of(
        new Case("ostentum:blaze_whip", true),
        new Case("ostentum:netherite_hook", true),
        new Case("ostentum:duelists_saber", true),
        new Case("ostentum:phantom_veil", true),
        new Case("ostentum:resonant_hilt", false),
        new Case("ostentum:rusted_delvers_head", false),
        new Case("minecraft:diamond_sword", false));

    private static boolean hidden;
    private static boolean started;
    private static int ticks;
    private static int index;
    private static int failed;
    static List<Component> showing = List.of();
    static ItemStack showingStack = ItemStack.EMPTY;

    private TooltipShot() {}

    private static void say(String s) {
        System.out.println("TOOLTIPSHOT " + s);
        if (s.startsWith("FAIL")) {
            failed++;
        }
    }

    /** Draws one tooltip near the top left, as a hovered slot would. */
    static final class Board extends Screen {
        Board() {
            super(Component.literal("tooltips"));
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            super.extractRenderState(graphics, mouseX, mouseY, a);
            graphics.item(showingStack, 12, 12);
            graphics.setTooltipForNextFrame(font, showing, Optional.empty(), showingStack, 24, 26);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private static List<Component> lines(Minecraft mc, ItemStack stack, boolean shift, boolean player) {
        TooltipFlag flag = new ClientTooltipFlag(false, false, shift, false, false);
        return stack.getTooltipLines(Item.TooltipContext.of(mc.level), player ? mc.player : null, flag);
    }

    private static boolean hinted(List<Component> lines) {
        String hint = Tooltips.holdShift().getString();
        return lines.stream().anyMatch(c -> c.getString().equals(hint));
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
            mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        }
        if (mc.level == null || mc.player == null) {
            if (!started && mc.gui.screen() instanceof TitleScreen) {
                started = true;
                create(mc);
            }
            return;
        }
        ticks++;
        if (ticks < 100) {
            return;
        }
        if (index >= CASES.size() * 2) {
            if (ticks == 120) {
                say("DONE failed=" + failed);
                mc.stop();
            }
            return;
        }
        Case c = CASES.get(index / 2);
        boolean shift = index % 2 == 1;
        if (ticks == 100) {
            Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(c.item)).orElse(null);
            if (item == null) {
                say("FAIL no item " + c.item);
                index += 2;
                ticks = 99;
                return;
            }
            ItemStack stack = new ItemStack(item);
            List<Component> up = lines(mc, stack, false, true);
            List<Component> down = lines(mc, stack, true, true);
            List<Component> search = lines(mc, stack, false, false);
            if (!shift) {
                boolean ok = hinted(up) == c.folds && !hinted(down) && !hinted(search) && search.size() == down.size();
                say((ok ? "PASS " : "FAIL ") + c.item + ": " + up.size() + " lines without Shift (hint " + hinted(up) + "), "
                    + down.size() + " with Shift, " + search.size() + " for search");
            }
            showingStack = stack;
            showing = shift ? down : up;
            if (!(mc.gui.screen() instanceof Board)) {
                mc.gui.setScreen(new Board());
            }
        } else if (ticks == 110) {
            String name = "tooltipshot-" + c.item.replace(':', '-') + (shift ? "-shift" : "") + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, msg -> say("saved " + name));
        } else if (ticks == 115) {
            index++;
            ticks = 99;
        }
    }

    private static void create(Minecraft mc) {
        say("creating a flat world");
        mc.options.pauseOnLostFocus = false;
        mc.options.guiScale().set(2);
        String name = "tooltipshot" + System.currentTimeMillis() / 1000;
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261106L, false, false),
            provider -> provider.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
            new TitleScreen());
    }
}
