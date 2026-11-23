package com.ostentum.whip.more;

import com.ostentum.Props;
import com.fundamentum.whip.WhipCordFx;
import com.fundamentum.whip.WhipDefinition;
import com.fundamentum.whip.WhipHit;
import com.fundamentum.whip.WhipHooks;
import com.ostentum.Ostentum;
import com.ostentum.whip.OstentumWhips;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Five tails of sea water (Fundamentum's water shader), each going for its own hostile within 45 degrees; hits soak
 * and slow. Holding use charges a wave: the tails crash down and a ring of spray throws everything back.
 */
public final class UntamedSeaWhip {
    public static final DeferredItem<Item> ITEM = Props.simple(Ostentum.ITEMS, "untamed_sea_whip",
        p -> p.stacksTo(1).rarity(Rarity.EPIC).enchantable(18));

    static WhipDefinition definition() {
        return WhipDefinition.builder(Ostentum.id("untamed_sea_whip"))
            .tuning(t -> t.length(5.0).damage(5).strands(5).strandSpread(12).maxTargets(5).cooldownTicks(16))
            .homing(45).strandTargets()
            .coiled().texture(OstentumWhips.lash("untamed_sea_whip")).width(0.12F, 0.04F)
            .cordFx(WhipCordFx.WATER)
            .shockwave(0xA060C8F0)
            .slam(new WhipDefinition.Slam(24, 2.0, 4.0, 8.0, 1.6, 0.4, 36))
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.PLAYER_SPLASH_HIGH_SPEED)
            .crackParticle(ParticleTypes.SPLASH)
            .particles((level, strand, segment, along, pos, speed, random) -> {
                if (random.nextFloat() < 0.05F + 0.1F * (float) along) {
                    level.addParticle(ParticleTypes.FALLING_WATER, pos.x, pos.y, pos.z, 0, 0, 0);
                }
            })
            .hooks(new WhipHooks() {
                @Override
                public void landed(WhipHit hit, boolean hurt) {
                    LivingEntity target = hit.living();
                    if (hurt && target != null) {
                        target.clearFire();
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0), hit.player);
                    }
                }

                @Override
                public void slam(ServerPlayer player, ItemStack stack, WhipDefinition whip, Vec3 at, float strength, List<LivingEntity> hit) {
                    if (!(player.level() instanceof ServerLevel level)) {
                        return;
                    }
                    double r = whip.slam.radius() * (0.5 + 0.5 * strength);
                    for (int i = 0; i < 32; i++) {
                        double a = Mth.TWO_PI * i / 32;
                        level.sendParticles(ParticleTypes.SPLASH, at.x + Math.cos(a) * r, at.y + 0.2, at.z + Math.sin(a) * r, 6, 0.2, 0.3, 0.2, 0.1);
                        level.sendParticles(ParticleTypes.BUBBLE_POP, at.x + Math.cos(a) * r * 0.6, at.y + 0.3, at.z + Math.sin(a) * r * 0.6, 2, 0.1, 0.2, 0.1, 0.05);
                    }
                    for (LivingEntity e : hit) {
                        e.clearFire();
                    }
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0F, 0.7F);
                }
            })
            .build();
    }

    private UntamedSeaWhip() {}
}
