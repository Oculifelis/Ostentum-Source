package com.ostentum.client;

import com.ostentum.Ostentum;
import com.ostentum.weapon.SeaFx;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Each client tick: the Riptide spiral round any player spinning with an Ostentum trident, and the held shimmer. */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class SeaFxClient {
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        for (Player p : mc.level.players()) {
            if (p.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 48 * 48) {
                continue;
            }
            SeaFx.Style held = SeaFx.Style.of(p.getMainHandItem());
            SeaFx.Style off = SeaFx.Style.of(p.getOffhandItem());
            if (p.isAutoSpinAttack() && (held != null || off != null)) {
                SeaFx.riptide(p, held != null ? held : off);
            } else if (held != null && !(p == mc.player && mc.options.getCameraType().isFirstPerson())) {
                SeaFx.held(p, held);
            }
        }
    }

    private SeaFxClient() {}
}
