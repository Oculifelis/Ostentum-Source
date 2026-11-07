package com.ostentum.music;

import com.ostentum.Ostentum;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Record shelves (1.9.4): the Record Shelf, a bookshelf full of record sleeves (in
 * {@code #minecraft:enchantment_power_provider}, so it powers an enchanting table like a bookshelf), and the Chiseled
 * Record Shelf, a chiseled bookshelf for music discs: six slots, one disc each, that powers an enchanting table only
 * while it holds at least {@link ChiseledRecordShelfBlock#DISCS_FOR_POWER} discs.
 */
public final class RecordShelves {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Ostentum.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Ostentum.MODID);

    public static final DeferredBlock<Block> RECORD_SHELF = BLOCKS.registerSimpleBlock("record_shelf",
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(1.5F).sound(SoundType.WOOD).ignitedByLava());
    public static final DeferredBlock<ChiseledRecordShelfBlock> CHISELED_RECORD_SHELF = BLOCKS.registerBlock("chiseled_record_shelf",
        ChiseledRecordShelfBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(1.5F).sound(SoundType.CHISELED_BOOKSHELF).ignitedByLava());

    public static final DeferredItem<BlockItem> RECORD_SHELF_ITEM = Ostentum.ITEMS.registerSimpleBlockItem("record_shelf", RECORD_SHELF);
    public static final DeferredItem<BlockItem> CHISELED_RECORD_SHELF_ITEM = Ostentum.ITEMS.registerSimpleBlockItem("chiseled_record_shelf",
        CHISELED_RECORD_SHELF);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChiseledRecordShelfBlockEntity>> CHISELED_RECORD_SHELF_BE =
        BLOCK_ENTITIES.register("chiseled_record_shelf", () -> BlockEntityType.Builder.of(ChiseledRecordShelfBlockEntity::new, CHISELED_RECORD_SHELF.get()).build(null));

    private RecordShelves() {
    }

    /** A music disc: anything a jukebox plays, or anything tagged {@code #c:music_discs}. */
    public static boolean isDisc(ItemStack stack) {
        return !stack.isEmpty() && (stack.has(DataComponents.JUKEBOX_PLAYABLE) || stack.is(Tags.Items.MUSIC_DISCS));
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
