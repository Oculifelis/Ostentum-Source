package com.ostentum.whip.more;

import com.ostentum.Props;
import com.fundamentum.whip.WhipCordFx;
import com.fundamentum.whip.WhipDefinition;
import com.fundamentum.whip.WhipHit;
import com.fundamentum.whip.WhipHooks;
import com.ostentum.Ostentum;
import com.ostentum.whip.OstentumWhips;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * A whip of pure fire on a blaze-rod handle (Fundamentum's fire shader), trailing flames. Everything it hits burns for
 * four seconds, and it cuts 40% deeper into anything already burning. The snapping tip lights the unlit campfires and
 * candles it strikes.
 */
public final class BlazeWhip {
    public static final DeferredItem<Item> ITEM = Props.simple(Ostentum.ITEMS, "blaze_whip",
        p -> p.stacksTo(1).rarity(Rarity.RARE).fireResistant().enchantable(16));
    static final double BURNING_BONUS = 1.4;

    static WhipDefinition definition() {
        return WhipDefinition.builder(Ostentum.id("blaze_whip"))
            .tuning(t -> t.length(5.0).damage(7).cooldownTicks(15))
            .coiled().texture(OstentumWhips.lash("blaze_whip")).width(0.15F, 0.05F)
            .cordFx(WhipCordFx.FIRE)
            .shockwave(0xC0FF8A1E)
            .sounds(SoundEvents.BLAZE_SHOOT, SoundEvents.FIRECHARGE_USE)
            .crackParticle(ParticleTypes.LAVA)
            .particles((level, strand, segment, along, pos, speed, random) -> {
                if (random.nextFloat() < 0.12F + 0.25F * (float) along) {
                    level.addParticle(random.nextInt(4) == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME, pos.x, pos.y, pos.z,
                        (random.nextDouble() - 0.5) * 0.02, 0.02 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.02);
                }
            })
            .hooks(new WhipHooks() {
                @Override
                public void cordHit(WhipHit hit) {
                    if (hit.target.isOnFire()) {
                        hit.damage *= (float) BURNING_BONUS;
                    }
                }

                @Override
                public void landed(WhipHit hit, boolean hurt) {
                    LivingEntity target = hit.living();
                    if (hurt && target != null && !target.fireImmune()) {
                        target.igniteForSeconds(4);
                        if (hit.player.level() instanceof ServerLevel level) {
                            level.sendParticles(ParticleTypes.FLAME, hit.contact.x, hit.contact.y, hit.contact.z, 8, 0.2, 0.3, 0.2, 0.02);
                        }
                    }
                }

                @Override
                public void struckBlock(ServerPlayer player, ItemStack stack, WhipDefinition whip, BlockPos pos, Vec3 tip) {
                    BlockState state = player.level().getBlockState(pos);
                    if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)
                        && !(state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED))) {
                        player.level().setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, true));
                        player.level().playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                }
            })
            .build();
    }

    private BlazeWhip() {}
}
