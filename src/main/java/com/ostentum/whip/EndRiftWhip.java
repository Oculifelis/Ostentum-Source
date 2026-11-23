package com.ostentum.whip;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;

/**
 * The End Rift Whip's only way in (1.9.3): an Ender Whip thrown into an End portal comes back up as the End Rift Whip,
 * keeping its enchantments, damage and name. Done here rather than as a Fundamentum portal recipe so no recipe viewer
 * lists it; the item itself is out of the creative tab and in {@code #c:hidden_from_recipe_viewers}.
 */
public final class EndRiftWhip {
    private EndRiftWhip() { }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EndRiftWhip::travel);
    }

    private static void travel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !(item.level() instanceof ServerLevel level)) return;
        if (event.getDimension() != Level.END || !item.getItem().is(OstentumWhips.ENDER_WHIP.get()) || !inEndPortal(level, item)) return;
        event.setCanceled(true);
        item.setItem(awaken(item.getItem()));
        item.setPortalCooldown();
        item.setDeltaMovement(0, 0.45, 0);
        item.setPickUpDelay(10);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, item.getX(), item.getY() + 0.2, item.getZ(), 40, 0.25, 0.25, 0.25, 0.08);
        level.playSound(null, item.getX(), item.getY(), item.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.5F, 1.6F);
    }

    /** The End Rift Whip with the Ender Whip's components. */
    public static ItemStack awaken(ItemStack ender) {
        return ender.transmuteCopy(OstentumWhips.END_RIFT_WHIP.get(), ender.getCount());
    }

    private static boolean inEndPortal(ServerLevel level, ItemEntity item) {
        AABB box = item.getBoundingBox().inflate(0.1);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (level.getBlockState(pos).is(Blocks.END_PORTAL)) return true;
        return false;
    }
}
