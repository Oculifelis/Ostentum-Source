package com.ostentum.weapon;

import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * The weapons' headless checks, run by the dev quick test ({@code SelfTest}, phase two, at night). Each weapon's own
 * rule is driven through the same public method its event uses, on a small stone floor built in the sky.
 */
public final class WeaponTest {
    private WeaponTest() {}

    public static void run(ServerLevel level, FakePlayer player, BlockPos centre, BiConsumer<String, Boolean> check,
        BiConsumer<String, String> skip) {
        floor(level, centre, 6);
        BlockPos at = centre.above();
        Language lang = Language.getInstance();
        long named = weaponItems().stream().filter(id -> lang.has("item.ostentum." + id) && lang.has("item.ostentum." + id + ".desc")).count();
        check.accept("all " + weaponItems().size() + " weapon items have a name and a tooltip (" + named + ")", named == weaponItems().size());

        // 1.9.4: the Voidstring Bow (players, game rule, immune tag, kill teleport) and the Duelist's Saber guard
        BowsTest.run(level, player, at, check, skip);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        // Delver's Pick: the 1x3 line
        BlockPos p = new BlockPos(0, 64, 0);
        check.accept("delver's pick: a wall mines up and down, a floor along the facing",
            DelversPick.line(p, Direction.NORTH, Direction.SOUTH).equals(List.of(p.above(), p.below()))
                && DelversPick.line(p, Direction.UP, Direction.EAST).equals(List.of(p.east(), p.west())));
        check.accept("a dormant pick wakes through a Nether portal (portal_crafting recipe loaded)", level.getServer().getRecipeManager().getRecipes()
            .stream().anyMatch(r -> r.id().equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ostentum", "delvers_pick_awakening"))
                && net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(r.value().getType()).toString().equals("fundamentum:portal_crafting")));

        // Hearthblade: kills cook drops and store an ember; a blade off a lit soul campfire turns soul
        check.accept("hearthblade cooks beef", Hearthblade.cooked(level, new ItemStack(Items.BEEF, 2)).is(Items.COOKED_BEEF)
            && Hearthblade.cooked(level, new ItemStack(Items.BEEF, 2)).getCount() == 2);
        ItemStack blade = new ItemStack(Hearthblade.HEARTHBLADE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        Mob cow = mob(level, EntityType.COW, Vec3.atBottomCenterOf(at).add(2, 0, 0));
        cow.hurt(level.damageSources().playerAttack(player), 1000.0F);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4));
        boolean cooked = drops.stream().anyMatch(d -> d.getItem().is(Items.COOKED_BEEF)) && drops.stream().noneMatch(d -> d.getItem().is(Items.BEEF));
        check.accept("a hearthblade kill drops cooked beef and stores an ember (" + Hearthblade.embers(blade) + ")", cooked && Hearthblade.embers(blade) == 1);
        drops.forEach(Entity::discard);
        level.setBlock(at.east(3), Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), Block.UPDATE_ALL);
        ItemEntity popped = new ItemEntity(level, at.east(3).getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, new ItemStack(Hearthblade.HEARTHBLADE.get()));
        level.addFreshEntity(popped);
        check.accept("a hearthblade off a lit soul campfire comes out soul", popped.getItem().is(Hearthblade.SOUL.get()));
        popped.discard();
        level.setBlock(at.east(3), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        // Echo Blade: the echo hurts again
        Mob dummy = mob(level, EntityType.ZOMBIE, Vec3.atBottomCenterOf(at).add(-2, 0, 0));
        float before = dummy.getHealth();
        EchoBlade.echo(level, player, dummy, 3.0F);
        check.accept("echo blade's echo lands (" + before + " -> " + dummy.getHealth() + ")", dummy.getHealth() < before);

        // Abyssal Trident: the field drains air on land and drowns at the end; water breathers are spared
        Mob pig = mob(level, EntityType.PIG, Vec3.atBottomCenterOf(at).add(2, 0, 2));
        int air = pig.getAirSupply();
        AbyssalTrident.drown(level, pig);
        check.accept("abyssal field takes air on land (" + air + " -> " + pig.getAirSupply() + ")", pig.getAirSupply() == air - AbyssalTrident.DRAIN);
        pig.setAirSupply(-15);
        float pigHealth = pig.getHealth();
        pig.invulnerableTime = 0;
        AbyssalTrident.drown(level, pig);
        check.accept("and drowns once the air is gone", pig.getHealth() < pigHealth && pig.getAirSupply() == 0);
        Mob drowned = mob(level, EntityType.DROWNED, Vec3.atBottomCenterOf(at).add(-2, 0, 2));
        int drownedAir = drowned.getAirSupply();
        AbyssalTrident.drown(level, drowned);
        check.accept("a drowned keeps its air", drowned.getAirSupply() == drownedAir);
        pig.discard();
        drowned.discard();

        // Quake Hammer: a heave under a mob hurts it and throws it up
        Mob target = mob(level, EntityType.ZOMBIE, Vec3.atBottomCenterOf(at).add(0, 0, 3));
        float zombie = target.getHealth();
        QuakeHammer.heave(level, player, centre.offset(0, 0, 3), new java.util.HashSet<>(), 1);
        check.accept("quake heave hurts and launches (" + zombie + " -> " + target.getHealth() + ", dy " + target.getDeltaMovement().y + ")",
            target.getHealth() < zombie && target.getDeltaMovement().y > 0.3);
        target.discard();
        dummy.discard();

        // Weathervane Spear: a jab presses a button
        level.setBlock(at.west(4), Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR),
            Block.UPDATE_ALL);
        boolean powered = WeathervaneSpear.power(level, at.west(4), player);
        check.accept("weathervane jab presses a button", powered && level.getBlockState(at.west(4)).getValue(ButtonBlock.POWERED));
        level.setBlock(at.west(4), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        // Bastion Crossbow: the flame bolt lights a campfire and a candle; a soul patch is placed
        level.setBlock(at.north(4), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
        level.setBlock(at.north(4).east(), Blocks.CANDLE.defaultBlockState(), Block.UPDATE_ALL);
        boolean lit = BastionCrossbow.light(level, at.north(4), player) && BastionCrossbow.light(level, at.north(4).east(), player);
        check.accept("bastion flame bolt lights a campfire and a candle", lit && level.getBlockState(at.north(4)).getValue(CampfireBlock.LIT)
            && level.getBlockState(at.north(4).east()).getValue(BlockStateProperties.LIT));
        level.setBlock(at.north(4), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(at.north(4).east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        var patch = BastionCrossbow.soulPatch(level, Vec3.atBottomCenterOf(at), player);
        check.accept("bastion bolt leaves a soul patch", patch.isAlive());
        patch.discard();

        // Bloom Bow: a landing bonemeals the patch
        level.setBlock(centre.offset(-4, 0, -4), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        check.accept("bloom arrow bonemeals grass", BloomBow.bloom(level, centre.offset(-4, 0, -4)) > 0);

        // Frostbite Hatchet: still water under its path freezes
        level.setBlock(centre.offset(5, 0, 0), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        FrostbiteHatchet.freezeWater(level, centre.offset(5, 1, 0));
        check.accept("frostbite hatchet freezes water", level.getBlockState(centre.offset(5, 0, 0)).is(Blocks.FROSTED_ICE));

        // returning weapons go back to the slot they left (1.9.4), else the first free hotbar slot, else the inventory
        var inv = player.getInventory();
        inv.clearContent();
        boolean home = ReturnSlot.give(player, new ItemStack(FrostbiteHatchet.HATCHET.get()), 3);
        check.accept("a returning weapon goes back to its own slot (3)", home && inv.getItem(3).is(FrostbiteHatchet.HATCHET.get()));
        inv.clearContent();
        inv.setItem(0, new ItemStack(Items.DIRT));
        inv.setItem(3, new ItemStack(Items.DIRT));
        ReturnSlot.give(player, new ItemStack(FrostbiteHatchet.HATCHET.get()), 3);
        check.accept("its slot taken, it goes to the first free hotbar slot (1)", inv.getItem(1).is(FrostbiteHatchet.HATCHET.get()));
        inv.clearContent();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, new ItemStack(Items.DIRT));
        }
        ReturnSlot.give(player, new ItemStack(FrostbiteHatchet.HATCHET.get()), 3);
        check.accept("a full hotbar sends it into the inventory (9)", inv.getItem(9).is(FrostbiteHatchet.HATCHET.get()));
        inv.clearContent();
        inv.setItem(5, new ItemStack(FrostbiteHatchet.HATCHET.get()));
        inv.selected = 5;
        FrostbiteHatchet.HATCHET.get().use(level, player, InteractionHand.MAIN_HAND);
        var hatchets = level.getEntitiesOfClass(FrostbiteHatchet.Thrown.class, player.getBoundingBox().inflate(4));
        FrostbiteHatchet.Thrown axe = hatchets.isEmpty() ? null : hatchets.get(0);
        check.accept("a hatchet thrown from slot 5 leaves it and remembers it", axe != null && inv.getItem(5).isEmpty() && axe.origin == 5);
        if (axe != null) {
            // its flight is saved: a hatchet saved on its way home reloads on its way home, as old and from the same slot
            axe.turnHome();
            axe.age = 9;
            net.minecraft.nbt.CompoundTag saved = axe.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            FrostbiteHatchet.Thrown copy = new FrostbiteHatchet.Thrown(FrostbiteHatchet.THROWN.get(), level);
            copy.load(saved);
            check.accept("a hatchet saved on its way home loads on its way home (age 9, slot 5)", copy.returning() && copy.age == 9 && copy.origin == 5);
            saved.remove("flight");
            FrostbiteHatchet.Thrown old = new FrostbiteHatchet.Thrown(FrostbiteHatchet.THROWN.get(), level);
            old.load(saved);
            check.accept("a hatchet saved before 1.9.5 loads flying out", old.flight() == FrostbiteHatchet.Thrown.Flight.OUTBOUND);
        }
        if (axe != null) {
            axe.giveBack(level, player);
            check.accept("and comes home to slot 5, in hand again", inv.getItem(5).is(FrostbiteHatchet.HATCHET.get())
                && player.getMainHandItem().is(FrostbiteHatchet.HATCHET.get()) && axe.isRemoved());
        }
        inv.clearContent();
        var homing = WeaponTrident.Thrown.create(CoralTrident.THROWN.get(), level, player, new ItemStack(CoralTrident.TRIDENT.get()));
        homing.origin = 6;
        homing.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.ALLOWED;
        check.accept("a thrown trident is picked up into the slot it was thrown from (6)", homing.pickUp(player)
            && inv.getItem(6).is(CoralTrident.TRIDENT.get()));
        inv.clearContent();
        inv.selected = 0;

        // Pathfinder's Glaive: no enchantment glint, ever
        ItemStack shiny = new ItemStack(PathfindersGlaive.GLAIVE.get());
        shiny.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 3);
        check.accept("an enchanted glaive shows no glint", shiny.isEnchanted() && !shiny.hasFoil());

        // Weathervane Spear: a charged wielder's spear carries the static flag the clients draw from
        ItemStack spear = new ItemStack(WeathervaneSpear.SPEAR.get());
        check.accept("a fresh spear shows no charge", !WeathervaneSpear.showsCharge(spear));

        // Coral Trident: a hit on a coral block grows one into the water beside it
        level.setBlock(centre.offset(-5, 1, 0), Blocks.BRAIN_CORAL_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(centre.offset(-5, 2, 0), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        boolean grew = CoralTrident.grow(level, centre.offset(-5, 1, 0));
        check.accept("coral trident grows coral", grew && level.getBlockState(centre.offset(-5, 2, 0)).is(Blocks.BRAIN_CORAL_BLOCK));

        // Fisher King's Trident: a roll of the fishing table
        List<ItemStack> fish = FisherKingsTrident.fish(level, player, new ItemStack(FisherKingsTrident.TRIDENT.get()), player);
        check.accept("fisher king's trident brings back a catch (" + fish + ")", !fish.isEmpty());
        level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(6)).forEach(Entity::discard);

        // the tridents throw their own entity, carrying their own item
        var thrown = WeaponTrident.Thrown.create(CoralTrident.THROWN.get(), level, player, new ItemStack(CoralTrident.TRIDENT.get()));
        check.accept("a thrown coral trident carries its item", thrown.getWeaponItem().is(CoralTrident.TRIDENT.get()));

        // Pathfinder's Glaive: plant, then recall from range
        ItemStack glaive = new ItemStack(PathfindersGlaive.GLAIVE.get());
        PathfindersGlaive.plant(level, player, glaive, at.south(3));
        String id = Weapons.data(glaive).getString("waypoint_id");
        Entity planted = id.isEmpty() ? null : level.getEntity(java.util.UUID.fromString(id));
        check.accept("glaive plants a waypoint (its own glow layer, no outline glow)", PathfindersGlaive.planted(glaive) && planted != null && !planted.isCurrentlyGlowing());
        PathfindersGlaive.recall(level, player, glaive);
        check.accept("and pulls it back", !PathfindersGlaive.planted(glaive) && planted != null && planted.isRemoved());

        // Recall Chime: the XP cost, and a mark
        check.accept("recall chime costs 5 / 10 / 60 XP for 0 / 160 / 5000 blocks",
            RecallChime.cost(0) == 5 && RecallChime.cost(160) == 10 && RecallChime.cost(5000) == 60);
        ItemStack chime = new ItemStack(RecallChime.CHIME.get());
        RecallChime.mark(level, player, chime);
        check.accept("recall chime marks", Weapons.data(chime).contains("mark_dim"));

        // Engineer's Crossbow: the zipline is BowsTest's (1.9.4)

        // Phantom Veil: worn in the back slot; at night mobs see half as far, and no phantoms come
        if (!wear(player, PhantomVeil.SLOT, new ItemStack(PhantomVeil.VEIL.get()))) {
            skip.accept("phantom veil worn: mobs see half as far at night, no phantoms", "a FakePlayer has no Curios slots");
        } else {
            check.accept("phantom veil goes in the back slot", PhantomVeil.wears(player));
            var seen = new LivingEvent.LivingVisibilityEvent(player, null, 1.0);
            NeoForge.EVENT_BUS.post(seen);
            check.accept("at night mobs see the wearer half as far (" + seen.getVisibilityModifier() + ")", seen.getVisibilityModifier() == 0.5);
            var phantoms = new PlayerSpawnPhantomsEvent(player, 2);
            NeoForge.EVENT_BUS.post(phantoms);
            check.accept("no phantoms for the wearer", phantoms.getResult() == PlayerSpawnPhantomsEvent.Result.DENY);
            wear(player, PhantomVeil.SLOT, ItemStack.EMPTY);
        }
        check.accept("the veil is tagged for the back slot", new ItemStack(PhantomVeil.VEIL.get()).is(net.minecraft.tags.ItemTags.create(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("curios", "back"))));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static List<String> weaponItems() {
        return List.of("weathervane_spear", "pathfinders_glaive", "duelists_saber", "hearthblade", "cold_hearthblade", "soul_hearthblade",
            "echo_blade", "resonant_hilt", "frostbite_hatchet", "bloom_bow", "voidstring_bow", "engineers_crossbow", "bastion_crossbow",
            "fisher_kings_trident", "coral_trident", "abyssal_trident", "quake_hammer", "recall_chime", "phantom_veil", "delvers_pick",
            "dormant_delvers_pick", "rusted_delvers_head", "delvers_shovel", "dormant_delvers_shovel");
    }

    private static Mob mob(ServerLevel level, EntityType<? extends Mob> type, Vec3 at) {
        Mob mob = type.create(level);
        mob.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        return mob;
    }

    /** Stone at {@code centre}'s height, air above it. */
    private static void floor(ServerLevel level, BlockPos centre, int r) {
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, 0, -r), centre.offset(r, 4, r))) {
            level.setBlock(pos, pos.getY() == centre.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static boolean wear(LivingEntity entity, String slot, ItemStack stack) {
        var inventory = CuriosApi.getCuriosInventory(entity).orElse(null);
        var handler = inventory == null ? null : inventory.getCurios().get(slot);
        if (handler == null || handler.getStacks().getSlots() == 0) {
            return false;
        }
        handler.getStacks().setStackInSlot(0, stack);
        return true;
    }
}
