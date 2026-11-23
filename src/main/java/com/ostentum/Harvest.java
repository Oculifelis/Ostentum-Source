package com.ostentum;

import com.fundamentum.moon.Moons;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** The Harvest Moon (fundamentum/moons/harvest.json): ripe crops broken under it drop twice. */
public final class Harvest {
    public static final String MOON = "ostentum:harvest";
    public static final TagKey<Block> CROPS = TagKey.create(Registries.BLOCK, Ostentum.id("harvest_crops"));

    private Harvest() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener(Harvest::drops);
    }

    private static void drops(BlockDropsEvent event) {
        BlockState state = event.getState();
        if (event.getDrops().isEmpty() || !state.is(CROPS) || !ripe(state) || !Moons.isUp(event.getLevel(), MOON)) {
            return;
        }
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemEntity copy = new ItemEntity(event.getLevel(), drop.getX(), drop.getY(), drop.getZ(), drop.getItem().copy());
            copy.setDefaultPickUpDelay();
            extra.add(copy);
        }
        event.getDrops().addAll(extra);
    }

    /** Fully grown: every "age" property at its top value (blocks with no age, like melons, are always ripe). */
    static boolean ripe(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                if (state.getValue(age) < max) {
                    return false;
                }
            }
        }
        return true;
    }
}
