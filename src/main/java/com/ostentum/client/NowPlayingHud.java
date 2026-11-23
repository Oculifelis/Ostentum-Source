package com.ostentum.client;

import com.fundamentum.curios.Slots;
import com.fundamentum.music.JukeboxProgress;
import com.fundamentum.music.JukeboxProgress.Track;
import com.fundamentum.music.client.JukeboxTracker;
import com.ostentum.Ostentum;
import com.ostentum.music.NowPlaying;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

/**
 * The Now Playing HUD: the song from the nearest jukebox the player can hear, as one line of the game's own font with
 * a drop shadow ("C418 - cat  1:12 / 3:05") over a two-pixel progress bar, on a faint backdrop like the subtitles'.
 * Small by default, in the top left corner; everything about it is the client config ({@link NowPlaying}): scale,
 * anchor and offsets, backdrop and its opacity, text colour and opacity, and which parts show (disc icon, name, time,
 * bar). Shown while the Record Pendant is worn (or always, with withoutPendant); hidden with F1 and when nothing plays.
 * <p>
 * Lag-free: whether the pendant is worn is looked up twice a second, not every frame; the song comes from Fundamentum's
 * {@link JukeboxTracker}, which only listens to the jukebox start and stop events the client gets anyway (no world
 * scan, no packets). Per frame it is one pass over the few jukeboxes playing, a string or two and two rectangles.
 */
