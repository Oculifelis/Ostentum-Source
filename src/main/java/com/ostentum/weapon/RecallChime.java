package com.ostentum.weapon;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Recall Chime, only from End city chests (about 8%). Ringing it with no mark (or sneaking) sets a mark, free. Ringing
 * again in the same dimension pulls you back to the mark after a {@link #DELAY}-tick chime, for one durability (48
 * rings, repaired with amethyst shards; takes Unbreaking and Mending) and XP: 1 point per 16 blocks, 5 to 60, and it
 * won't ring if you can't pay. Then it rests for five minutes. A mark in another dimension is replaced, never used.
 */
public final class RecallChime {
    public static final DeferredItem<Item> CHIME = Weapons.item("recall_chime", Chime::new, p -> p.durability(48).repairable(Items.AMETHYST_SHARD)
        .enchantable(10).rarity(Rarity.EPIC));

    public static final int DELAY = 60;
    public static final int COOLDOWN = 6000;

    private static final Set<UUID> RINGING = ConcurrentHashMap.newKeySet();

    static void init() {}

    /** XP a recall over {@code distance} blocks costs. */
    public static int cost(double distance) {
        return Math.max(5, Math.min(60, (int) (distance / 16)));
    }

    static final class Chime extends Item {
        Chime(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(level instanceof ServerLevel server)) {
                return InteractionResult.SUCCESS;
            }
            ItemStack stack = player.getItemInHand(hand);
            if (RINGING.contains(player.getUUID())) {
                return InteractionResult.FAIL;
            }
            CompoundTag mark = Weapons.data(stack);
            String here = server.dimension().identifier().toString();
            if (player.isShiftKeyDown() || !mark.contains("mark_dim") || !mark.getStringOr("mark_dim", "").equals(here)) {
                mark(server, player, stack);
                return InteractionResult.SUCCESS;
            }
            Vec3 to = new Vec3(mark.getIntOr("mark_x", 0) + 0.5, mark.getIntOr("mark_y", 0), mark.getIntOr("mark_z", 0) + 0.5);
            int cost = cost(to.distanceTo(player.position()));
            if (!player.hasInfiniteMaterials() && player.totalExperience < cost) {
                player.sendOverlayMessage(Component.translatable("item.ostentum.recall_chime.poor", cost));
                ChimeFx.fail(server, player);
                return InteractionResult.FAIL;
            }
            if (!player.hasInfiniteMaterials()) {
                player.giveExperiencePoints(-cost);
            }
            stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
            RINGING.add(player.getUUID());
            ChimeFx.windup(server, player, DELAY);
            for (int t = 0; t < DELAY; t += 20) {
                int at = t;
                Weapons.later(server, t, () -> server.playSound(null, player.blockPosition(), com.ostentum.OstentumSounds.CHIME_WINDUP.get(),
                    SoundSource.PLAYERS, 1.0F, 0.8F + at / 100.0F));
            }
            Weapons.later(server, DELAY, () -> {
                RINGING.remove(player.getUUID());
                if (player.isAlive() && player.level() == server) {
                    recall(server, player, to);
                }
            });
            player.getCooldowns().addCooldown(stack, COOLDOWN);
            return InteractionResult.SUCCESS;
        }
    }

    public static void mark(ServerLevel level, Player player, ItemStack stack) {
        Weapons.edit(stack, tag -> {
            tag.putString("mark_dim", level.dimension().identifier().toString());
            tag.putInt("mark_x", player.blockPosition().getX());
            tag.putInt("mark_y", player.blockPosition().getY());
            tag.putInt("mark_z", player.blockPosition().getZ());
        });
        player.sendOverlayMessage(Component.translatable("item.ostentum.recall_chime.marked"));
        ChimeFx.mark(level, player);
    }

    public static void recall(ServerLevel level, Player player, Vec3 to) {
        ChimeFx.depart(level, player.position());
        player.teleportTo(to.x, to.y, to.z);
        player.resetFallDistance();
        ChimeFx.arrive(level, to);
    }

    private RecallChime() {}
}
