package com.ostentum.weapon;

import com.fundamentum.curios.Slots;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Phantom Veil, a cloak worn in the Curios back slot (6 phantom membranes, a long Potion of Invisibility, 2 echo
 * shards). In the dark or at night, mobs notice its wearer from half as far away; phantoms never spawn for them; and
 * while they sneak, other players more than {@link #SHIMMER_RANGE} blocks away see only a faint shimmer (drawn on the
 * client, {@code client.WeaponsClient}).
 */
public final class PhantomVeil {
    public static final String SLOT = "back";
    public static final RegistryObject<Item> VEIL = Weapons.item("phantom_veil", Item::new, p -> p.stacksTo(1).rarity(Rarity.RARE));

    public static final double SHIMMER_RANGE = 8.0;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(PhantomVeil::seen);
        MinecraftForge.EVENT_BUS.addListener(PhantomVeil::phantoms);
    }

    public static boolean wears(LivingEntity entity) {
        return entity instanceof Player && Slots.curiosLoaded() && Slots.equipped(entity, SLOT).is(VEIL.get());
    }

    /** In the dark (block or sky light under 8) or at night. */
    public static boolean dark(LivingEntity entity) {
        return (!entity.level().dimensionType().hasFixedTime() && !entity.level().isDay()) || entity.level().getMaxLocalRawBrightness(entity.blockPosition()) < 8;
    }

    private static void seen(LivingEvent.LivingVisibilityEvent event) {
        if (wears(event.getEntity()) && dark(event.getEntity())) {
            event.modifyVisibility(0.5);
        }
    }

    /**
     * 1.20.1 has no phantom spawn event per player: a natural phantom spawn is cancelled when the player nearest it (the
     * one the spawner picked, 20 to 34 blocks below) wears the veil.
     */
    private static void phantoms(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Phantom && event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.NATURAL) {
            Player nearest = event.getLevel().getLevel().getNearestPlayer(event.getX(), event.getY(), event.getZ(), 48.0, false);
            if (nearest != null && wears(nearest)) {
                event.setSpawnCancelled(true);
            }
        }
    }

    private PhantomVeil() {}
}
