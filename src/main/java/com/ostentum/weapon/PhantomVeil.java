package com.ostentum.weapon;

import com.fundamentum.curios.Slots;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Phantom Veil, a cloak worn in the Curios back slot (6 phantom membranes, a long Potion of Invisibility, 2 echo
 * shards). In the dark or at night, mobs notice its wearer from half as far away; phantoms never spawn for them; and
 * while they sneak, other players more than {@link #SHIMMER_RANGE} blocks away see only a faint shimmer (drawn on the
 * client, {@code client.WeaponsClient}).
 */
public final class PhantomVeil {
    public static final String SLOT = "back";
    public static final DeferredItem<Item> VEIL = Weapons.item("phantom_veil", Item::new, p -> p.stacksTo(1).rarity(Rarity.RARE));

    public static final double SHIMMER_RANGE = 8.0;

    static void init() {
        NeoForge.EVENT_BUS.addListener(PhantomVeil::seen);
        NeoForge.EVENT_BUS.addListener(PhantomVeil::phantoms);
    }

    public static boolean wears(LivingEntity entity) {
        return entity instanceof Player && Slots.curiosLoaded() && Slots.equipped(entity, SLOT).is(VEIL.get());
    }

    /** In the dark (block or sky light under 8) or at night. */
    public static boolean dark(LivingEntity entity) {
        return entity.level().isDarkOutside() || entity.level().getMaxLocalRawBrightness(entity.blockPosition()) < 8;
    }

    private static void seen(LivingEvent.LivingVisibilityEvent event) {
        if (wears(event.getEntity()) && dark(event.getEntity())) {
            event.modifyVisibility(0.5);
        }
    }

    private static void phantoms(PlayerSpawnPhantomsEvent event) {
        if (wears(event.getEntity())) {
            event.setResult(PlayerSpawnPhantomsEvent.Result.DENY);
        }
    }

    private PhantomVeil() {}
}
