package com.ostentum.whip.more;

import com.fundamentum.whip.WhipDefinition;
import com.fundamentum.whip.WhipFx;
import com.fundamentum.whip.WhipHooks;
import com.ostentum.Ostentum;
import com.ostentum.whip.OstentumWhips;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredItem;

/** A cord strung with emeralds: when it cracks, three emerald bolts fly from the tip and home on the nearest hostiles. */
public final class EmeraldWhip {
    public static final DeferredItem<Item> ITEM = Ostentum.ITEMS.registerSimpleItem("emerald_whip", p -> p.stacksTo(1).enchantable(18));
    static final int GREEN = 0xFF41F384;

    static WhipDefinition definition() {
        return WhipDefinition.builder(Ostentum.id("emerald_whip"))
            .tuning(t -> t.length(5.0).damage(6).cooldownTicks(16))
            .coiled().texture(OstentumWhips.lash("emerald_whip")).width(0.1F, 0.04F)
            .shockwave(0xA041F384)
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.AMETHYST_BLOCK_RESONATE)
            .crackParticle(ParticleTypes.HAPPY_VILLAGER)
            .hooks(new WhipHooks() {
                @Override
                public void crack(ServerPlayer player, ItemStack stack, WhipDefinition whip, Vec3 tip) {
                    List<LivingEntity> near = WhipFx.hostilesNear(player, tip, 10);
                    Vec3 look = player.getLookAngle();
                    for (int i = 0; i < 3; i++) {
                        LivingEntity target = near.isEmpty() ? null : near.get(i % near.size());
                        double a = (i - 1) * 0.6;
                        Vec3 dir = new Vec3(look.x * Math.cos(a) - look.z * Math.sin(a), look.y + 0.25, look.x * Math.sin(a) + look.z * Math.cos(a));
                        WhipFx.bolt(player, tip, dir, target, WhipFx.BoltSpec.homing(4.0F, GREEN), whip.damage.make(player, stack), null);
                    }
                    player.level().playSound(null, tip.x, tip.y, tip.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.6F);
                }
            })
            .build();
    }

    private EmeraldWhip() {}
}
