package com.ostentum;

import com.fundamentum.creature.CreatureTypes;
import com.fundamentum.hook.Hooks;
import com.fundamentum.moon.Moon;
import com.fundamentum.moon.Moons;
import com.fundamentum.moon.NightEvents;
import com.fundamentum.mount.Mounts;
import com.fundamentum.sickness.Sickness;
import com.fundamentum.summon.Summons;
import com.ostentum.relic.Relics;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Dev-only quick test, run by the dev server when -Dostentum.selftest=true (see build.gradle). Prints one
 * "OSTENTUM TEST" line per check, then stops the server. Never runs in a player's game.
 */
public final class SelfTest {
    private SelfTest() {}

    static void init() {
        if (Boolean.getBoolean("ostentum.selftest")) {
            NeoForge.EVENT_BUS.addListener(SelfTest::run);
        }
    }

    private static int failed;

    private static void check(String what, boolean ok) {
        if (!ok) {
            failed++;
        }
        System.out.println("OSTENTUM TEST " + (ok ? "PASS " : "FAIL ") + what);
    }

    private static int skipped;

    /** A check this headless test cannot make, with the reason; counted apart, so DONE stays honest. */
    private static void skip(String what, String why) {
        skipped++;
        System.out.println("OSTENTUM TEST SKIP " + what + " (" + why + ")");
    }

    /** Boss relics: the 3x3 places its frame and clears it, a frame breaks the relic, both bosses give one. */
    private static void relics(ServerLevel level, FakePlayer player, BlockPos centre) {
        check("relic block entity covers all three relics", Relics.RELIC.get().isValid(Relics.WITHER_RELIC.get().defaultBlockState())
            && Relics.RELIC.get().isValid(Relics.ELDER_GUARDIAN_RELIC.get().defaultBlockState())
            && Relics.RELIC.get().isValid(Relics.WARDEN_RELIC.get().defaultBlockState())
            && Relics.RELIC.get().isValid(Relics.DRAGON_RELIC.get().defaultBlockState()));
        // relic items an earlier run left lying here (the dev world is kept between runs) would be counted as drops
        level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(centre).inflate(4)).forEach(net.minecraft.world.entity.Entity::discard);
        BlockState relic = Relics.DRAGON_RELIC.get().defaultBlockState();
        level.setBlock(centre, relic, Block.UPDATE_ALL);
        relic.getBlock().setPlacedBy(level, centre, relic, player, new ItemStack(Relics.DRAGON_RELIC_ITEM.get()));
        check("dragon relic places 8 frame blocks", frames(level, centre) == 8);
        check("dragon relic has its block entity", level.getBlockEntity(centre) instanceof com.ostentum.relic.RelicBlockEntity);
        level.removeBlock(centre, false);
        check("frame clears once the centre is gone (left " + frames(level, centre) + ")", frames(level, centre) == 0);

