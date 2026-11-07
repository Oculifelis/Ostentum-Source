package com.ostentum.whip.more;

import com.ostentum.Props;
import com.fundamentum.whip.WhipDefinition;
import com.fundamentum.whip.WhipHit;
import com.fundamentum.whip.WhipHooks;
import com.ostentum.Ostentum;
import com.ostentum.whip.OstentumWhips;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * A slime cord under a gold crown: it bounces what it hits up a little, and every hit within three seconds of the last
 * adds a stack (up to five) that cuts two ticks off its cooldown, so a steady rhythm keeps getting faster.
 */
public final class RoyalSlimeWhip {
    public static final DeferredItem<Item> ITEM = Props.simple(Ostentum.ITEMS, "royal_slime_whip",
        p -> p.stacksTo(1).rarity(Rarity.UNCOMMON).enchantable(20));
    static final int BASE_COOLDOWN = 15, MAX_STACKS = 5, STACK_TICKS = 60;

    private record Stacks(int count, long lastHit) {}

    private static final Map<UUID, Stacks> STACKS = new ConcurrentHashMap<>();

    /** The player's current stacks (0 once three seconds pass without a hit). */
    public static int stacks(ServerPlayer player) {
        Stacks s = STACKS.get(player.getUUID());
        return s == null || player.level().getGameTime() - s.lastHit() > STACK_TICKS ? 0 : s.count();
    }

    static WhipDefinition definition() {
        return WhipDefinition.builder(Ostentum.id("royal_slime_whip"))
            .tuning(t -> t.length(5.0).damage(5).guide(0.6).retention(0.9).cooldownTicks(BASE_COOLDOWN).knockback(0.2))
            .coiled().texture(OstentumWhips.lash("royal_slime_whip")).width(0.13F, 0.06F)
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.SLIME_JUMP)
            .crackParticle(ParticleTypes.ITEM_SLIME)
            .hooks(new WhipHooks() {
                @Override
                public void swingStart(ServerPlayer player, ItemStack stack, WhipDefinition whip) {
                    int n = stacks(player);
                    if (n > 0) {
                        player.getCooldowns().addCooldown(stack.getItem(), Math.max(4, BASE_COOLDOWN - 2 * n));
                    }
                }

                @Override
                public void landed(WhipHit hit, boolean hurt) {
                    if (!hurt) {
                        return;
                    }
                    long now = hit.player.level().getGameTime();
                    STACKS.put(hit.player.getUUID(), new Stacks(Math.min(MAX_STACKS, stacks(hit.player) + 1), now));
                    if (hit.living() != null) {
                        hit.living().push(0, 0.35, 0);
                        hit.living().hurtMarked = true;
                    }
                    if (hit.player.level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.ITEM_SLIME, hit.contact.x, hit.contact.y, hit.contact.z, 6, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            })
            .build();
    }

    private RoyalSlimeWhip() {}
}
