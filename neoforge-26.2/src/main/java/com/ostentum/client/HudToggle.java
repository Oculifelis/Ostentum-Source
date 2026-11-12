package com.ostentum.client;

import net.minecraft.client.Minecraft;

/** options.hideGui is gone on 26.2+: the HUD is hidden with gui.hud.toggle(). Used by the dev shot clients. */
public final class HudToggle {
    private HudToggle() {
    }

    public static void hide(Minecraft mc, boolean hidden) {
        if (mc.gui.hud.isHidden() != hidden) {
            mc.gui.hud.toggle();
        }
    }
}
