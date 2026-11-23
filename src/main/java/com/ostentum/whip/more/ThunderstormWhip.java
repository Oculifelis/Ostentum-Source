package com.ostentum.whip.more;

import com.fundamentum.whip.WhipCordFx;
import com.fundamentum.whip.WhipDefinition;
import com.fundamentum.whip.WhipFx;
import com.fundamentum.whip.WhipHit;
import com.fundamentum.whip.WhipHooks;
import com.ostentum.Ostentum;
import com.ostentum.whip.OstentumWhips;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * A storm cord drawn with Fundamentum's lightning shader. A tip hit arcs on to up to five more hostiles, each within
 * five blocks of the last and in its sight (an arc never jumps through a wall), hurting each a little less and holding
 * it still for a second. Not crafted: lightning that strikes a dropped Golden Heavy Whip charges it into this one.
 */
public final class ThunderstormWhip {
    public static final DeferredItem<Item> ITEM = Ostentum.ITEMS.registerSimpleItem("thunderstorm_whip",
        p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant().enchantable(15));
    static final int ARC = 0xFFE6F0FF;
    /** How many more hostiles a tip hit arcs on to (Oculifelis, 2026-11-04: yes to more hops; was three). */
    static final int HOPS = 5;
    static final double HOP_RANGE = 5.0;

    static WhipDefinition definition() {
        return WhipDefinition.builder(Ostentum.id("thunderstorm_whip"))
            .tuning(t -> t.length(5.5).damage(8).cooldownTicks(18))
            .coiled().texture(OstentumWhips.lash("thunderstorm_whip")).width(0.11F, 0.04F)
            .cordFx(WhipCordFx.LIGHTNING)
            .shockwave(0xC0BFD8FF)
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.TRIDENT_THUNDER.value())
            .crackParticle(ParticleTypes.ELECTRIC_SPARK)
            .hooks(new WhipHooks() {
                @Override
                public void landed(WhipHit hit, boolean hurt) {
                    LivingEntity first = hit.living();
                    if (!hurt || !hit.tip || first == null || !(hit.player.level() instanceof ServerLevel level)) {
                        return;
                    }
                    List<LivingEntity> chain = new ArrayList<>(List.of(first));
                    first.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 4), hit.player);
                    LivingEntity from = first;
                    float damage = 5.0F;
                    for (int hop = 0; hop < HOPS; hop++) {
                        LivingEntity next = null;
                        for (LivingEntity e : WhipFx.hostilesNear(hit.player, from.getBoundingBox().getCenter(), HOP_RANGE)) {
                            if (!chain.contains(e) && inSight(level, from, e)) {
                                next = e;
                                break;
                            }
                        }
                        if (next == null) {
                            break;
                        }
                        Vec3 a = from.getBoundingBox().getCenter(), b = next.getBoundingBox().getCenter();
                        WhipFx.beam(level, a, b, ARC, 0.1F, 6, true);
                        if (next.hurtServer(level, level.damageSources().lightningBolt(), damage)) {
                            next.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 4), hit.player);
                        }
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, 8, 0.2, 0.3, 0.2, 0.1);
                        chain.add(next);
                        from = next;
                        damage = Math.max(2.0F, damage - 0.75F);
                    }
                    level.playSound(null, first.getX(), first.getY(), first.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5F, 1.4F);
                }
            })
            .build();
    }

    /** An arc from one creature to the next needs a clear line between their middles (no block in the way). */
    public static boolean inSight(ServerLevel level, LivingEntity from, LivingEntity to) {
        Vec3 a = from.getBoundingBox().getCenter(), b = to.getBoundingBox().getCenter();
        return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, from)).getType() == HitResult.Type.MISS;
    }

    /** Lightning on a dropped Golden Heavy Whip charges it (the item is fire resistant, so it waits for the strike). */
    static void init() {
        NeoForge.EVENT_BUS.addListener((EntityStruckByLightningEvent e) -> {
            if (e.getEntity() instanceof ItemEntity item && item.getItem().is(GoldenHeavyWhip.ITEM.get())) {
                ItemStack charged = item.getItem().transmuteCopy(ITEM.get());
                item.setItem(charged);
                e.setCanceled(true);
            }
        });
    }

    private ThunderstormWhip() {}
}