        level.setBlock(centre, relic, Block.UPDATE_ALL);
        relic.getBlock().setPlacedBy(level, centre, relic, player, new ItemStack(Relics.DRAGON_RELIC_ITEM.get()));
        BlockPos corner = centre.offset(1, 0, 1);
        BlockState frame = level.getBlockState(corner);
        frame.getBlock().playerWillDestroy(level, corner, frame, player);
        level.removeBlock(corner, false);
        check("breaking a frame breaks the relic", level.getBlockState(centre).isAir() && frames(level, centre) == 0);
        long dropped = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(centre).inflate(3))
            .stream().filter(e -> e.getItem().is(Relics.DRAGON_RELIC_ITEM.get())).count();
        check("and drops it (" + dropped + ")", dropped == 1);

        // the bosses are killed for real (vanilla death and loot path), not handed a hand-made event: a posted event
        // only proves the handler, not that the game ever calls it the way the check assumes
        BlockPos sky = centre.above(8);
        var wither = EntityType.WITHER.create(level, EntitySpawnReason.COMMAND);
        wither.snapTo(sky, 0, 0);
        level.addFreshEntity(wither);
        boolean witherHit = wither.hurtServer(level, level.damageSources().playerAttack(player), 10000f);
        long witherRelics = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(sky).inflate(4))
            .stream().filter(e -> e.getItem().is(Relics.WITHER_RELIC_ITEM.get())).count();
        check("a wither killed by a player drops its relic (hit " + witherHit + ", dead " + wither.isDeadOrDying()
            + ", relics " + witherRelics + ")", wither.isDeadOrDying() && witherRelics == 1);
        wither.discard();
        level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(sky).inflate(4)).forEach(Entity::discard);

        var elder = EntityType.ELDER_GUARDIAN.create(level, EntitySpawnReason.COMMAND);
        elder.snapTo(sky, 0, 0);
        level.addFreshEntity(elder);
        boolean elderHit = elder.hurtServer(level, level.damageSources().playerAttack(player), 10000f);
        long elderRelics = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(sky).inflate(4))
            .stream().filter(e -> e.getItem().is(Relics.ELDER_GUARDIAN_RELIC_ITEM.get())).count();
        check("an elder guardian killed by a player drops its relic (hit " + elderHit + ", dead " + elder.isDeadOrDying()
            + ", relics " + elderRelics + ")", elder.isDeadOrDying() && elderRelics == 1);
        elder.discard();
        var warden = EntityType.WARDEN.create(level, EntitySpawnReason.COMMAND);
        warden.snapTo(sky, 0, 0);
        level.addFreshEntity(warden);
        boolean wardenHit = warden.hurtServer(level, level.damageSources().playerAttack(player), 10000f);
        long wardenRelics = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(sky).inflate(4))
            .stream().filter(e -> e.getItem().is(Relics.WARDEN_RELIC_ITEM.get())).count();
        check("a warden killed by a player drops its relic (hit " + wardenHit + ", dead " + warden.isDeadOrDying()
            + ", relics " + wardenRelics + ")", warden.isDeadOrDying() && wardenRelics == 1);
        warden.discard();
        level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(sky).inflate(4)).forEach(Entity::discard);

        var dragon = EntityType.ENDER_DRAGON.create(level, EntitySpawnReason.COMMAND);
        dragon.snapTo(sky, 0, 0);
        level.addFreshEntity(dragon);
        player.getInventory().clearContent();
        boolean dragonHit = dragon.hurtServer(level, level.damageSources().playerAttack(player), 10000f);
        check("a dragon killed by a player gives its killer the relic (hit " + dragonHit + ")",
            player.getInventory().contains(new ItemStack(Relics.DRAGON_RELIC_ITEM.get())));
        dragon.discard();
    }

    private static int frames(ServerLevel level, BlockPos centre) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-1, 0, -1), centre.offset(1, 0, 1))) {
            n += level.getBlockState(p).is(Relics.DRAGON_RELIC_FRAME.get()) ? 1 : 0;
        }
        return n;
    }

    private static void run(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        try {
            // mounts, hooks and summons are checked by using them (behaviour(), phase two), not by their data being there
            check("glimmer moon loaded", Moons.get(Glimmer.MOON).isPresent());
            for (String slot : List.of("mount", "hook", "summon")) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath("curios", slot));
                long n = BuiltInRegistries.ITEM.stream().filter(i -> new ItemStack(i).is(tag) && BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("ostentum")).count();
                check("curios tag " + slot + " has " + n, n > 0);
            }
            long recipes = server.getRecipeManager().getRecipes().stream().filter(r -> r.id().identifier().getNamespace().equals("ostentum")).count();
            check("recipes loaded: " + recipes, recipes == 58 + (com.ostentum.music.NowPlaying.PENDANT_ON ? 1 : 0));   // the pendant's recipe is left out of a -Pnopendant build
            // the six base whips are Fundamentum whips with their own lengths; the Ender Whip's loot needs an End ship
            List<net.neoforged.neoforge.registries.DeferredItem<Item>> whips = new java.util.ArrayList<>(List.of(com.ostentum.whip.OstentumWhips.VINE_WHIP,
                com.ostentum.whip.OstentumWhips.WEEPING_VINE_WHIP, com.ostentum.whip.OstentumWhips.TWISTING_VINE_WHIP,
                com.ostentum.whip.OstentumWhips.GLOW_BERRY_WHIP, com.ostentum.whip.OstentumWhips.SILK_WHIP, com.ostentum.whip.OstentumWhips.ENDER_WHIP));
            com.ostentum.whip.more.MoreWhips.ALL.forEach(e -> whips.add(e.item()));
            for (var whip : whips) {
                var def = com.fundamentum.whip.Whips.definition(new ItemStack(whip.get()));
                check(whip.getId().getPath() + " is a whip (" + (def == null ? "none" : def.tuning.length() + " blocks") + ")",
                    def != null && def.texture.getPath().endsWith(whip.getId().getPath() + ".png"));
            }
            TagKey<Item> whipTag = TagKey.create(Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath("fundamentum", "whips"));
            check("all 18 whips in #fundamentum:whips", whips.stream().allMatch(w -> new ItemStack(w.get()).is(whipTag)) && whips.size() == 18);
            check("no End ship at 0 64 0 in the overworld", !com.ostentum.whip.EndShipLoot.onShip(level, new BlockPos(0, 64, 0)));
            // Thunderstorm Whip (1.8.5): its arcs need a clear line from one creature to the next
            {
                BlockPos at = new BlockPos(8, 230, 8);
                var a = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                var b = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                a.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                b.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 3.5, 0, 0);
                boolean open = com.ostentum.whip.more.ThunderstormWhip.inSight(level, a, b);
                for (int y = 0; y < 3; y++) {
                    level.setBlock(at.offset(0, y, 2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
                boolean walled = com.ostentum.whip.more.ThunderstormWhip.inSight(level, a, b);
                for (int y = 0; y < 3; y++) {
                    level.setBlock(at.offset(0, y, 2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                check("thunderstorm arcs see through air (" + open + ") but not a wall (" + walled + ")", open && !walled);
            }
            Moon enchanted = Moons.get(Enchanted.MOON).orElse(null);
            check("enchanted moon loaded", enchanted != null);
            check("enchanted moon tint shifts", enchanted != null && enchanted.fog().colorAt(0) != enchanted.fog().colorAt(4));
            check("harvest moon loaded", Moons.get(Harvest.MOON).isPresent());
            check("siege event loaded", NightEvents.get(Siege.EVENT).isPresent());

            // the sky darkens on the next ticks, and moons shine only once it is dark: finish in phase two
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set 18000");
            // Fundamentum 1.16.0: a moonrise listener that throws is logged against its mod and the others still run
            guardListeners();
            check("raise enchanted", Moons.raise(level, Enchanted.MOON));
        } catch (Throwable t) {
            failed++;
            System.out.println("OSTENTUM TEST FAIL exception " + t);
            t.printStackTrace(System.out);
        }
        NeoForge.EVENT_BUS.addListener(SelfTest::tick);
    }

    private static int ticks;
    private static final List<String> risen = new ArrayList<>();
    private static final List<String> errors = new ArrayList<>();
    private static Moons.Listener thrower;
    private static Moons.Listener after;
    private static org.apache.logging.log4j.core.appender.AbstractAppender catcher;

    /** One listener that throws on purpose, registered before one that works, and the ERROR lines caught as they come. */
    private static void guardListeners() {
        catcher = new org.apache.logging.log4j.core.appender.AbstractAppender("ostentum-selftest", null, null, true) {
            @Override
            public void append(org.apache.logging.log4j.core.LogEvent e) {
                if (e.getLevel() == org.apache.logging.log4j.Level.ERROR) {
                    errors.add(e.getMessage().getFormattedMessage());
                }
            }
        };
        catcher.start();
        ((org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger()).addAppender(catcher);
        thrower = new Moons.Listener() {
            @Override
            public void onRise(ServerLevel level, Moon moon) {
                throw new IllegalStateException("OSTENTUM TEST deliberate moonrise failure");
            }
        };
        after = new Moons.Listener() {
            @Override
            public void onRise(ServerLevel level, Moon moon) {
                risen.add(moon.id());
            }
        };
        Moons.addListener(thrower);
        Moons.addListener(after);
    }

    private static void guardChecks() {
        // reaching this tick at all means the throw did not take the server tick down, as it did at 14:20 on 10-29
        check("the listener after a throwing one still ran (saw " + risen + ")", risen.contains(Enchanted.MOON));
        String line = errors.stream().filter(e -> e.contains("moon rise listener")).findFirst().orElse("");
        check("moonrise ERROR names the mod: " + line, line.contains("mod 'ostentum'") && line.contains(Enchanted.MOON));
        Moons.removeListener(thrower);
        Moons.removeListener(after);
        ((org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger()).removeAppender(catcher);
        catcher.stop();
    }

    private static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (++ticks != 40) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        try {
            // Enchanted Moon: an enchantment under it spends no levels
            check("night now (dark outside " + level.isDarkOutside() + ")", Moons.isNight(level));
            check("enchanted is up", Enchanted.up(level));
            guardChecks();
            FakePlayer player = FakePlayerFactory.getMinecraft(level);
            player.experienceLevel = 5;
            player.onEnchantmentPerformed(new ItemStack(Items.IRON_SWORD), 3);
            check("free enchanting keeps 5 levels (has " + player.experienceLevel + ")", player.experienceLevel == 5);

            // Harvest Moon: ripe wheat drops twice, unripe wheat does not
            check("raise harvest", Moons.raise(level, Harvest.MOON));
            BlockPos pos = new BlockPos(0, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 0), 0);
            BlockState ripe = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
            check("ripe wheat doubles", drops(level, pos, ripe) == 4);
            check("young wheat does not", drops(level, pos, Blocks.WHEAT.defaultBlockState()) == 1);
            Moons.clear(level);
            check("no doubling without the moon", drops(level, pos, ripe) == 2);

            steering(level, player, pos.above());
            behaviour(level, player, pos.above());

            relics(level, player, pos.above(3));
            com.ostentum.music.ShelfTest.run(level, player, pos.offset(40, 1, 0), SelfTest::check);   // 1.9.4 record shelves, on a floor of their own off to the side

            // 1.9.0: the special weapons, on a floor of their own high above
            com.ostentum.weapon.WeaponTest.run(level, player, pos.above(24), SelfTest::check, SelfTest::skip);
            {   // 1.9.3: an Ender Whip thrown into an End portal comes back as the End Rift Whip, enchantments kept
                net.minecraft.core.BlockPos rift = pos.above(40);
                level.setBlockAndUpdate(rift, net.minecraft.world.level.block.Blocks.END_PORTAL.defaultBlockState());
                net.minecraft.world.item.ItemStack ender = new net.minecraft.world.item.ItemStack(com.ostentum.whip.OstentumWhips.ENDER_WHIP.get());
                ender.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Rifty"));
                net.minecraft.world.entity.item.ItemEntity thrown = new net.minecraft.world.entity.item.ItemEntity(level, rift.getX() + 0.5, rift.getY() + 0.3, rift.getZ() + 0.5, ender);
                level.addFreshEntity(thrown);
                var travel = new net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent(thrown, net.minecraft.world.level.Level.END);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(travel);
                check("an Ender Whip thrown into an End portal stays and becomes the End Rift Whip, name kept (canceled " + travel.isCanceled() + ", now " + thrown.getItem() + ")", travel.isCanceled()
                    && thrown.getItem().is(com.ostentum.whip.OstentumWhips.END_RIFT_WHIP.get()) && "Rifty".equals(thrown.getItem().getHoverName().getString()));
                net.minecraft.world.entity.item.ItemEntity other = new net.minecraft.world.entity.item.ItemEntity(level, rift.getX() + 0.5, rift.getY() + 0.3, rift.getZ() + 0.5,
                    new net.minecraft.world.item.ItemStack(com.ostentum.whip.OstentumWhips.VINE_WHIP.get()));
                level.addFreshEntity(other);
                var plain = new net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent(other, net.minecraft.world.level.Level.END);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(plain);
                check("another whip thrown into an End portal is left alone", !plain.isCanceled() && other.getItem().is(com.ostentum.whip.OstentumWhips.VINE_WHIP.get()));
                thrown.discard(); other.discard();
                level.setBlockAndUpdate(rift, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                check("the End Rift Whip is hidden from recipe viewers", new net.minecraft.world.item.ItemStack(com.ostentum.whip.OstentumWhips.END_RIFT_WHIP.get())
                    .is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "hidden_from_recipe_viewers"))));
            }


            // 1.4.0: pickaxes crack constructs (Fundamentum creature damage), enchanted golden apples give use sickness
            var golem = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            double pick = CreatureTypes.multiplier(golem, level.damageSources().playerAttack(player));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            double sword = CreatureTypes.multiplier(golem, level.damageSources().playerAttack(player));
            check("a pickaxe hits an iron golem x1.5 (" + pick + "), a sword x1 (" + sword + ")", pick == 1.5 && sword == 1.0);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            check("raise siege", NightEvents.raise(level, Siege.EVENT));
            NightEvents.clear(level);
        } catch (Throwable t) {
            failed++;
            System.out.println("OSTENTUM TEST FAIL exception " + t);
            t.printStackTrace(System.out);
        }
        System.out.println("OSTENTUM TEST DONE failed=" + failed + " skipped=" + skipped);
        server.halt(false);
    }

    /**
     * Fundamentum 1.15.0 steering, as far as a server can check it. The movement itself ({@code Mounts.steerTravel}) is
     * driven directly with the fake player as the rider. Who controls the mount ({@code MobMixin}) needs a real
     * passenger: NeoForge's FakePlayer.startRiding always returns false, so the test calls Entity's own startRiding
     * past that override, and reports a SKIP if the JVM refuses. What only a client shows (prediction, rubber-banding,
     * the camera) is in the play-test plan, fundamentum-steering-playtest.md in the shared notes.
     */
    private static void steering(ServerLevel level, FakePlayer player, BlockPos at) {
        // The real path: the pig saddle in the Curios mount slot, then Mounts.summon, as the V key does. (1.4.0 and
        // before checked a mark the test had set itself, so that check could only pass.)
        player.snapTo(at, 0, 0);
        Pig pig;
        boolean summonedPig = false;
        if (wear(player, "mount", new ItemStack(BuiltInRegistries.ITEM.getOptional(Ostentum.id("pig_saddle")).orElseThrow()))) {
            boolean summoned = Mounts.summon(player);
            if (!(Mounts.active(player) instanceof Pig p)) {
                check("the pig saddle summons a pig (summon returned " + summoned + ")", false);
                wear(player, "mount", ItemStack.EMPTY);
                return;
            }
            pig = p;
            summonedPig = true;
            check("summoning a pig marks it for steering", Mounts.steers(pig));
        } else {
            // the summon itself is checked on a real player by client/SteerTest; the mark here is set by hand only so the
            // travel and controlling-passenger checks below have a marked pig to work on
            skip("summoning a pig marks it for steering", "a FakePlayer has no Curios slots; client/SteerTest checks it");
            pig = EntityType.PIG.create(level, EntitySpawnReason.COMMAND);
            pig.snapTo(at, 0, 0);
            level.addFreshEntity(pig);
            pig.getPersistentData().putBoolean("fundamentum:mount_steer", true);
        }
        // What the mark is decided on (does vanilla already let the seated rider control it?) cannot be reproduced here:
        // Mounts.summon seats the rider through FakePlayer.startRiding, which refuses, so no summon here is ever controlled
        // by its rider and every one gets marked, a saddled horse included.
        skip("a horse summon is not marked", "the fake rider is never seated inside Mounts.summon, so the horse would be marked too");

        // Walking: faces the rider's look, forward at speed, backward a quarter as fast
        player.setYRot(90.0F);                           // facing -x
        player.setXRot(0.0F);
        player.zza = 1.0F;
        player.xxa = 0.0F;
        pig.setDeltaMovement(Vec3.ZERO);
        Mounts.steerTravel(pig, player);
        double fwd = pig.getDeltaMovement().horizontalDistance();
        check("steered pig turns to the rider's look (yaw " + pig.getYRot() + ")", pig.getYRot() == 90.0F && pig.yBodyRot == 90.0F);
        check("steered pig moves the way the rider looks (dx " + pig.getDeltaMovement().x + ")", pig.getDeltaMovement().x < -1.0E-4
            && Math.abs(pig.getDeltaMovement().z) < Math.abs(pig.getDeltaMovement().x) * 0.01);
        player.zza = -1.0F;
        pig.setDeltaMovement(Vec3.ZERO);
        Mounts.steerTravel(pig, player);
        double back = pig.getDeltaMovement().horizontalDistance();
        check("backing up is slower (" + back + " vs " + fwd + ")", back > 0 && back < fwd * 0.5);
        player.zza = 0.0F;
        pig.setDeltaMovement(Vec3.ZERO);
        Mounts.steerTravel(pig, player);
        check("no keys, no movement", pig.getDeltaMovement().horizontalDistance() < 1.0E-6);

        // Swimming: in water the rider's pitch climbs and dives
        BlockPos pool = at.offset(8, 0, 0);
        for (BlockPos p : BlockPos.betweenClosed(pool.offset(-1, -1, -1), pool.offset(1, 2, 1))) {
            level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        }
        Entity dolphinEntity = EntityType.DOLPHIN.create(level, EntitySpawnReason.COMMAND);
        if (dolphinEntity instanceof LivingEntity dolphin) {
            dolphin.snapTo(pool, 0, 0);
            level.addFreshEntity(dolphin);
            dolphin.tick();                              // picks up that it is in water
            player.zza = 1.0F;
            player.setXRot(-45.0F);                      // looking up
            dolphin.setDeltaMovement(Vec3.ZERO);
            Mounts.steerTravel(dolphin, player);
            double up = dolphin.getDeltaMovement().y;
            player.setXRot(45.0F);                       // looking down
            dolphin.setDeltaMovement(Vec3.ZERO);
            Mounts.steerTravel(dolphin, player);
            double down = dolphin.getDeltaMovement().y;
            check("in water, looking up climbs and down dives (" + up + ", " + down + ")", dolphin.isInWater() && up > down + 1.0E-4);
            dolphin.discard();
        }
        player.zza = 0.0F;
        player.setXRot(0.0F);

        // Control: with the rider really seated, the steered pig answers to them; a saddled horse still does through
        // vanilla; an unmarked pig without a stick does not
        if (ride(player, pig)) {
            check("the rider controls a steered pig", pig.getControllingPassenger() == player);
            pig.getPersistentData().putBoolean("fundamentum:mount_steer", false);
            check("an unmarked pig ignores a rider without a stick", pig.getControllingPassenger() == null);
            player.stopRiding();
            Entity horseEntity = EntityType.HORSE.create(level, EntitySpawnReason.COMMAND);
            if (horseEntity instanceof Mob horse) {
                horse.snapTo(at.offset(-8, 0, 0), 0, 0);
                level.addFreshEntity(horse);
                horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
                ride(player, horse);
                check("a saddled horse is still vanilla's (controlled " + (horse.getControllingPassenger() == player)
                    + ", not marked " + !Mounts.steers(horse) + ")", horse.getControllingPassenger() == player && !Mounts.steers(horse));
                player.stopRiding();
                horse.discard();
            }
        } else {
            skip("the rider controls a steered pig", "could not seat the FakePlayer past its startRiding override");
        }
        if (summonedPig) {
            Mounts.dismiss(player);
            check("putting the mount away removes it", pig.isRemoved() && Mounts.active(player) == null);
        } else {
            pig.discard();
        }
        wear(player, "mount", ItemStack.EMPTY);
        skip("steering in play", "client prediction, rubber-banding and the camera need a client; see fundamentum-steering-playtest.md");
    }

    /** Puts a stack in the first slot of a Curios slot type; false when the entity has no such slot. */
    /**
     * The things Ostentum adds through Fundamentum, used the way a player uses them. These replaced checks that only
     * asked whether the data was loaded, which passed whether or not the item did anything.
     */
    private static void behaviour(ServerLevel level, FakePlayer player, BlockPos pos) {
        player.snapTo(pos, 0, 0);
        if (wear(player, "mount", ItemStack.EMPTY)) {
            curios(player);
        } else {
            skip("mounts, hooks, wings and summons in use", "a FakePlayer has no Curios slots; client/SteerTest runs them on a real player");
        }
        armourAndFood(level, player, pos);
    }

    /** Mounts, hooks, wings and summons, from their Curios slots, on a real player (client/SteerTest) or one with slots. */
    public static void curios(net.minecraft.server.level.ServerPlayer player) {
        // every saddle summons the vanilla creature it is named after (the name, not the data, says which)
        for (String m : Ostentum.MOUNTS) {
            ItemStack saddle = new ItemStack(BuiltInRegistries.ITEM.getValue(Ostentum.id(m + "_saddle")));
            boolean summoned = wear(player, "mount", saddle) && Mounts.summon(player);
            Entity mount = Mounts.active(player);
            EntityType<?> want = BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(m));
            check(m + " saddle summons a " + m + " (got " + (mount == null ? "nothing" : mount.getType()) + ")",
                summoned && mount != null && mount.getType() == want);
            Mounts.dismiss(player);
        }
        wear(player, "mount", ItemStack.EMPTY);

        // every hook fires a hook entity from the hook slot
        for (String h : Ostentum.HOOKS) {
            wear(player, "hook", new ItemStack(BuiltInRegistries.ITEM.getValue(Ostentum.id(h))));
            boolean fired = Hooks.fire(player);
            check(h + " fires a hook (" + Hooks.out(player).size() + " out)", fired && Hooks.out(player).size() == 1);
            Hooks.release(player);
        }
        wear(player, "hook", ItemStack.EMPTY);

        // every summon item calls its creatures on the player's half-second tick (posted by hand: a FakePlayer is not ticked)
        for (String m : Ostentum.SUMMONS) {
            Summons.Def def = Summons.SUMMONS.get(Ostentum.id(m));
            wear(player, "summon", new ItemStack(BuiltInRegistries.ITEM.getValue(Ostentum.id(m))));
            player.tickCount = 10;
            NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
            List<Mob> minions = Summons.minions(player);
            check(m + " calls " + (def == null ? "?" : def.count() + " " + def.entity()) + " (got " + minions.size() + ")",
                def != null && minions.size() == def.count()
                    && minions.stream().allMatch(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(def.entity())));
            Summons.dismiss(player);
        }
        wear(player, "summon", ItemStack.EMPTY);
        System.out.println("OSTENTUM TEST CURIOS DONE failed=" + failed);
    }

    private static void armourAndFood(ServerLevel level, FakePlayer player, BlockPos pos) {
        // netherite armour resists fire: the same burn hurts a zombie in a netherite helmet less (fire skips armour)
        var bare = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        var helmed = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        for (var z : List.of(bare, helmed)) {
            z.snapTo(pos.above(2), 0, 0);
            level.addFreshEntity(z);
        }
        helmed.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        // armour modifiers are applied when the wearer next ticks, as in play
        bare.tick();
        helmed.tick();
        check("a netherite helmet gives fire resistance (" + helmed.getAttributeValue(com.fundamentum.damage.Elements.FIRE.resistance()) + ")",
            helmed.getAttributeValue(com.fundamentum.damage.Elements.FIRE.resistance()) > 0);
        float before = bare.getHealth();
        bare.hurtServer(level, level.damageSources().onFire(), 8f);
        helmed.hurtServer(level, level.damageSources().onFire(), 8f);
        float lostBare = before - bare.getHealth();
        float lostHelmed = before - helmed.getHealth();
        check("a netherite helmet takes the edge off fire (" + lostHelmed + " vs " + lostBare + ")", lostBare > 0 && lostHelmed < lostBare);
        bare.discard();
        helmed.discard();

        // an enchanted golden apple, eaten to the end, makes the next one refuse to be eaten; a plain one does not
        eat(level, player, new ItemStack(Items.GOLDEN_APPLE));
        check("a plain golden apple leaves no sickness", !Sickness.isBlocked(player, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)));
        eat(level, player, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
        ItemStack second = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        player.setItemInHand(InteractionHand.MAIN_HAND, second);
        var result = player.gameMode.useItem(player, level, second, InteractionHand.MAIN_HAND);
        check("the next enchanted golden apple is refused (" + result + ", using " + player.isUsingItem() + ")",
            !player.isUsingItem() && !result.consumesAction());
        player.removeAllEffects();
        player.stopUsingItem();
    }

    /** Eats {@code food} the way a player does: starts using it through the game mode, then finishes the use. */
    private static void eat(ServerLevel level, FakePlayer player, ItemStack food) {
        player.setItemInHand(InteractionHand.MAIN_HAND, food);
        player.gameMode.useItem(player, level, food, InteractionHand.MAIN_HAND);
        try {
            MethodHandles.privateLookupIn(LivingEntity.class, MethodHandles.lookup())
                .findVirtual(LivingEntity.class, "completeUsingItem", MethodType.methodType(void.class)).invoke(player);
        } catch (Throwable t) {
            throw new IllegalStateException("could not finish eating", t);
        }
    }

    private static boolean wear(LivingEntity entity, String slot, ItemStack stack) {
        var inventory = CuriosApi.getCuriosInventoryOrNull(entity);
        var handler = inventory == null ? null : inventory.getCurios().get(slot);
        if (handler == null || handler.getStacks().getSlots() == 0) {
            return false;
        }
        handler.getStacks().setStackInSlot(0, stack);
        return true;
    }

    /** Seats the fake player through Entity.startRiding itself, skipping FakePlayer's override that always refuses. */
    private static boolean ride(FakePlayer player, Entity mount) {
        try {
            MethodHandle start = MethodHandles.privateLookupIn(FakePlayer.class, MethodHandles.lookup()).findSpecial(Entity.class,
                "startRiding", MethodType.methodType(boolean.class, Entity.class, boolean.class, boolean.class), FakePlayer.class);
            return (boolean) start.invoke(player, mount, true, false) && player.getVehicle() == mount;
        } catch (Throwable t) {
            System.out.println("OSTENTUM TEST NOTE could not seat the fake player: " + t);
            return false;
        }
    }

    private static int drops(ServerLevel level, BlockPos pos, BlockState state) {
        List<ItemEntity> list = new ArrayList<>();
        list.add(new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.WHEAT)));
        if (Harvest.ripe(state)) {
            list.add(new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.WHEAT_SEEDS)));
        }
        BlockDropsEvent e = new BlockDropsEvent(level, pos, state, null, list, null, ItemStack.EMPTY);
        NeoForge.EVENT_BUS.post(e);
        return e.getDrops().size();
    }
}