@Mod.EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class NowPlayingHud {
    private static final int ICON = 16;
    /** Text row height: 7-pixel glyphs plus their shadow. */
    private static final int TEXT_H = 8;
    private static final int BAR_H = 2;
    private static final int GAP = 2;

    private static boolean worn;
    private static int tick;

    private NowPlayingHud() {
    }

    /** Mod bus half. Forge 1.20.1 has no built-in config screen, so there is no Config button: edit ostentum-client.toml. */
    @Mod.EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Layers {
        private Layers() {
        }

        @SubscribeEvent
        public static void layers(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "now_playing", (gui, g, partialTick, width, height) -> render(g));
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 10 != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        worn = NowPlaying.PENDANT != null && mc.player != null
            && Slots.allEquipped(mc.player, NowPlaying.SLOT).stream().anyMatch(NowPlaying::isPendant);
    }

    /** True while the pendant is worn (as of the last check). */
    public static boolean worn() {
        return worn;
    }

    /** True when the HUD has a reason to show: the pendant is worn, or withoutPendant is on. */
    public static boolean active() {
        return worn || NowPlaying.WITHOUT_PENDANT.get();
    }

    /** The track the HUD shows right now, or empty. */
    public static Optional<Track> track() {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null) {
            return Optional.empty();
        }
        return JukeboxTracker.nearest(mc.player.getEyePosition());
    }

    private static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !NowPlaying.ENABLED.get()) {
            return;
        }
        Optional<Track> track = track();
        if (track.isEmpty()) {
            return;
        }
        Track t = track.get();
        Layout l = layout(mc.font, t);
        if (l == null) {
            return;
        }
        int[] box = box(g.guiWidth(), g.guiHeight(), l);
        float s = NowPlaying.SCALE.get().floatValue();
        g.pose().pushPose();
        g.pose().translate(box[0], box[1], 0);
        g.pose().scale(s, s, 1);
        draw(g, mc.font, t, l);
        g.pose().popPose();
    }

    /** What the HUD holds and its size before scaling (GUI pixels). */
    record Layout(String title, int pad, int iconW, int contentW, int innerH, int w, int h) {
    }

    /** The layout for this track under the current config, or null when every part is switched off. */
    static Layout layout(Font font, Track t) {
        boolean title = NowPlaying.SHOW_TITLE.get();
        boolean time = NowPlaying.SHOW_TIME.get();
        boolean bar = NowPlaying.SHOW_BAR.get();
        boolean icon = NowPlaying.SHOW_ICON.get();
        if (!title && !time && !bar && !icon) {
            return null;
        }
        String name = title ? cut(font, t.name().getString(), NowPlaying.MAX_TITLE_WIDTH.get()) : "";
        int timeW = time ? font.width(JukeboxProgress.timeText(t.lengthSeconds(), t.lengthSeconds())) : 0;
        int textW = (title ? font.width(name) : 0) + (title && time ? 6 : 0) + timeW;
        int contentW = Math.max(textW, bar ? NowPlaying.BAR_WIDTH.get() : 0);
        int iconW = icon ? ICON + (contentW > 0 ? 3 : 0) : 0;
        int innerH = Math.max(icon ? ICON : 0, rows(title, time, bar));
        // the backdrop sits 3 pixels out on the left and top, 2 on the right and bottom (the text shadow fills the rest)
        int pad = NowPlaying.BACKGROUND.get() ? 3 : 0;
        int w = pad + iconW + contentW + Math.max(0, pad - 1);
        int h = pad + innerH + Math.max(0, pad - 1);
        return new Layout(name, pad, iconW, contentW, innerH, w, h);
    }

    private static int rows(boolean title, boolean time, boolean bar) {
        return (title || time ? TEXT_H : 0) + ((title || time) && bar ? GAP : 0) + (bar ? BAR_H : 0);
    }

    /** Where the HUD's top left goes on a screen this size, and its scaled size: {x, y, w, h} in GUI pixels. */
    static int[] box(int guiWidth, int guiHeight, Layout l) {
        float s = NowPlaying.SCALE.get().floatValue();
        int w = (int) Math.ceil(l.w() * s);
        int h = (int) Math.ceil(l.h() * s);
        NowPlaying.Anchor a = NowPlaying.ANCHOR.get();
        int x = Math.max(0, Math.min(guiWidth - w, Math.round(a.fx * (guiWidth - w)) + NowPlaying.X.get()));
        int y = Math.max(0, Math.min(guiHeight - h, Math.round(a.fy * (guiHeight - h)) + NowPlaying.Y.get()));
        return new int[] {x, y, w, h};
    }

    /** The HUD's box ({x, y, w, h}) for this track under the current config, or null when nothing would show. */
    public static int[] box(int guiWidth, int guiHeight, Track t) {
        Layout l = layout(Minecraft.getInstance().font, t);
        return l == null ? null : box(guiWidth, guiHeight, l);
    }

    private static void draw(GuiGraphics g, Font font, Track t, Layout l) {
        if (l.pad() > 0) {
            int alpha = (int) Math.round(NowPlaying.BACKGROUND_OPACITY.get() * 255);
            if (alpha > 0) {
                g.fill(0, 0, l.w(), l.h(), alpha << 24);
            }
        }
        int color = NowPlaying.textArgb();
        int dim = color & 0xFF000000 | ((color >> 16 & 0xFF) * 3 / 4) << 16 | ((color >> 8 & 0xFF) * 3 / 4) << 8 | (color & 0xFF) * 3 / 4;
        int x = l.pad();
        int y = l.pad();
        if (l.iconW() > 0) {
            g.renderItem(disc(t), x, y + (l.innerH() - ICON) / 2);
            x += l.iconW();
        }
        boolean title = NowPlaying.SHOW_TITLE.get();
        boolean time = NowPlaying.SHOW_TIME.get();
        boolean bar = NowPlaying.SHOW_BAR.get();
        y += (l.innerH() - rows(title, time, bar)) / 2;
        if (title) {
            g.drawString(font, l.title(), x, y, color, true);
        }
        if (time) {
            String clock = t.timeText();
            g.drawString(font, clock, x + l.contentW() - font.width(clock), y, title ? dim : color, true);
        }
        if (title || time) {
            y += TEXT_H + (bar ? GAP : 0);
        }
        if (bar && l.contentW() > 0) {
            g.fill(x, y, x + l.contentW(), y + BAR_H, ((color >>> 24) / 2) << 24);
            int filled = Math.round(Math.max(0F, Math.min(1F, t.progress())) * l.contentW());
            if (filled > 0) {
                g.fill(x, y, x + filled, y + BAR_H, color);
            }
        }
    }

    /** The song name, cut short with "..." to fit {@code max} pixels. */
    private static String cut(Font font, String name, int max) {
        if (font.width(name) <= max) {
            return name;
        }
        return font.plainSubstrByWidth(name, Math.max(0, max - font.width("..."))).stripTrailing() + "...";
    }

    /** The disc that is playing (on 1.20.1 the track holds the record item itself), or a jukebox. */
    static ItemStack disc(Track t) {
        return new ItemStack(t.record() == null ? Items.JUKEBOX : t.record());
    }
}
