package com.ostentum.weapon;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/** 1.9.4's checks for the Voidstring Bow and the Duelist's Saber guard, run from WeaponTest on its stone floor. */
final class BowsTest {
    private BowsTest() {}

    static void run(ServerLevel level, FakePlayer player, BlockPos at, BiConsumer<String, Boolean> check, BiConsumer<String, String> skip) {
        voidstring(level, player, at, check);
        saber(level, player, at, check);
        zipline(level, player, at, check, skip);
    }

    private static Mob mob(ServerLevel level, EntityType<? extends Mob> type, Vec3 at) {
        Mob mob = type.create(level);
        mob.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        return mob;
    }

    private static FakePlayer other(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(("ostentum-test-" + name).getBytes()), name));
    }

    private static void voidstring(ServerLevel level, FakePlayer player, BlockPos at, BiConsumer<String, Boolean> check) {
        Vec3 base = Vec3.atBottomCenterOf(at);
        player.moveTo(base.x, base.y, base.z, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidstringBow.BOW.get()));
        player.getCooldowns().removeCooldown(VoidstringBow.BOW.get());

        // a mob: levitation and the swap
        Mob dummy = mob(level, EntityType.ZOMBIE, base.add(-2, 0, 0));
        Vec3 mine = player.position();
        boolean landed = VoidstringBow.land(level, player, dummy, true);
        check.accept("voidstring hit on a mob: levitation and swap (mob now at the shooter's spot)", landed && dummy.hasEffect(MobEffects.LEVITATION)
            && dummy.position().distanceTo(mine) < 0.01);
        check.accept("and no swap again while cooling down", !VoidstringBow.swap(level, player, dummy));
        dummy.discard();
        player.getCooldowns().removeCooldown(VoidstringBow.BOW.get());

        // the shooter itself is never a target
        check.accept("voidstring never affects the shooter", !VoidstringBow.affects(level, player, player));
        // 1.9.5: the bow's own release (vanilla's, with the Voidstring shot sound) still looses a voidstring arrow
        ItemStack loosed = new ItemStack(VoidstringBow.BOW.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, loosed);
        player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.ARROW, 2));
        net.minecraft.world.phys.AABB near = player.getBoundingBox().inflate(8);
        java.util.function.Predicate<net.minecraft.world.entity.projectile.AbstractArrow> ours = a -> a.getTags().contains(VoidstringBow.TAG);
        int before = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class, near, ours).size();
        loosed.getItem().releaseUsing(loosed, level, player, loosed.getUseDuration() - 20);
        int after = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class, near, ours).size();
        check.accept("the bow's release looses a voidstring arrow (" + before + " -> " + after + ")", after == before + 1);
        level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class, near, ours).forEach(net.minecraft.world.entity.Entity::discard);

        // a player target: the effect lands, under the PvP rules
        FakePlayer target = other(level, "VoidTarget");
        target.moveTo(base.x + 2, base.y, base.z, 0.0F, 0.0F);
        target.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        target.removeAllEffects();
        boolean onPlayer = VoidstringBow.land(level, player, target, false);
        // a FakePlayer may refuse mob effects outright; then only the rule's answer is checked
        boolean takesEffects = target.hasEffect(MobEffects.LEVITATION) || !target.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.GLOWING, 20));
        check.accept("voidstring hit on a (fake) player: the effect applies (levitation " + target.hasEffect(MobEffects.LEVITATION) + ", affects "
            + VoidstringBow.affects(level, player, target) + ", spectator " + target.isSpectator() + ", creative " + target.isCreative()
            + ", alive " + target.isAlive() + ", dead " + target.isDeadOrDying() + ")",
            onPlayer && takesEffects);
        target.removeAllEffects();
        level.getServer().setPvpAllowed(false);
        check.accept("with pvp off a player gets no voidstring effect", !VoidstringBow.land(level, player, target, false) && !target.hasEffect(MobEffects.LEVITATION));
        level.getServer().setPvpAllowed(true);
        var board = level.getScoreboard();
        var team = board.addPlayerTeam("ostentum_void_test");
        team.setAllowFriendlyFire(false);
        board.addPlayerToTeam(player.getScoreboardName(), team);
        board.addPlayerToTeam(target.getScoreboardName(), team);
        check.accept("a teammate without friendly fire gets no voidstring effect", !VoidstringBow.affects(level, player, target));
        board.removePlayerTeam(team);
        level.getGameRules().getRule(VoidstringBow.AFFECTS_PLAYERS).set(false, level.getServer());
        check.accept("gamerule voidstringAffectsPlayers false: damage only, no effect on players",
            !VoidstringBow.land(level, player, target, true) && !target.hasEffect(MobEffects.LEVITATION));
        Mob zombie = mob(level, EntityType.ZOMBIE, base.add(-2, 0, 2));
        check.accept("and mobs still get it", VoidstringBow.affects(level, player, zombie));
        zombie.discard();
        level.getGameRules().getRule(VoidstringBow.AFFECTS_PLAYERS).set(true, level.getServer());
        check.accept("gamerule is on by default and back on (" + VoidstringBow.affectsPlayers(level) + ")", VoidstringBow.affectsPlayers(level));


        // the immune tag
        Mob warden = mob(level, EntityType.WARDEN, base.add(4, 0, 4));
        check.accept("the warden is in #ostentum:voidstring_immune and gets nothing", VoidstringBow.swapImmune(warden)
            && !VoidstringBow.land(level, player, warden, true) && !warden.hasEffect(MobEffects.LEVITATION));
        warden.discard();
        check.accept("the immune tag holds the wither, dragon and elder guardian", EntityType.WITHER.builtInRegistryHolder().is(VoidstringBow.IMMUNE)
            && EntityType.ENDER_DRAGON.builtInRegistryHolder().is(VoidstringBow.IMMUNE) && EntityType.ELDER_GUARDIAN.builtInRegistryHolder().is(VoidstringBow.IMMUNE));

        // a kill teleports the shooter to where it fell, never into a block
        Mob victim = mob(level, EntityType.ZOMBIE, base.add(3, 0, -3));
        BlockPos wall = BlockPos.containing(victim.position()).above();
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        victim.setHealth(0.0F);
        Vec3 to = VoidstringBow.killTeleport(level, player, victim);
        check.accept("a voidstring kill teleports the shooter to a safe spot by the death position (" + to + ")", to != null
            && to.distanceTo(victim.position()) < 3.0 && VoidstringBow.fits(level, player, to));
        check.accept("the spot avoids the block at head height", to != null && !player.getDimensions(player.getPose()).makeBoundingBox(to)
            .intersects(new net.minecraft.world.phys.AABB(wall)));
        level.setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        victim.discard();
        player.getCooldowns().removeCooldown(VoidstringBow.BOW.get());
        Mob boss = mob(level, EntityType.WARDEN, base.add(-3, 0, -3));
        boss.setHealth(0.0F);
        check.accept("no kill teleport for an immune target", VoidstringBow.killTeleport(level, player, boss) == null);
        boss.discard();
        player.moveTo(base.x, base.y, base.z, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static boolean post(FakePlayer defender, net.minecraft.world.damagesource.DamageSource source, float amount, float[] out) {
        // 1.20.1: the parry is a LivingAttackEvent, the riposte a LivingHurtEvent
        var attack = new net.minecraftforge.event.entity.living.LivingAttackEvent(defender, source, amount);
        if (MinecraftForge.EVENT_BUS.post(attack)) {
            out[0] = amount;
            return true;
        }
        var hurt = new net.minecraftforge.event.entity.living.LivingHurtEvent(defender, source, amount);
        MinecraftForge.EVENT_BUS.post(hurt);
        out[0] = hurt.getAmount();
        return hurt.isCanceled();
    }

    private static void saber(ServerLevel level, FakePlayer player, BlockPos at, BiConsumer<String, Boolean> check) {
        Vec3 base = Vec3.atBottomCenterOf(at);
        player.moveTo(base.x, base.y, base.z, 0.0F, 0.0F);
        player.setHealth(player.getMaxHealth());
        ItemStack saber = new ItemStack(DuelistsSaber.SABER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, saber);
        long now = level.getGameTime();
        float[] amount = new float[1];

        // perfect window: the first PERFECT ticks of the guard
        player.startUsingItem(InteractionHand.MAIN_HAND);
        DuelistsSaber.guard(level, player);
        check.accept("saber guard is up while using (" + player.isUsingItem() + ")", DuelistsSaber.guarding(player));
        check.accept("perfect window open at 0 and 9, shut at 10", DuelistsSaber.perfect(player, now) && DuelistsSaber.perfect(player, now + DuelistsSaber.PERFECT - 1)
            && !DuelistsSaber.perfect(player, now + DuelistsSaber.PERFECT));
        Mob zombie = mob(level, EntityType.ZOMBIE, base.add(0, 0, 1.5));
        check.accept("perfect parry: a zombie hit from the front is cancelled, the zombie staggered, riposte ready",
            post(player, level.damageSources().mobAttack(zombie), 4.0F, amount) && zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && DuelistsSaber.riposteReady(player, now));
        Mob skeleton = mob(level, EntityType.SKELETON, base.add(0, 0, 6));
        Arrow arrow = new Arrow(EntityType.ARROW, level);
        arrow.setPos(base.x, base.y + 1.4, base.z + 1.0);
        arrow.setOwner(skeleton);
        arrow.setDeltaMovement(0, 0, -1.5);
        level.addFreshEntity(arrow);
        check.accept("perfect parry: a skeleton's arrow is cancelled and bounces back as the player's",
            post(player, level.damageSources().arrow(arrow, skeleton), 4.0F, amount) && arrow.getDeltaMovement().z > 0 && arrow.getOwner() == player);
        FakePlayer duelist = other(level, "Duelist");
        duelist.moveTo(base.x, base.y, base.z + 1.5, 180.0F, 0.0F);
        duelist.removeAllEffects();
        check.accept("perfect parry: a (fake) player's hit is cancelled and staggers them",
            post(player, level.damageSources().playerAttack(duelist), 4.0F, amount) && duelist.hasEffect(MobEffects.MOVEMENT_SLOWDOWN));

        // after the window: half damage from the front, full from behind
        DuelistsSaber.guardSince(player, now - DuelistsSaber.PERFECT - 5);
        boolean cancelled = post(player, level.damageSources().mobAttack(zombie), 4.0F, amount);
        check.accept("guard after the perfect window halves a front hit (4 -> " + amount[0] + ")", !cancelled && amount[0] == 2.0F);
        Mob behind = mob(level, EntityType.ZOMBIE, base.add(0, 0, -1.5));
        cancelled = post(player, level.damageSources().mobAttack(behind), 4.0F, amount);
        check.accept("a hit from behind goes through whole (4 -> " + amount[0] + ")", !cancelled && amount[0] == 4.0F);

        // whiff: letting go of a guard that caught something costs nothing; one that caught nothing costs WHIFF ticks
        check.accept("releasing a guard that blocked costs no cooldown", !DuelistsSaber.release(player, saber) && !player.getCooldowns().isOnCooldown(saber.getItem()));
        DuelistsSaber.guard(level, player);
        check.accept("releasing a guard that caught nothing costs the whiff cooldown", DuelistsSaber.release(player, saber) && player.getCooldowns().isOnCooldown(saber.getItem()));
        player.getCooldowns().removeCooldown(DuelistsSaber.SABER.get());
        player.stopUsingItem();
        check.accept("not guarding once the saber is lowered", !DuelistsSaber.guarding(player));
        zombie.discard();
        skeleton.discard();
        arrow.discard();
        behind.discard();

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.AIR));
        player.setHealth(player.getMaxHealth());
    }

    /** Steps the seat {@code n} ticks with no input, the passenger following. */
    private static void ride(Zipline line, Zipline.Rider rider, Entity hanger, int n, boolean brake) {
        rider.positionRider(hanger);
        for (int i = 0; i < n; i++) {
            rider.step(line, hanger, brake, null);
            rider.positionRider(hanger);
        }
    }

    private static Zipline rope(ServerLevel level, FakePlayer player, Vec3 a, Vec3 b) {
        BlockPos pa = BlockPos.containing(a);
        BlockPos pb = BlockPos.containing(b);
        level.setBlock(pa, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pb, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        return EngineersCrossbow.string(level, player.getUUID(), a, pa, b, pb);
    }

    /** A zombie that stands in for a rider: NeoForge's FakePlayer refuses to ride anything. */
    private static Mob hanger(ServerLevel level, Vec3 at) {
        Mob mob = mob(level, EntityType.ZOMBIE, at);
        mob.setNoAi(true);
        mob.setSilent(true);
        return mob;
    }

    private static void zipline(ServerLevel level, FakePlayer player, BlockPos at, BiConsumer<String, Boolean> check, BiConsumer<String, String> skip) {
        Vec3 base = Vec3.atBottomCenterOf(at).add(0, 7, 0);
        // the test floor can sit inside terrain: clear the air the ropes and riders need
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(base.add(-10, -5, -2)), BlockPos.containing(base.add(10, 5, 5)))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        check.accept("a rope longer than " + Zipline.MAX_LENGTH + " blocks is not strung",
            EngineersCrossbow.string(level, player.getUUID(), base, at, base.add(60, 0, 0), at) == null);
        check.accept("a rope shorter than " + Zipline.MIN_LENGTH + " blocks is not strung",
            EngineersCrossbow.string(level, player.getUUID(), base, at, base.add(2, 0, 0), at) == null);

        // downhill: 16 across, 6 down
        Zipline down = rope(level, player, base.add(-8, 3, 0), base.add(8, -3, 0));
        check.accept("zipline strung between two anchor blocks (" + (down == null ? "none" : String.format("%.1f", down.length())) + " blocks)", down != null);
        if (down == null) {
            return;
        }
        check.accept("the rope sags in the middle", Zipline.point(down.a(), down.b(), 0.5).y < (down.a().y + down.b().y) / 2 - 0.2);
        Vec3 hands = down.point(0.2);
        Vec3 grab = hands.add(0, -0.5, 0);
        if (level.getEntitiesOfClass(Zipline.class, down.getBoundingBox().inflate(1)).isEmpty()) {
            // the headless test's chunk has no loaded entity sections, so entity searches see nothing: check the geometry
            double t = down.nearest(grab);
            check.accept("hands half a block under the rope are within reach of it (t " + String.format("%.3f", t) + ", "
                + String.format("%.2f", down.point(t).distanceTo(grab)) + " away)", Math.abs(t - 0.2) < 0.05 && down.point(t).distanceTo(grab) <= 1.3);
        } else {
            Zipline.Catch c = Zipline.reach(level, grab, 1.3);
            check.accept("a player's hands near the rope catch it", c != null && c.line() == down && Math.abs(c.t() - 0.2) < 0.05);
        }
        // Forge 47's FakePlayer can ride (NeoForge's never does), so on 1.20.1 attach really seats it: check that, then let go
        Zipline.Rider seated = Zipline.attach(level, player, down, 0.2);
        check.accept("attach seats a player on the rope", seated != null && player.getVehicle() == seated);
        if (seated != null) {
            seated.letGo(level, false);
            check.accept("letting go leaves the rope", !player.isPassenger());
        }
        skip.accept("logging out while riding lets go", "a FakePlayer cannot ride, so it cannot log out from a rope (EngineersCrossbow.loggedOut calls the same letGo)");

        Mob hanger = hanger(level, hands.subtract(0, Zipline.HANG, 0));
        Zipline.Rider rider = Zipline.seat(level, hanger, down, 0.2);
        check.accept("a rider is seated on the rope", rider != null && hanger.getVehicle() == rider);
        if (rider == null) {
            hanger.discard();
            down.discard();
            return;
        }
        rider.positionRider(hanger);
        check.accept("the rider hangs " + Zipline.HANG + " below the rope", Math.abs(hanger.getY() - (rider.getY() - Zipline.HANG)) < 0.01);
        double t0 = rider.t();
        ride(down, rider, hanger, 10, false);
        double s10 = rider.speed();
        ride(down, rider, hanger, 10, false);
        check.accept("downhill the rider speeds up toward the low end (t " + String.format("%.2f -> %.2f", t0, rider.t()) + ", speed "
            + String.format("%.3f -> %.3f", s10, rider.speed()) + ")", rider.t() > t0 && rider.speed() > s10 && s10 > 0);
        double before = rider.speed();
        ride(down, rider, hanger, 3, true);
        check.accept("sneak brakes (" + String.format("%.3f -> %.3f", before, rider.speed()) + ")", rider.speed() < before * 0.7);
        ride(down, rider, hanger, 200, false);
        check.accept("the ride stops at the low end, short of the anchor (t " + String.format("%.3f", rider.t()) + ")", rider.t() > 0.9 && rider.t() < 1.0
            && rider.speed() == 0.0);

        // a mob in the way stops the rider
        rider.t = 0.3;
        rider.speed = 0.0;
        Vec3 p3 = down.point(0.3);
        rider.setPos(p3.x, p3.y, p3.z);
        Mob blocker = mob(level, EntityType.ZOMBIE, down.point(0.45).subtract(0, Zipline.HANG, 0));
        blocker.setNoGravity(true);
        blocker.setNoAi(true);
        if (level.getEntities(hanger, blocker.getBoundingBox()).isEmpty()) {
            // entity searches see nothing in a chunk whose entities are not loaded (the headless 26.x test floor)
            skip.accept("a mob in the way stops the rider", "entity searches see no entities in the test chunk (its entity sections are not loaded)");
        } else {
            ride(down, rider, hanger, 40, false);
            check.accept("a mob in the way stops the rider (t " + String.format("%.2f", rider.t()) + ")", rider.t() < 0.45 && rider.blocked());
        }
        blocker.discard();
        rider.t = 0.3;
        rider.speed = 0.0;
        rider.setPos(p3.x, p3.y, p3.z);
        // and a block
        BlockPos wall = BlockPos.containing(down.point(0.6).subtract(0, 1.0, 0));
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ride(down, rider, hanger, 60, false);
        check.accept("a block in the way stops the rider (t " + String.format("%.2f", rider.t()) + ")", rider.t() < 0.62 && rider.blocked());
        level.setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        // jump lets go, carried on along the rope and a little up
        rider.speed = 0.4;
        rider.letGo(level, true);
        check.accept("jump lets go: off the rope, the seat gone, moving on (" + hanger.getDeltaMovement() + ")", !hanger.isPassenger() && rider.isRemoved()
            && hanger.getDeltaMovement().x > 0.2 && hanger.getDeltaMovement().y > 0.1);

        // uphill slides back
        Zipline.Rider back = Zipline.seat(level, hanger, down, 0.7);
        if (back != null) {
            back.speed = -0.05;
            ride(down, back, hanger, 20, false);
            check.accept("going uphill slows and slides back (speed " + String.format("%.3f", back.speed()) + ")", back.speed() > 0);
            back.letGo(level, false);
        } else {
            check.accept("seated again for the uphill check", false);
        }

        // a flat rope: the rider settles in the sag
        Zipline flat = rope(level, player, base.add(-6, 0, 3), base.add(6, 0, 3));
        Zipline.Rider f = flat == null ? null : Zipline.seat(level, hanger, flat, 0.2);
        if (f != null) {
            ride(flat, f, hanger, 400, false);
            check.accept("on a flat rope the rider settles in the middle (t " + String.format("%.2f", f.t()) + ")", Math.abs(f.t() - 0.5) < 0.15
                && Math.abs(f.speed()) < 0.05);
            f.letGo(level, false);
        } else {
            check.accept("flat rope strung and seated", false);
        }

        // the line snaps when an anchor block goes, and a rider on it lets go
        Zipline.Rider last = Zipline.seat(level, hanger, down, 0.5);
        level.setBlock(BlockPos.containing(down.b()), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        for (int i = 0; i < 4; i++) {
            down.tick();
        }
        if (last != null) {
            last.tick();
        }
        check.accept("removing an anchor block snaps the line and drops the rider", down.isRemoved() && last != null && !hanger.isPassenger()
            && last.isRemoved());

        // a rider who dies lets go
        Zipline.Rider dying = flat == null ? null : Zipline.seat(level, hanger, flat, 0.5);
        if (dying != null) {
            hanger.setHealth(0.0F);
            dying.tick();
            check.accept("a rider who dies lets go", !hanger.isPassenger() && dying.isRemoved());
        } else {
            check.accept("seated for the death check", false);
        }
        hanger.discard();
        if (flat != null) {
            flat.discard();
        }
        for (Vec3 v : new Vec3[] {base.add(-8, 3, 0), base.add(8, -3, 0), base.add(-6, 0, 3), base.add(6, 0, 3)}) {
            level.setBlock(BlockPos.containing(v), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
