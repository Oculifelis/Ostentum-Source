package com.ostentum.weapon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Coral Trident (trident + three coral blocks). Landing underwater it leaves a whirlpool for {@link #WHIRL} ticks that
 * pulls its thrower to it. Hits on coral or kelp grow them: kelp and seagrass as bone meal does, coral by a new piece
 * of the same coral beside it.
 */
public final class CoralTrident {
    public static final RegistryObject<EntityType<Thrown>> THROWN = WeaponTrident.type("coral_trident", Thrown::new);
    public static final RegistryObject<WeaponTrident.Item> TRIDENT = Weapons.item("coral_trident",
        p -> new WeaponTrident.Item(p, THROWN), WeaponTrident::props);

    public static final int WHIRL = 100;
    static final double REACH = 24.0;

    record Whirlpool(ServerLevel level, Vec3 at, UUID owner, long until) {}

    private static final List<Whirlpool> WHIRLPOOLS = new ArrayList<>();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(CoralTrident::whirl);
    }

    public static final class Thrown extends WeaponTrident.Thrown {
        public Thrown(EntityType<? extends Thrown> type, Level level) {
            super(type, level);
        }

        @Override
        net.minecraft.world.item.Item item() {
            return TRIDENT.get();
        }

        @Override
        void land(ServerLevel level) {
            if (isInWater() && getOwner() != null) {
                WHIRLPOOLS.add(new Whirlpool(level, position(), getOwner().getUUID(), level.getGameTime() + WHIRL));
                level.playSound(null, blockPosition(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }

        @Override
        void hitBlock(ServerLevel level, BlockHitResult hit) {
            grow(level, hit.getBlockPos());
        }
    }

    public static void whirlpool(ServerLevel level, Vec3 at, UUID owner) {
        WHIRLPOOLS.add(new Whirlpool(level, at, owner, level.getGameTime() + WHIRL));
    }

    /** Each tick of a live whirlpool: bubbles, and the thrower pulled toward it while in water and in reach. */
    private static void whirl(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (Iterator<Whirlpool> it = WHIRLPOOLS.iterator(); it.hasNext(); ) {
            Whirlpool pool = it.next();
            ServerLevel level = pool.level();
            if (level.getGameTime() > pool.until()) {
                it.remove();
                continue;
            }
            double spin = level.getGameTime() * 0.6;
            for (int i = 0; i < 3; i++) {
                double a = spin + i * Math.PI * 2 / 3;
                level.sendParticles(ParticleTypes.BUBBLE, pool.at().x + Math.cos(a) * 1.2, pool.at().y + 0.3 * i, pool.at().z + Math.sin(a) * 1.2,
                    1, 0, 0.1, 0, 0.0);
            }
            Entity owner = level.getEntity(pool.owner());
            if (owner != null && owner.isInWater() && owner.distanceToSqr(pool.at()) < REACH * REACH && owner.distanceToSqr(pool.at()) > 1.5) {
                Vec3 to = pool.at().subtract(owner.position()).normalize();
                owner.setDeltaMovement(owner.getDeltaMovement().scale(0.8).add(to.scale(0.25)));
                owner.hurtMarked = true;
            }
        }
    }

    /** Grows the coral or kelp at {@code pos}; true if it did. */
    public static boolean grow(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)) {
            BlockPos at = state.is(Blocks.KELP_PLANT) ? top(level, pos) : pos;
            return BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), level, at, null)
                || BoneMealItem.growWaterPlant(new ItemStack(Items.BONE_MEAL), level, at, Direction.UP);
        }
        if (!state.is(BlockTags.CORALS) && !state.is(BlockTags.WALL_CORALS) && !state.is(BlockTags.CORAL_BLOCKS)) {
            return false;
        }
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (!level.getFluidState(next).is(Fluids.WATER) || !level.getBlockState(next).is(Blocks.WATER)) {
                continue;
            }
            BlockState copy = state;
            if (!state.is(BlockTags.CORAL_BLOCKS)) {
                // plants and fans need a block under them; set them on the floor beside
                if (!level.getBlockState(next.below()).isFaceSturdy(level, next.below(), Direction.UP) || state.is(BlockTags.WALL_CORALS)) {
                    continue;
                }
            }
            if (copy.hasProperty(BlockStateProperties.WATERLOGGED)) {
                copy = copy.setValue(BlockStateProperties.WATERLOGGED, true);
            }
            level.setBlockAndUpdate(next, copy);
            level.levelEvent(1505, next, 5);
            return true;
        }
        return false;
    }

    private static BlockPos top(ServerLevel level, BlockPos pos) {
        BlockPos at = pos;
        while (level.getBlockState(at.above()).is(Blocks.KELP_PLANT) || level.getBlockState(at.above()).is(Blocks.KELP)) {
            at = at.above();
        }
        return at;
    }

    private CoralTrident() {}
}
