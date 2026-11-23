package com.ostentum.whip.more;

import com.fundamentum.whip.WhipDefinition;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Ostentum's second set of whips (1.8.0), one class each so any of them can move to another mod whole: its class, its
 * {@code assets/ostentum} item, model and texture files (named after its id), its recipe or loot, and its lang lines.
 * Each class holds its item and its {@link WhipDefinition}; effects are first passes and expected to change.
 */
public final class MoreWhips {
    public record Entry(DeferredItem<Item> item, Supplier<WhipDefinition> definition) {}

    public static final List<Entry> ALL = List.of(
        new Entry(LeatherWhip.ITEM, LeatherWhip::definition),
        new Entry(ChainWhip.ITEM, ChainWhip::definition),
        new Entry(GoldenHeavyWhip.ITEM, GoldenHeavyWhip::definition),
        new Entry(DiamondWhip.ITEM, DiamondWhip::definition),
        new Entry(EmeraldWhip.ITEM, EmeraldWhip::definition),
        new Entry(AmethystWhip.ITEM, AmethystWhip::definition),
        new Entry(RoyalSlimeWhip.ITEM, RoyalSlimeWhip::definition),
        new Entry(DestructionWhip.ITEM, DestructionWhip::definition),
        new Entry(ThunderstormWhip.ITEM, ThunderstormWhip::definition),
        new Entry(UntamedSeaWhip.ITEM, UntamedSeaWhip::definition),
        new Entry(BreezeWhip.ITEM, BreezeWhip::definition),
        new Entry(BlazeWhip.ITEM, BlazeWhip::definition));

    public static void init() {
        for (Entry e : ALL) {
            com.ostentum.whip.OstentumWhips.register(e.item().getId().getPath(), e.definition().get());
        }
        ThunderstormWhip.init();
    }

    private MoreWhips() {}
}
