package com.ostentum.whip.more;

import com.ostentum.Props;
import com.fundamentum.whip.WhipDefinition;
import com.fundamentum.whip.WhipFx;
import com.fundamentum.whip.WhipHit;
import com.fundamentum.whip.WhipHooks;
import com.ostentum.Ostentum;
import com.ostentum.whip.OstentumWhips;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * A cord of amethyst shards: a tip hit shatters, throwing five crystal shards on through the target in a fan, each
 * hurting the first thing it meets.
 */
public final class AmethystWhip {
    public static final RegistryObject<Item> ITEM = Props.simple(Ostentum.ITEMS, "amethyst_whip", p -> p.stacksTo(1).enchantable(16));
    static final int PURPLE = 0xFFC890F0;

    static WhipDefinition definition() {
        return WhipDefinition.builder(Ostentum.id("amethyst_whip"))
            .tuning(t -> t.length(4.5).damage(6).cooldownTicks(14))
            .coiled().texture(OstentumWhips.lash("amethyst_whip")).width(0.11F, 0.05F)
            .shockwave(0xA0C890F0)
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.AMETHYST_BLOCK_CHIME)
            .crackParticle(ParticleTypes.END_ROD)
            .hooks(new WhipHooks() {
                @Override
                public void landed(WhipHit hit, boolean hurt) {
                    LivingEntity target = hit.living();
                    if (!hurt || !hit.tip || target == null) {
                        return;
                    }
                    Vec3 on = target.position().subtract(hit.player.position());
                    on = new Vec3(on.x, 0, on.z).lengthSqr() < 1.0E-4 ? hit.player.getLookAngle() : on.normalize();
                    Vec3 from = hit.contact.add(on.scale(target.getBbWidth() * 0.6 + 0.2));
                    for (int i = 0; i < 5; i++) {
                        double a = (i - 2) * 0.35;
                        Vec3 dir = new Vec3(on.x * Math.cos(a) - on.z * Math.sin(a), 0.08, on.x * Math.sin(a) + on.z * Math.cos(a));
                        WhipFx.bolt(hit.player, from, dir, null, WhipFx.BoltSpec.shard(3.0F, PURPLE), hit.whip.damage.make(hit.player, hit.stack), null);
                    }
                    hit.player.level().playSound(null, from.x, from.y, from.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
                }
            })
            .build();
    }

    private AmethystWhip() {}
}
