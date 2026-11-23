package com.ostentum.client;

import com.ostentum.Ostentum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Dev client only (-Dostentum.devshot): saves a screenshot of the title screen, then closes the game. */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class DevShot {
    private static final boolean ON = Boolean.getBoolean("ostentum.devshot");
    private static int ticks;

    private DevShot() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ON || SteerTest.ON || MusicTest.ON || WhipShot.ON || WeaponShot.ON || ThrowShot.ON || TooltipShot.ON || VeilShot.ON || NowPlayingShot.ON || ZipShot.ON || !(mc.gui.screen() instanceof TitleScreen)) {
            return;
        }
        if (++ticks == 1) {
            System.out.println("OSTENTUM devshot: title screen is up");
        }
        if (ticks == 100) {
            Screenshot.grab(mc.gameDirectory, "ostentum-title.png", mc.gameRenderer.mainRenderTarget(), 1, msg -> System.out.println("OSTENTUM devshot saved"));
        } else if (ticks == 140) {
            mc.stop();
        }
    }
}
