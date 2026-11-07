package com.ostentum.client;

import com.ostentum.Ostentum;
import com.ostentum.weapon.ChimeFx;
import com.ostentum.weapon.RecallChime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** While you hold a Recall Chime with a mark in this dimension, a faint column of amethyst light marks the spot (only you see it). */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class ChimeFxClient {
    static final double RANGE = 64.0;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        ItemStack stack = mc.player.getMainHandItem().is(RecallChime.CHIME.get()) ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (!stack.is(RecallChime.CHIME.get())) {
            return;
        }
        CompoundTag mark = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!mark.getStringOr("mark_dim", "").equals(mc.level.dimension().identifier().toString())) {
            return;
        }
        Vec3 at = new Vec3(mark.getIntOr("mark_x", 0) + 0.5, mark.getIntOr("mark_y", 0), mark.getIntOr("mark_z", 0) + 0.5);
        if (at.distanceToSqr(mc.player.position()) < RANGE * RANGE) {
            ChimeFx.marker(mc.level, at, mc.player.tickCount);
        }
    }

    private ChimeFxClient() {}
}
