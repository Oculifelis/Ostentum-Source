package com.ostentum.client;

import com.fundamentum.moon.MoonsClient;
import com.ostentum.Enchanted;

/** Client side only: the moon this client sees. Only called from client-side code paths. */
public final class ClientMoon {
    private ClientMoon() {}

    public static boolean enchanted() {
        return Enchanted.MOON.equals(MoonsClient.moon());
    }
}
