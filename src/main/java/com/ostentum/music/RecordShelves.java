package com.ostentum.music;

import com.ostentum.Ostentum;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Record shelves (1.9.4): the Record Shelf, a bookshelf full of record sleeves (in
 * {@code #minecraft:enchantment_power_provider}, so it powers an enchanting table like a bookshelf), and the Chiseled
 * Record Shelf, a chiseled bookshelf for music discs: six slots, one disc each, that powers an enchanting table only
 * while it holds at least {@link ChiseledRecordShelfBlock#DISCS_FOR_POWER} discs.
 */
public final class RecordShelves {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Ostentum.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Ostentum.MODID);

    public static final RegistryObject<Block> RECORD_SHELF = BLOCKS.register("record_shelf", () -> new Block(
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(1.5F).sound(SoundType.WOOD).ignitedByLava()));
    public static final RegistryObject<ChiseledRecordShelfBlock> CHISELED_RECORD_SHELF = BLOCKS.register("chiseled_record_shelf",
        () -> new ChiseledRecordShelfBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(1.5F).sound(SoundType.CHISELED_BOOKSHELF).ignitedByLava()));

    public static final RegistryObject<BlockItem> RECORD_SHELF_ITEM = Ostentum.ITEMS.register("record_shelf",
        () -> new BlockItem(RECORD_SHELF.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> CHISELED_RECORD_SHELF_ITEM = Ostentum.ITEMS.register("chiseled_record_shelf",
        () -> new BlockItem(CHISELED_RECORD_SHELF.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<ChiseledRecordShelfBlockEntity>> CHISELED_RECORD_SHELF_BE =
        BLOCK_ENTITIES.register("chiseled_record_shelf", () -> BlockEntityType.Builder.of(ChiseledRecordShelfBlockEntity::new, CHISELED_RECORD_SHELF.get()).build(null));

    /** {@code #c:music_discs}, the common tag later versions use; 1.20.1 also has {@code #minecraft:music_discs}. */
    private static final TagKey<Item> C_MUSIC_DISCS = ItemTags.create(new ResourceLocation("c", "music_discs"));

    private RecordShelves() {
    }

    /** A music disc: anything a jukebox plays, or anything tagged {@code #c:music_discs}. */
    public static boolean isDisc(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof RecordItem || stack.is(ItemTags.MUSIC_DISCS) || stack.is(C_MUSIC_DISCS));
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
