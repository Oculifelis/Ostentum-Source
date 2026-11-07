package com.ostentum.music;

import com.ostentum.Ostentum;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

/**
 * The Record Pendant (1.9.4), a Curios necklace: while it is worn, a small HUD (client.NowPlayingHud) shows the song
 * from the nearest jukebox the player can hear, with its time and a thin progress bar. The song and its time come from
 * Fundamentum's client-side jukebox tracker, fed by the song start and stop events vanilla already sends: no block
 * scanning and no packets of Ostentum's own. How the HUD looks and where it sits is the client config
 * (ostentum-client.toml, section nowPlayingHud), in the spirit of Jade's: scale, anchor and offsets, background, text
 * colour and which parts show.
 * <p>
 * The pendant can be held back from a build: {@code gradlew build -Pnopendant} writes {@code pendant=false} into
 * META-INF/ostentum-build.properties and leaves the pendant's recipe, model, texture and Curios data out of the jar.
 * Then {@link #PENDANT} is null (no item, so no recipe and no creative tab entry) and the HUD shows for the nearest
 * playing jukebox instead ({@link #WITHOUT_PENDANT} defaults to on).
 */
public final class NowPlaying {
    public static final String SLOT = "necklace";

    /** False in a {@code -Pnopendant} build. */
    public static final boolean PENDANT_ON = buildFlag("pendant");

    @Nullable
    public static final DeferredItem<Item> PENDANT = PENDANT_ON
        ? com.ostentum.Props.simple(Ostentum.ITEMS, "record_pendant", p -> p.stacksTo(1)) : null;

    /** Where on screen the HUD is anchored; {@link #fx}, {@link #fy} are the fraction of the screen (and of the box). */
    public enum Anchor {
        TOP_LEFT(0, 0), TOP_CENTER(0.5F, 0), TOP_RIGHT(1, 0),
        MIDDLE_LEFT(0, 0.5F), MIDDLE_RIGHT(1, 0.5F),
        BOTTOM_LEFT(0, 1), BOTTOM_CENTER(0.5F, 1), BOTTOM_RIGHT(1, 1);

        public final float fx;
        public final float fy;

        Anchor(float fx, float fy) {
            this.fx = fx;
            this.fy = fy;
        }
    }

