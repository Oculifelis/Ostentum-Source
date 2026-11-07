package com.ostentum.weapon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeTier;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Weathervane Spear: a spear between iron and diamond with a faster lunge charge, from ordinary trial chamber vaults
 * (about 5%). Sprinting with it builds static; once charged, the next hit discharges a shock that hops to one more mob.
 * A jab on a redstone component (button, lever, door, trapdoor, fence gate, lamp) powers it for one second.
 */
public final class WeathervaneSpear {
    /** Between iron (250, +2) and diamond (1561, +3); repaired with copper, like its shaft. */
    static final Tier MATERIAL = new ForgeTier(2, 900, 7.0F, 2.5F, 13, BlockTags.NEEDS_IRON_TOOL, () -> net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.COPPER_INGOT));

    public static final RegistryObject<Item> SPEAR = Weapons.item("weathervane_spear", Item::new, p -> p.rarity(Rarity.RARE)
        // iron and diamond spears' numbers in between, with a shorter lunge delay (0.4 s; iron 0.6, diamond 0.5)
        .spear(MATERIAL, 1.0F, 1.0F, 0.4F, 2.75F, 10.5F, 6.6F, 5.1F, 10.6F, 4.6F));

    /** Sprint ticks until the static is charged, and how far the shock hops. */
    static final int CHARGE = 40;
    static final double HOP = 6.0;
    static final float SHOCK = 4.0F;

    private static final Map<UUID, Integer> STATIC = new ConcurrentHashMap<>();
    private static boolean shocking;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(WeathervaneSpear::sprint);
        MinecraftForge.EVENT_BUS.addListener(WeathervaneSpear::hit);
        MinecraftForge.EVENT_BUS.addListener(WeathervaneSpear::jabBlock);
    }

    public static boolean charged(Player player) {
        return STATIC.getOrDefault(player.getUUID(), 0) >= CHARGE;
    }

    private static void sprint(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || !Weapons.holds(player, SPEAR)) {
            return;
        }
        int now = STATIC.getOrDefault(player.getUUID(), 0);
        if (player.isSprinting() && now < CHARGE) {
            STATIC.put(player.getUUID(), ++now);
            if (now == CHARGE) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.6F, 1.8F);
            }
        }
        if (now >= CHARGE && player.tickCount % 5 == 0) {
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(),
                3, 0.3, 0.5, 0.3, 0.05);
        }
    }

    /** The charged jab: a shock on the target and one hop to the nearest other mob. */
    private static void hit(LivingDamageEvent event) {
        if (shocking || !(event.getSource().getEntity() instanceof ServerPlayer player) || event.getSource().getDirectEntity() != player
            || !Weapons.holds(player, SPEAR) || !charged(player)) {
            return;
        }
        STATIC.remove(player.getUUID());
        ServerLevel level = (ServerLevel) player.level();
        LivingEntity target = event.getEntity();
        LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(HOP),
                e -> e != target && e != player && e.isAlive() && !e.isAlliedTo(player))
            .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(target))).orElse(null);
        shocking = true;
        try {
            target.hurt(level.damageSources().lightningBolt(), SHOCK);
            sparks(level, player.getEyePosition(), target.getBoundingBox().getCenter());
            if (next != null) {
                next.hurt(level.damageSources().lightningBolt(), SHOCK);
                sparks(level, target.getBoundingBox().getCenter(), next.getBoundingBox().getCenter());
            }
        } finally {
            shocking = false;
        }
        level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    static void sparks(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from).scale(1.0 / 8);
        for (int i = 0; i <= 8; i++) {
            Vec3 at = from.add(step.scale(i));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.0);
        }
    }

    /** A jab (left click) on a redstone component powers it for one second instead of mining it. */
    private static void jabBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || !(event.getLevel() instanceof ServerLevel level)
            || !Weapons.holds(event.getEntity(), SPEAR)) {
            return;
        }
        if (power(level, event.getPos(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Powers the component at {@code pos} for a second; false if it is not one. */
    public static boolean power(ServerLevel level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ButtonBlock button) {
            if (!state.getValue(ButtonBlock.POWERED)) {
                button.press(state, level, pos);
            }
        } else if (state.getBlock() instanceof LeverBlock lever) {
            if (!state.getValue(LeverBlock.POWERED)) {
                lever.pull(state, level, pos);
                Weapons.later(level, 20, () -> {
                    BlockState now = level.getBlockState(pos);
                    if (now.getBlock() == lever && now.getValue(LeverBlock.POWERED)) {
                        lever.pull(now, level, pos);
                    }
                });
            }
        } else if (state.getBlock() instanceof DoorBlock door) {
            if (!door.isOpen(state)) {
                door.setOpen(player, level, state, pos, true);
                Weapons.later(level, 20, () -> {
                    BlockState now = level.getBlockState(pos);
                    if (now.getBlock() == door) {
                        door.setOpen(null, level, now, pos, false);
                    }
                });
            }
        } else if (state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock) {
            toggleFor(level, pos, state, BlockStateProperties.OPEN);
        } else if (state.getBlock() instanceof RedstoneLampBlock) {
            toggleFor(level, pos, state, RedstoneLampBlock.LIT);
        } else {
            return false;
        }
        sparks(level, player.getEyePosition(), Vec3.atCenterOf(pos));
        return true;
    }

    private static void toggleFor(ServerLevel level, BlockPos pos, BlockState state,
                                  net.minecraft.world.level.block.state.properties.BooleanProperty property) {
        if (state.getValue(property)) {
            return;
        }
        level.setBlock(pos, state.setValue(property, true), 3);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 1.2F);
        Weapons.later(level, 20, () -> {
            BlockState now = level.getBlockState(pos);
            if (now.getBlock() == state.getBlock() && now.getValue(property)) {
                level.setBlock(pos, now.setValue(property, false), 3);
            }
        });
    }

    private WeathervaneSpear() {}
}
