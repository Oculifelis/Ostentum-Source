package com.ostentum.whip.more;

import com.ostentum.Props;
import com.fundamentum.whip.WhipDefinition;
import com.fundamentum.whip.WhipHit;
import com.fundamentum.whip.WhipHooks;
import com.ostentum.Ostentum;
import com.ostentum.whip.OstentumWhips;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/**
 * A diamond-studded cord with a blade at the tip: the cord itself hits light and the blade hits hard; a cracking tip
 * crit cuts for 2.5x and sprays sparks.
 */
public final class DiamondWhip {
    public static final RegistryObject<Item> ITEM = Props.simple(Ostentum.ITEMS, "diamond_whip", p -> p.stacksTo(1).enchantable(10));

    static WhipDefinition definition() {
        return WhipDefinition.builder(Ostentum.id("diamond_whip"))
            .tuning(t -> t.length(5.0).damage(9).rootDamage(0.25).falloffPower(2.2).tipFraction(0.12).tipCritMultiplier(2.5))
            .coiled().texture(OstentumWhips.lash("diamond_whip")).width(0.09F, 0.06F)
            .shockwave(0xA06AE6E6)
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.PLAYER_ATTACK_CRIT)
            .crackParticle(ParticleTypes.ENCHANTED_HIT)
            .hooks(new WhipHooks() {
                @Override
                public void landed(WhipHit hit, boolean hurt) {
                    if (hurt && hit.tip && hit.crit && hit.player.level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.contact.x, hit.contact.y, hit.contact.z, 10, 0.2, 0.2, 0.2, 0.2);
                    }
                }
            })
            .build();
    }

    private DiamondWhip() {}
}
