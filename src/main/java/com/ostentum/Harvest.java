package com.ostentum;

import com.fundamentum.moon.Moons;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

/**
 * The Harvest Moon (fundamentum/moons/harvest.json): ripe crops broken under it drop twice. 1.20.1 has no block drops
 * event, so the second set is rolled from the crop's loot table on break and popped beside the first.
 */
public final class Harvest {
    public static final String MOON = "ostentum:harvest";
    public static final TagKey<Block> CROPS = TagKey.create(Registries.BLOCK, Ostentum.id("harvest_crops"));

    private Harvest() {}

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(Harvest::drops);
    }

    private static void drops(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getPlayer().isCreative()) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), event.getPlayer(), event.getPlayer().getMainHandItem());
        for (ItemStack stack : extra(level, state, drops)) {
            Block.popResource(level, pos, stack);
        }
    }

    /** The second set of drops a crop in {@code state} gets: copies of {@code drops} when ripe under the moon, else none. */
    static List<ItemStack> extra(ServerLevel level, BlockState state, List<ItemStack> drops) {
        List<ItemStack> extra = new ArrayList<>();
        if (drops.isEmpty() || !state.is(CROPS) || !ripe(state) || !Moons.isUp(level, MOON)) {
            return extra;
        }
        for (ItemStack drop : drops) {
            extra.add(drop.copy());
        }
        return extra;
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
