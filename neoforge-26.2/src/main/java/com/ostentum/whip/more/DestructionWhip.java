package com.ostentum.whip.more;

import com.fundamentum.whip.WhipCordFx;
import com.fundamentum.whip.WhipDefinition;
import com.fundamentum.whip.WhipFx;
import com.fundamentum.whip.WhipHooks;
import com.fundamentum.whip.Whips;
import com.ostentum.Ostentum;
import com.ostentum.whip.OstentumWhips;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Netherite cord with burning cracks, drawn with Fundamentum's energy shader: when it cracks, four points along the
 * cord each fire a laser at a different hostile within eight blocks.
 */
public final class DestructionWhip {
    public static final DeferredItem<Item> ITEM = Ostentum.ITEMS.registerSimpleItem("destruction_whip",
        p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant().enchantable(15));
    static final int RED = 0xFFFF4A1C;
    static final float LASER_DAMAGE = 5.0F;

    static WhipDefinition definition() {
        return WhipDefinition.builder(Ostentum.id("destruction_whip"))
            .tuning(t -> t.length(5.5).damage(9).cooldownTicks(20))
            .coiled().texture(OstentumWhips.lash("destruction_whip")).width(0.12F, 0.05F)
            .cordFx(WhipCordFx.ENERGY)
            .shockwave(0xC0FF4A1C)
            .sounds(SoundEvents.PLAYER_ATTACK_SWEEP, SoundEvents.BLAZE_SHOOT)
            .crackParticle(ParticleTypes.FLAME)
            .hooks(new WhipHooks() {
                @Override
                public void crack(ServerPlayer player, ItemStack stack, WhipDefinition whip, Vec3 tip) {
                    Vec3 hand = Whips.hand(player, 1.0F);
                    List<LivingEntity> taken = new ArrayList<>();
                    for (int i = 1; i <= 4; i++) {
                        Vec3 from = hand.lerp(tip, i / 4.0);
                        LivingEntity target = null;
                        for (LivingEntity e : WhipFx.hostilesNear(player, from, 8)) {
                            if (!taken.contains(e) && player.level().clip(new net.minecraft.world.level.ClipContext(from,
                                e.getBoundingBox().getCenter(), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                                target = e;
                                break;
                            }
                        }
                        if (target == null) {
                            continue;
                        }
                        taken.add(target);
                        WhipFx.beam(player.level(), from, target.getBoundingBox().getCenter(), RED, 0.14F, 6, false);
                        if (target.hurtServer(player.level(), whip.damage.make(player, stack), LASER_DAMAGE)) {
                            target.igniteForSeconds(2);
                        }
                    }
                    if (!taken.isEmpty()) {
                        player.level().playSound(null, tip.x, tip.y, tip.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9F, 1.8F);
                    }
                }
            })
            .build();
    }

    private DestructionWhip() {}
}
