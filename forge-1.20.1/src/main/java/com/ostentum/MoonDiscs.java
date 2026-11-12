package com.ostentum;

import com.fundamentum.moon.Moons;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.Rarity;
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

    public static final RegistryObject<Item> FUNDAMENTUM = disc("fundamentum", 4, OstentumSounds.DISC_FUNDAMENTUM, 123);
    public static final RegistryObject<Item> HARVEST_MOON = disc("harvest_moon", 9, OstentumSounds.DISC_HARVEST_MOON, 106);
    public static final RegistryObject<Item> ENCHANTED_MOON = disc("enchanted_moon", 12, OstentumSounds.DISC_ENCHANTED_MOON, 146);
    public static final RegistryObject<Item> GLIMMER_MOON = disc("glimmer_moon", 14, OstentumSounds.DISC_GLIMMER_MOON, 165);

    /** Fundamentum moon id to its disc. */
    private static final Map<String, RegistryObject<Item>> BY_MOON = Map.of(
        Harvest.MOON, HARVEST_MOON,
        Enchanted.MOON, ENCHANTED_MOON,
        Glimmer.MOON, GLIMMER_MOON);

    private MoonDiscs() {}

    /** 1.20.1 discs are {@link RecordItem}s: comparator output, sound and length (seconds) come from 1.21's jukebox_song data. */
    private static RegistryObject<Item> disc(String name, int comparator, Supplier<SoundEvent> sound, int seconds) {
        return Ostentum.ITEMS.register("music_disc_" + name,
            () -> new RecordItem(comparator, sound, new Item.Properties().stacksTo(1).rarity(Rarity.RARE), seconds * 20));
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