    private static final String S = "nowPlayingHud.";
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Show the Now Playing HUD (while the Record Pendant is worn, or always with withoutPendant).")
        .translation("ostentum.configuration.enabled")
        .define(S + "enabled", true);
    public static final ModConfigSpec.BooleanValue WITHOUT_PENDANT = BUILDER
        .comment("Show the HUD for the nearest jukebox you can hear even without the Record Pendant.")
        .translation("ostentum.configuration.withoutPendant")
        .define(S + "withoutPendant", !PENDANT_ON);
    public static final ModConfigSpec.EnumValue<Anchor> ANCHOR = BUILDER
        .comment("Which part of the screen the HUD is placed against.")
        .translation("ostentum.configuration.anchor")
        .defineEnum(S + "anchor", Anchor.TOP_LEFT);
    public static final ModConfigSpec.IntValue X = BUILDER
        .comment("Moves the HUD right (negative: left) from its anchor, in GUI pixels.")
        .translation("ostentum.configuration.x")
        .defineInRange(S + "x", 4, -4000, 4000);
    public static final ModConfigSpec.IntValue Y = BUILDER
        .comment("Moves the HUD down (negative: up) from its anchor, in GUI pixels.")
        .translation("ostentum.configuration.y")
        .defineInRange(S + "y", 4, -4000, 4000);
    public static final ModConfigSpec.DoubleValue SCALE = BUILDER
        .comment("Size of the HUD: 1 is the size of the game's own text.")
        .translation("ostentum.configuration.scale")
        .defineInRange(S + "scale", 1.0, 0.25, 4.0);
    public static final ModConfigSpec.BooleanValue SHOW_ICON = BUILDER
        .comment("Show the playing disc's icon.")
        .translation("ostentum.configuration.showIcon")
        .define(S + "showIcon", false);
    public static final ModConfigSpec.BooleanValue SHOW_TITLE = BUILDER
        .comment("Show the song's name.")
        .translation("ostentum.configuration.showTitle")
        .define(S + "showTitle", true);
    public static final ModConfigSpec.BooleanValue SHOW_TIME = BUILDER
        .comment("Show the time, \"1:12 / 3:05\".")
        .translation("ostentum.configuration.showTime")
        .define(S + "showTime", true);
    public static final ModConfigSpec.BooleanValue SHOW_BAR = BUILDER
        .comment("Show the progress bar.")
        .translation("ostentum.configuration.showBar")
        .define(S + "showBar", true);
    public static final ModConfigSpec.IntValue BAR_WIDTH = BUILDER
        .comment("Shortest the progress bar gets, in GUI pixels (it grows to the width of the text above it).")
        .translation("ostentum.configuration.barWidth")
        .defineInRange(S + "barWidth", 60, 10, 400);
    public static final ModConfigSpec.IntValue MAX_TITLE_WIDTH = BUILDER
        .comment("Longer song names are cut short with \"...\" at this width, in GUI pixels.")
        .translation("ostentum.configuration.maxTitleWidth")
        .defineInRange(S + "maxTitleWidth", 150, 30, 1000);
    public static final ModConfigSpec.BooleanValue BACKGROUND = BUILDER
        .comment("Draw a dark backdrop behind the HUD, as chat and subtitles have.")
        .translation("ostentum.configuration.background")
        .define(S + "background", true);
    public static final ModConfigSpec.DoubleValue BACKGROUND_OPACITY = BUILDER
        .comment("How dark the backdrop is: 0 clear, 1 solid black.")
        .translation("ostentum.configuration.backgroundOpacity")
        .defineInRange(S + "backgroundOpacity", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.ConfigValue<String> TEXT_COLOR = BUILDER
        .comment("Colour of the song name and the bar, as #RRGGBB. The time is drawn a little dimmer.")
        .translation("ostentum.configuration.textColor")
        .define(S + "textColor", "#FFFFFF", o -> o instanceof String s && s.matches("#?[0-9a-fA-F]{6}"));
    public static final ModConfigSpec.DoubleValue TEXT_OPACITY = BUILDER
        .comment("Opacity of the text and the bar: 0.1 faint, 1 solid.")
        .translation("ostentum.configuration.textOpacity")
        .defineInRange(S + "textOpacity", 0.9, 0.1, 1.0);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private NowPlaying() {
    }

    /** True for the Record Pendant (always false in a -Pnopendant build). */
    public static boolean isPendant(ItemStack stack) {
        return PENDANT != null && stack.is(PENDANT.get());
    }

    /** {@link #TEXT_COLOR} with {@link #TEXT_OPACITY} as ARGB (white if the setting can't be read). */
    public static int textArgb() {
        int rgb = 0xFFFFFF;
        try {
            rgb = Integer.parseInt(TEXT_COLOR.get().replace("#", ""), 16) & 0xFFFFFF;
        } catch (RuntimeException ignored) {
            // keeps white
        }
        int a = Math.max(26, Math.min(255, (int) Math.round(TEXT_OPACITY.get() * 255)));
        return a << 24 | rgb;
    }

    /** A flag the build writes into META-INF/ostentum-build.properties; true when it is missing. */
    private static boolean buildFlag(String key) {
        try (InputStream in = NowPlaying.class.getResourceAsStream("/META-INF/ostentum-build.properties")) {
            if (in == null) {
                return true;
            }
            Properties p = new Properties();
            p.load(in);
            return !"false".equalsIgnoreCase(p.getProperty(key, "true").trim());
        } catch (IOException e) {
            return true;
        }
    }

    /** Touches the pendant into the item register and registers the client config. */
    public static void init() {
        if (PENDANT != null) {
            PENDANT.getId();
        }
        ModList.get().getModContainerById(Ostentum.MODID)
            .ifPresent(c -> c.registerConfig(ModConfig.Type.CLIENT, SPEC, "ostentum-client.toml"));
    }
}
