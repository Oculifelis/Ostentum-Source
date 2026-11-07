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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Weathervane Spear: a spear between iron and diamond with a faster lunge charge, from ordinary trial chamber vaults
 * (about 5%). Sprinting with it builds static; once charged, the next hit discharges a shock that hops to one more mob.
 * A jab on a redstone component (button, lever, door, trapdoor, fence gate, lamp) powers it for one second.
 */
public final class WeathervaneSpear {
    /** Between iron (250, +2) and diamond (1561, +3); repaired with copper, like its shaft. */
    static final ToolMaterial MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 900, 7.0F, 2.5F, 13, ItemTags.COPPER_TOOL_MATERIALS);

    public static final DeferredItem<Item> SPEAR = Weapons.item("weathervane_spear", Spear::new, p -> p.rarity(Rarity.RARE)
        // iron and diamond spears' numbers in between, with a shorter lunge delay (0.4 s; iron 0.6, diamond 0.5)
        .spear(MATERIAL, 1.0F, 1.0F, 0.4F, 2.75F, 10.5F, 6.6F, 5.1F, 10.6F, 4.6F));

    /** Sprint ticks until the static is charged, and how far the shock hops. */
    static final int CHARGE = 40;
    static final double HOP = 6.0;
    static final float SHOCK = 4.0F;

    /** Set on the held spear's stack while its wielder is charged, so every client can draw the crackle on it. */
    public static final String CHARGED_TAG = "ostentum_static";

    private static final Map<UUID, Integer> STATIC = new ConcurrentHashMap<>();
    private static boolean shocking;

    static void init() {
        NeoForge.EVENT_BUS.addListener(WeathervaneSpear::sprint);
        NeoForge.EVENT_BUS.addListener(WeathervaneSpear::hit);
        NeoForge.EVENT_BUS.addListener(WeathervaneSpear::jabBlock);
    }

    /** The spear: its static flag coming and going doesn't bob it in hand as a new item would. */
    static final class Spear extends Item {
        Spear(Properties properties) {
            super(properties);
        }

        @Override
        public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
            return slotChanged || !newStack.is(oldStack.getItem());
        }
    }

    /** Whether this spear stack shows its wielder's static (drawn by {@code client.LightningFx}). */
    public static boolean showsCharge(ItemStack stack) {
        return stack.is(SPEAR.get()) && Weapons.data(stack).getBooleanOr(CHARGED_TAG, false);
    }

    private static void flag(ItemStack stack, boolean on) {
        if (stack.is(SPEAR.get()) && showsCharge(stack) != on) {
            Weapons.edit(stack, tag -> {
                if (on) {
                    tag.putBoolean(CHARGED_TAG, true);
                } else {
                    tag.remove(CHARGED_TAG);
                }
            });
        }
    }

    public static boolean charged(Player player) {
        return STATIC.getOrDefault(player.getUUID(), 0) >= CHARGE;
    }

    private static void sprint(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Weapons.holds(player, SPEAR)) {
            return;
        }
        int now = STATIC.getOrDefault(player.getUUID(), 0);
        if (player.isSprinting() && now < CHARGE) {
            STATIC.put(player.getUUID(), ++now);
            if (now == CHARGE) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.6F, 1.8F);
                SpearArcs.send((ServerLevel) player.level(), tip(player), tip(player), SpearArcs.BURST);
            }
        }
        flag(player.getMainHandItem(), now >= CHARGE);
        if (now >= CHARGE && player.tickCount % 8 == 0) {
            Vec3 tip = tip(player);
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 2, 0.15, 0.15, 0.15, 0.05);
        }
    }

    /** The charged jab: a shock on the target and one hop to the nearest other mob. */
    private static void hit(LivingDamageEvent.Post event) {
        if (shocking || !(event.getSource().getEntity() instanceof ServerPlayer player) || event.getSource().getDirectEntity() != player
            || !Weapons.holds(player, SPEAR) || !charged(player)) {
            return;
        }
        STATIC.remove(player.getUUID());
        flag(player.getMainHandItem(), false);
        ServerLevel level = (ServerLevel) player.level();
        LivingEntity target = event.getEntity();
        LivingEntity next = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(HOP),
                e -> e != target && e != player && e.isAlive() && !e.isAlliedTo(player))
            .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(target))).orElse(null);
        shocking = true;
        try {
            target.hurtServer(level, level.damageSources().lightningBolt(), SHOCK);
            SpearArcs.send(level, tip(player), target.getBoundingBox().getCenter(), SpearArcs.SHOCK);
            sparks(level, target.getBoundingBox().getCenter());
            if (next != null) {
                next.hurtServer(level, level.damageSources().lightningBolt(), SHOCK);
                SpearArcs.send(level, target.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), SpearArcs.HOP);
                sparks(level, next.getBoundingBox().getCenter());
            }
        } finally {
            shocking = false;
        }
        level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    /** Where the held spear's head is, about: ahead of the eyes, down and to the right, along the look. */
    static Vec3 tip(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        return player.getEyePosition().add(look.scale(1.6)).add(right.scale(0.3)).add(0, -0.3, 0);
    }

    /** A spray of sparks where a bolt lands (the bolt itself is {@link SpearArcs}). */
    static void sparks(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, 0.2, 0.25, 0.2, 0.35);
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
                button.press(state, level, pos, player);
            }
        } else if (state.getBlock() instanceof LeverBlock lever) {
            if (!state.getValue(LeverBlock.POWERED)) {
                lever.pull(state, level, pos, player);
                Weapons.later(level, 20, () -> {
                    BlockState now = level.getBlockState(pos);
                    if (now.getBlock() == lever && now.getValue(LeverBlock.POWERED)) {
                        lever.pull(now, level, pos, null);
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
        SpearArcs.send(level, tip(player), Vec3.atCenterOf(pos), SpearArcs.JAB);
        sparks(level, Vec3.atCenterOf(pos));
        return true;
    }

    private static void toggleFor(ServerLevel level, BlockPos pos, BlockState state,
                                  net.minecraft.world.level.block.state.properties.BooleanProperty property) {
        if (state.getValue(property)) {
            return;
        }
        level.setBlock(pos, state.setValue(property, true), 3);
        level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.6F, 1.2F);
        Weapons.later(level, 20, () -> {
            BlockState now = level.getBlockState(pos);
            if (now.getBlock() == state.getBlock() && now.getValue(property)) {
                level.setBlock(pos, now.setValue(property, false), 3);
            }
        });
    }

    private WeathervaneSpear() {}
}
