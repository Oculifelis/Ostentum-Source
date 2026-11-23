package com.ostentum.whip.more;

import com.fundamentum.whip.WhipDefinition;
import com.ostentum.Ostentum;
import com.ostentum.whip.OstentumWhips;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * A thick gold braid: the slowest swing and the biggest single hit, it knocks hard, and holding use charges a ground
 * slam that throws everything around where it lands.
 */
public final class GoldenHeavyWhip {
    public static final DeferredItem<Item> ITEM = Ostentum.ITEMS.registerSimpleItem("golden_heavy_whip", p -> p.stacksTo(1).fireResistant().enchantable(22));

    static WhipDefinition definition() {
        return WhipDefinition.builder(Ostentum.id("golden_heavy_whip"))
            .tuning(t -> t.length(4.0).damage(11).rootDamage(0.55).guide(0.7).windupTicks(6).strokeTicks(6).recoverTicks(10)
                .cooldownTicks(26).knockback(1.4))
            .coiled().texture(OstentumWhips.lash("golden_heavy_whip")).width(0.17F, 0.08F)
            .shockwave(0xB0FFD84A)
            .slam(new WhipDefinition.Slam(28, 2.5, 3.5, 12.0, 1.3, 0.55, 40))
            .sounds(SoundEvents.PLAYER_ATTACK_STRONG, SoundEvents.ANVIL_LAND)
            .crackParticle(ParticleTypes.WAX_ON)
            .build();
    }

    private GoldenHeavyWhip() {}
}
