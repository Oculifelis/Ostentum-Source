package com.ostentum.whip.more;

import com.ostentum.Props;
import com.fundamentum.whip.WhipDefinition;
import com.ostentum.Ostentum;
import com.ostentum.whip.OstentumWhips;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/** The plain whip: braided leather on an oak handle, Fundamentum's default numbers, the one the others are made from. */
public final class LeatherWhip {
    public static final RegistryObject<Item> ITEM = Props.simple(Ostentum.ITEMS, "leather_whip", p -> p.stacksTo(1).enchantable(15));

    static WhipDefinition definition() {
        return WhipDefinition.builder(Ostentum.id("leather_whip"))
            .tuning(t -> t.length(4.5).damage(6))
            .coiled().texture(OstentumWhips.lash("leather_whip")).width(0.1F, 0.035F)
            .summonTag(new WhipDefinition.SummonTag(240, 2, 1.0, 0.05))
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.PLAYER_ATTACK_KNOCKBACK)
            .crackParticle(ParticleTypes.CRIT)
            .build();
    }

    private LeatherWhip() {}
}
