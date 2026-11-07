package com.ostentum.whip.more;

import com.ostentum.Props;
import com.fundamentum.whip.WhipCordFx;
import com.fundamentum.whip.WhipDefinition;
import com.fundamentum.whip.WhipHit;
import com.fundamentum.whip.WhipHooks;
import com.ostentum.Ostentum;
import com.ostentum.whip.OstentumWhips;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The trial chambers' whip: a cord of breeze air (Fundamentum's wind shader) on a copper handle. The fastest whip by
 * far, light on damage, and every hit blows the target away; a tip hit bursts like a wind charge. Found only in trial
 * chamber vaults, more often in ominous ones ({@code loot_modifiers/breeze_whip_in_vaults}).
 */
public final class BreezeWhip {
    public static final DeferredItem<Item> ITEM = Props.simple(Ostentum.ITEMS, "breeze_whip",
        p -> p.stacksTo(1).rarity(Rarity.RARE).enchantable(16));

    static WhipDefinition definition() {
        return WhipDefinition.builder(Ostentum.id("breeze_whip"))
            .tuning(t -> t.length(5.0).damage(4).windupTicks(2).strokeTicks(3).recoverTicks(4).cooldownTicks(6).knockback(2.6).guide(0.95))
            .coiled().texture(OstentumWhips.lash("breeze_whip")).width(0.1F, 0.035F)
            .cordFx(WhipCordFx.WIND)
            .shockwave(0x90D6ECFF)
            .sounds(SoundEvents.BREEZE_IDLE_GROUND, SoundEvents.BREEZE_SHOOT)
            .crackParticle(ParticleTypes.SMALL_GUST)
            .particles((level, strand, segment, along, pos, speed, random) -> {
                if (random.nextFloat() < 0.03F + 0.06F * (float) along) {
                    level.addParticle(ParticleTypes.SMALL_GUST, pos.x, pos.y, pos.z, 0, 0, 0);
                }
            })
            .hooks(new WhipHooks() {
                @Override
                public void landed(WhipHit hit, boolean hurt) {
                    LivingEntity target = hit.living();
                    if (!hurt || !hit.tip || target == null || !(hit.player.level() instanceof ServerLevel level)) {
                        return;
                    }
                    double resist = 1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                    Vec3 away = target.position().subtract(hit.player.position());
                    away = new Vec3(away.x, 0, away.z).lengthSqr() < 1.0E-4 ? hit.player.getLookAngle() : new Vec3(away.x, 0, away.z).normalize();
                    target.push(away.x * 0.8 * resist, 0.5 * resist, away.z * 0.8 * resist);
                    target.hurtMarked = true;
                    level.sendParticles(ParticleTypes.GUST, hit.contact.x, hit.contact.y, hit.contact.z, 1, 0, 0, 0, 0);
                    level.playSound(null, hit.contact.x, hit.contact.y, hit.contact.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
                }
            })
            .build();
    }

    private BreezeWhip() {}
}
