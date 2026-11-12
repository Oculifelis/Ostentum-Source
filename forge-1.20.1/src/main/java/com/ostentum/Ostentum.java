package com.ostentum;


import com.fundamentum.curios.Slots;
import com.fundamentum.hook.Hooks;
import com.ostentum.entity.ModEntities;
import com.ostentum.relic.Relics;
import com.ostentum.whip.OstentumWhips;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Ostentum: a vanilla expansion that shows off Fundamentum. Almost everything here is Fundamentum data
 * (data/ostentum/fundamentum/...): the saddles are mounts, the hooks are hooks, the Harvest and
 * Enchanted moons are moons and the Pillager Siege is a night event. The code only adds what those systems leave to
 * the owning mod: the items, the harvest doubling, the free enchanting, the Glimmer Moon's double experience, the siege
 * waves, the amethyst hook's slow fall, a few element classifiers, the summons' own creatures and behaviours
 * ({@link Minions}, {@code entity}), the Guardian Totem's save, the boss relics ({@code relic}), four whips on
 * Fundamentum's cord whips ({@code whip}), the special weapons ({@code weapon}), and the moon and siege music with its discs ({@link OstentumSounds},
 * {@link MoonDiscs}, {@code client/MoonMusic}).
 */
@Mod(Ostentum.MODID)
public final class Ostentum {
    public static final String MODID = "ostentum";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Summoning saddles, one per vanilla mount (fundamentum/mounts). 1.21.1 has no nautilus or happy ghast, so no saddles for them. */
    public static final List<String> MOUNTS = List.of("horse", "donkey", "mule", "skeleton_horse", "zombie_horse", "camel", "pig",
        "strider", "llama", "dolphin");
    /**
     * Grappling hooks (fundamentum/hooks): amethyst below diamond below netherite, with ender beside netherite, and the
     * Stillwind Hook above them all, from trial chamber vaults only (1.8.1).
     */
    public static final List<String> HOOKS = List.of("amethyst_hook", "diamond_hook", "netherite_hook", "ender_hook", "stillwind_hook");
    /** Summon items (fundamentum/summons), in the Curios summon slot. */
    public static final List<String> SUMMONS = List.of("bone_whistle", "golem_heart", "vex_charm", "allay_chime", "hive_charm", "snow_heart",
        "bat_wing", "bottled_dragonling", "pink_pearl", "creeper_sprout", "guardian_totem");

    static {
        for (String mount : MOUNTS) {
            Props.simple(ITEMS, mount + "_saddle", p -> p.stacksTo(1));
        }
        for (String hook : HOOKS) {
            Props.simple(ITEMS, hook, p -> switch (hook) {
                case "netherite_hook" -> p.stacksTo(1).fireResistant().rarity(Rarity.UNCOMMON);
                case "stillwind_hook" -> p.stacksTo(1).rarity(Rarity.EPIC);
                default -> p.stacksTo(1);
            });
        }
        for (String summon : SUMMONS) {
            Props.simple(ITEMS, summon, p -> summon.equals("bottled_dragonling") || summon.equals("guardian_totem")
                ? p.stacksTo(1).rarity(Rarity.RARE) : p.stacksTo(1));
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.ostentum"))
        .icon(() -> new ItemStack(com.ostentum.weapon.Hearthblade.HEARTHBLADE.get()))
        .displayItems((params, out) -> ITEMS.getEntries().forEach(item -> {
            if (item != com.ostentum.whip.OstentumWhips.END_RIFT_WHIP) out.accept(item.get());     // hidden: see whip.EndRiftWhip
        }))
        .build());

    public Ostentum() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // touch MoonDiscs first: its static fields add the four discs to ITEMS
        MoonDiscs.FUNDAMENTUM.getId();
        // then the weapon classes, which add theirs (1.9.0)
        com.ostentum.weapon.Weapons.init(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        ModEntities.init(modBus);
        Relics.init(modBus);
        OstentumWhips.init(modBus);
        Harvest.init();
        Glimmer.init();
        Minions.init();
        Siege.init();
        OstentumSounds.init(modBus);
        MoonDiscs.init();
        SelfTest.init();
        ElementsSetup.init();
        com.ostentum.music.RecordShelves.init(modBus);      // 1.9.4: record shelves and the Record Pendant
        com.ostentum.music.NowPlaying.init();
        MinecraftForge.EVENT_BUS.addListener(Ostentum::amethystHook);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    /** Players whose amethyst hook held a block last tick. */
    private static final Set<UUID> LATCHED = ConcurrentHashMap.newKeySet();

    /** The amethyst hook's own trick: letting go of it gives three seconds of Slow Falling. */
    private static void amethystHook(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        boolean amethyst = ForgeRegistries.ITEMS.getKey(Slots.equipped(player, Slots.HOOK).getItem()).equals(id("amethyst_hook"));
        if (amethyst && Hooks.latched(player)) {
            LATCHED.add(player.getUUID());
        } else if (LATCHED.remove(player.getUUID()) && amethyst) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, player.getSoundSource(), 1.0F, 1.2F);
        }
    }
}
