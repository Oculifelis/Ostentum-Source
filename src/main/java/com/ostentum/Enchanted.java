package com.ostentum;

import com.fundamentum.moon.Moons;
import net.minecraft.world.level.Level;

/** The Enchanted Moon (fundamentum/moons/enchanted.json): enchanting under it costs no levels and needs none. */
public final class Enchanted {
    public static final String MOON = "ostentum:enchanted";

    private Enchanted() {}

    /** Whether the Enchanted Moon is up over this level, on either side. */
    public static boolean up(Level level) {
        return level.isClientSide() ? com.ostentum.client.ClientMoon.enchanted() : Moons.isUp(level, MOON);
    }
}
