package com.ostentum.whip.more;

import com.ostentum.Props;
import com.fundamentum.whip.WhipDefinition;
import com.fundamentum.whip.WhipHit;
import com.fundamentum.whip.WhipHooks;
import com.ostentum.Ostentum;
import com.ostentum.whip.OstentumWhips;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Iron chain links: shorter, slower and harder than leather, it hits up to four in a sweep, and a tip hit wraps the
 * target (Slowness II for two seconds).
 */
public final class ChainWhip {
    public static final DeferredItem<Item> ITEM = Props.simple(Ostentum.ITEMS, "chain_whip", p -> p.stacksTo(1).enchantable(12));

    static WhipDefinition definition() {
        return WhipDefinition.builder(Ostentum.id("chain_whip"))
            .tuning(t -> t.length(4.0).damage(8).guide(0.75).cooldownTicks(18).maxTargets(4).knockback(0.6))
            .coiled().texture(OstentumWhips.lash("chain_whip")).width(0.12F, 0.07F)
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.CHAIN_HIT)
            .crackParticle(ParticleTypes.CRIT)
            .hooks(new WhipHooks() {
                @Override
                public void landed(WhipHit hit, boolean hurt) {
                    LivingEntity target = hit.living();
                    if (hurt && hit.tip && target != null) {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), hit.player);
                    }
                }
            })
            .build();
    }

    private ChainWhip() {}
}
