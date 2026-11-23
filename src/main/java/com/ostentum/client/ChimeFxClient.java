package com.ostentum.client;

import com.ostentum.Ostentum;
import com.ostentum.weapon.ChimeFx;
import com.ostentum.weapon.RecallChime;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

/** While you hold a Recall Chime with a mark in this dimension, a faint column of amethyst light marks the spot (only you see it). */
@Mod.EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class ChimeFxClient {
    static final double RANGE = 64.0;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        ItemStack stack = mc.player.getMainHandItem().is(RecallChime.CHIME.get()) ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (!stack.is(RecallChime.CHIME.get())) {
            return;
        }
        CompoundTag mark = (stack.getTag() == null ? new CompoundTag() : stack.getTag());
        if (!mark.getString("mark_dim").equals(mc.level.dimension().location().toString())) {
            return;
        }
        Vec3 at = new Vec3(mark.getInt("mark_x") + 0.5, mark.getInt("mark_y"), mark.getInt("mark_z") + 0.5);
        if (at.distanceToSqr(mc.player.position()) < RANGE * RANGE) {
            ChimeFx.marker(mc.level, at, mc.player.tickCount);
        }
    }

    private ChimeFxClient() {}
}
