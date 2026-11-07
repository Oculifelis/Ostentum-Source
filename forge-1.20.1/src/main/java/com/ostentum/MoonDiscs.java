package com.ostentum;

import com.ostentum.Props;
import com.fundamentum.moon.Moons;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Four music discs. Each moon's disc is a rare drop from hostile mobs a player kills under that moon; the Fundamentum
 * disc comes from the Pillager Siege's captain ({@link Siege}).
 */
public final class MoonDiscs {
    /** Chance per hostile mob killed by a player while the disc's moon is up. */
    static final float MOON_CHANCE = 0.02F;
    /** Chance the siege captain drops the Fundamentum disc when a player kills it. */
    static final float CAPTAIN_CHANCE = 0.25F;

    public static final RegistryObject<Item> FUNDAMENTUM = disc("fundamentum");
    public static final RegistryObject<Item> HARVEST_MOON = disc("harvest_moon");
    public static final RegistryObject<Item> ENCHANTED_MOON = disc("enchanted_moon");
    public static final RegistryObject<Item> GLIMMER_MOON = disc("glimmer_moon");

    /** Fundamentum moon id to its disc. */
    private static final Map<String, RegistryObject<Item>> BY_MOON = Map.of(
        Harvest.MOON, HARVEST_MOON,
        Enchanted.MOON, ENCHANTED_MOON,
        Glimmer.MOON, GLIMMER_MOON);

    private MoonDiscs() {}

    private static RegistryObject<Item> disc(String name) {
        ResourceKey<JukeboxSong> song = ResourceKey.create(Registries.JUKEBOX_SONG, Ostentum.id(name));
        return Props.simple(Ostentum.ITEMS, "music_disc_" + name, p -> p.stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(song));
    }

    /** Listens for the drops. */
    static void init() {
        MinecraftForge.EVENT_BUS.addListener(MoonDiscs::drops);
    }

    private static void drops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        RegistryObject<Item> disc = null;
        float chance = 0.0F;
        if (event.getEntity().getTags().contains(Siege.CAPTAIN_TAG)) {
            disc = FUNDAMENTUM;
            chance = CAPTAIN_CHANCE;
        } else if (event.getEntity() instanceof Enemy) {
            for (Map.Entry<String, RegistryObject<Item>> e : BY_MOON.entrySet()) {
                if (Moons.isUp(level, e.getKey())) {
                    disc = e.getValue();
                    chance = MOON_CHANCE;
                    break;
                }
            }
        }
        if (disc == null || level.getRandom().nextFloat() >= chance) {
            return;
        }
        var dead = event.getEntity();
        ItemEntity drop = new ItemEntity(level, dead.getX(), dead.getY(), dead.getZ(), new ItemStack(disc.get()));
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }
}
