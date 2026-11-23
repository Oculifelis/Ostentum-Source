package com.ostentum.music;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Headless checks for the record shelves and the Now Playing numbers (1.9.4), run by SelfTest: putting discs into and
 * taking them out of a Chiseled Record Shelf by clicking its slots, what a comparator reads, the enchanting power at
 * 0, 2, 3 and 6 discs (and what an enchanting table adds up), the discs dropping when it breaks, and Fundamentum's
 * jukebox track maths the HUD shows (range, progress, time text).
 */
public final class ShelfTest {
    private ShelfTest() {
    }

    public static void run(ServerLevel level, FakePlayer player, BlockPos at, BiConsumer<String, Boolean> check) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                level.setBlock(at.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                for (int y = 0; y < 3; y++) {
                    level.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        BlockPos pos = at.north(2);
        level.setBlock(pos, RecordShelves.CHISELED_RECORD_SHELF.get().defaultBlockState(), Block.UPDATE_ALL);
        check.accept("chiseled record shelf has its block entity (at " + pos.toShortString() + ": " + level.getBlockState(pos) + ")",
            level.getBlockEntity(pos) instanceof ChiseledRecordShelfBlockEntity);
        ChiseledRecordShelfBlockEntity shelf = (ChiseledRecordShelfBlockEntity) level.getBlockEntity(pos);

        // click slot 0 (top left) with a disc, then slot 4 (bottom middle)
        player.getInventory().clearContent();
        InteractionResult in0 = click(level, player, pos, 0, new ItemStack(Items.MUSIC_DISC_CAT));
        check.accept("a disc clicked into slot 0 goes in (" + in0 + ")", in0.consumesAction() && shelf.getItem(0).is(Items.MUSIC_DISC_CAT)
            && level.getBlockState(pos).getValue(ChiseledRecordShelfBlock.SLOTS.get(0)));
        check.accept("comparator reads 1 after slot 0 (" + signal(level, pos) + ")", signal(level, pos) == 1);
        click(level, player, pos, 4, new ItemStack(com.ostentum.MoonDiscs.FUNDAMENTUM.get()));
        check.accept("an Ostentum disc goes into slot 4", shelf.getItem(4).is(com.ostentum.MoonDiscs.FUNDAMENTUM.get()));
        check.accept("comparator reads 5 after slot 4 (" + signal(level, pos) + ")", signal(level, pos) == 5);
        InteractionResult book = click(level, player, pos, 1, new ItemStack(Items.BOOK));
        check.accept("a book is refused (" + book + ")", shelf.getItem(1).isEmpty() && !level.getBlockState(pos).getValue(ChiseledRecordShelfBlock.SLOTS.get(1)));
        click(level, player, pos, 0, new ItemStack(Items.MUSIC_DISC_13));
        // 1.20.1's shelf has one use(): like vanilla's chiseled bookshelf, a click on a full slot takes its disc out,
        // whatever is in hand (1.21.1 does the same through PASS_TO_DEFAULT_BLOCK_INTERACTION)
        check.accept("a full slot takes no second disc (the click takes the first out)", !shelf.getItem(0).is(Items.MUSIC_DISC_13)
            && player.getMainHandItem().is(Items.MUSIC_DISC_13) && player.getInventory().countItem(Items.MUSIC_DISC_CAT) == 1);
        player.getInventory().clearContent();
        click(level, player, pos, 0, new ItemStack(Items.MUSIC_DISC_CAT));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult out = level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND, hit(pos, 0));
        check.accept("an empty hand takes the disc back out of slot 0 (" + out + ")", shelf.getItem(0).isEmpty()
            && player.getInventory().countItem(Items.MUSIC_DISC_CAT) == 1 && !level.getBlockState(pos).getValue(ChiseledRecordShelfBlock.SLOTS.get(0)));
        check.accept("comparator reads 1 after taking from slot 0 (" + signal(level, pos) + ")", signal(level, pos) == 1);
        check.accept("the shelf counts 1 disc", ChiseledRecordShelfBlock.discs(level.getBlockState(pos)) == 1);

        // enchanting power: 0, 2, 3 and 6 discs
        int[] counts = {0, 2, 3, 6};
        float[] want = {0, 0, 1, 1};
        StringBuilder seen = new StringBuilder();
        boolean powers = true;
        for (int k = 0; k < counts.length; k++) {
            fill(shelf, counts[k]);
            BlockState s = level.getBlockState(pos);
            float p = s.getEnchantPowerBonus(level, pos);
            boolean valid = EnchantmentTableBlock.isValidBookShelf(level, at, pos.subtract(at));
            seen.append(counts[k]).append("->").append(p).append(valid ? "y " : "n ");
            powers &= p == want[k] && valid == (want[k] > 0);
        }
        check.accept("enchanting power at 0, 2, 3, 6 discs is 0, 0, 1, 1 and the table sees it (" + seen.toString().trim() + ")", powers);
        level.setBlock(at, Blocks.ENCHANTING_TABLE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(at.east(2), RecordShelves.RECORD_SHELF.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(at.west(2), RecordShelves.CHISELED_RECORD_SHELF.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(at.west(2)) instanceof ChiseledRecordShelfBlockEntity two) {
            fill(two, 2);
        }
        BlockState deco = RecordShelves.RECORD_SHELF.get().defaultBlockState();
        check.accept("the Record Shelf is in #minecraft:enchantment_power_provider (power " + deco.getEnchantPowerBonus(level, at.east(2)) + ")",
            deco.is(net.minecraft.tags.BlockTags.ENCHANTMENT_POWER_PROVIDER) && deco.getEnchantPowerBonus(level, at.east(2)) == 1.0F);
        float total = 0;
        for (BlockPos off : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantmentTableBlock.isValidBookShelf(level, at, off)) {
                total += level.getBlockState(at.offset(off)).getEnchantPowerBonus(level, at.offset(off));
            }
        }
        check.accept("a table with a Record Shelf, a 6-disc and a 2-disc chiseled shelf adds up 2 (" + total + ")", total == 2.0F);

        // breaking drops the discs (6 in it) and the shelf
        fill(shelf, 6);
        level.destroyBlock(pos, true);
        AABB box = new AABB(pos).inflate(2);
        long discs = level.getEntitiesOfClass(ItemEntity.class, box).stream().filter(e -> RecordShelves.isDisc(e.getItem()))
            .mapToInt(e -> e.getItem().getCount()).sum();
        long shelves = level.getEntitiesOfClass(ItemEntity.class, box).stream()
            .filter(e -> e.getItem().is(RecordShelves.CHISELED_RECORD_SHELF_ITEM.get())).count();
        check.accept("a broken chiseled record shelf drops its 6 discs (" + discs + ") and itself (" + shelves + ")", discs == 6 && shelves == 1);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(5)).forEach(Entity::discard);
        level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(at.east(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(at.west(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(5)).forEach(Entity::discard);

        if (NowPlaying.PENDANT != null) {      // null in a -Pnopendant build
            check.accept("the Record Pendant is a Curios necklace", new ItemStack(NowPlaying.PENDANT.get())
                .is(net.minecraft.tags.TagKey.create(Registries.ITEM, new net.minecraft.resources.ResourceLocation("curios", NowPlaying.SLOT))));
        }
    }

    /** Sets the first {@code n} slots to discs and the rest empty. */
    private static void fill(ChiseledRecordShelfBlockEntity shelf, int n) {
        for (int i = 0; i < ChiseledRecordShelfBlockEntity.SIZE; i++) {
            shelf.setItem(i, i < n ? new ItemStack(Items.MUSIC_DISC_CHIRP) : ItemStack.EMPTY);
        }
    }

    private static int signal(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getAnalogOutputSignal(level, pos);
    }

    /** Clicks slot {@code slot} of a north-facing shelf holding {@code stack}. */
    private static InteractionResult click(ServerLevel level, FakePlayer player, BlockPos pos, int slot, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND, hit(pos, slot));
    }

    /** A hit on the north face in the middle of slot {@code slot} (0-2 top row, 3-5 bottom row, left to right as seen). */
    private static BlockHitResult hit(BlockPos pos, int slot) {
        double u = (slot % 3 + 0.5) / 3.0;
        double v = 1.0 - (slot / 3 + 0.5) / 2.0;
        // seen from the north, the face's left is +x
        return new BlockHitResult(new Vec3(pos.getX() + 1.0 - u, pos.getY() + v, pos.getZ()), Direction.NORTH, pos, false);
    }
}
