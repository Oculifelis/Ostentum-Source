package com.ostentum;

import com.fundamentum.moon.Moons;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** The Glimmer Moon: on about one full moon in six, mobs and ores drop twice their experience. */
final class Glimmer {
    static final String MOON = "ostentum:glimmer";

    private Glimmer() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((LivingExperienceDropEvent e) -> {
            if (e.getEntity().level() instanceof ServerLevel level && Moons.isUp(level, MOON)) {
                e.setDroppedExperience(e.getDroppedExperience() * 2);
            }
        });
        NeoForge.EVENT_BUS.addListener((BlockDropsEvent e) -> {
            if (e.getDroppedExperience() > 0 && Moons.isUp(e.getLevel(), MOON)) {
                e.setDroppedExperience(e.getDroppedExperience() * 2);
            }
        });
    }
}
