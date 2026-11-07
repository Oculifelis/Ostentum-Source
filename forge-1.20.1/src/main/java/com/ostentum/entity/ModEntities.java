package com.ostentum.entity;

import com.ostentum.Ostentum;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/** Ostentum's own creatures: the summons vanilla has no fitting mob for, and the puff they shoot. */
public final class ModEntities {
    private static final com.ostentum.Entities ENTITIES = new com.ostentum.Entities(Ostentum.MODID);

    public static final RegistryObject<EntityType<Dragonling>> DRAGONLING = ENTITIES.registerEntityType("dragonling",
        Dragonling::new, MobCategory.MISC, b -> b.sized(0.7F, 0.6F).clientTrackingRange(8).noSummon());
    public static final RegistryObject<EntityType<SkyDolphin>> SKY_DOLPHIN = ENTITIES.registerEntityType("sky_dolphin",
        SkyDolphin::new, MobCategory.MISC, b -> b.sized(0.6F, 0.4F).clientTrackingRange(8).noSummon());
    public static final RegistryObject<EntityType<BiteBat>> BITE_BAT = ENTITIES.registerEntityType("bite_bat",
        BiteBat::new, MobCategory.MISC, b -> b.sized(0.5F, 0.9F).clientTrackingRange(8).noSummon());
    public static final RegistryObject<EntityType<GuardianTotem>> GUARDIAN_TOTEM = ENTITIES.registerEntityType("guardian_totem",
        GuardianTotem::new, MobCategory.MISC, b -> b.sized(0.35F, 0.4F).clientTrackingRange(8).noSummon());
    public static final RegistryObject<EntityType<Puff>> PUFF = ENTITIES.registerEntityType("puff",
        Puff::new, MobCategory.MISC, b -> b.sized(0.3F, 0.3F).clientTrackingRange(4).updateInterval(10).noSummon().noSave());

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(DRAGONLING.get(), FlyingMinion.attributes(20.0, 4.0).build());
            e.put(SKY_DOLPHIN.get(), FlyingMinion.attributes(16.0, 3.0).build());
            e.put(BITE_BAT.get(), Bat.createAttributes().add(Attributes.ATTACK_DAMAGE, 2.0).build());
            e.put(GUARDIAN_TOTEM.get(), GuardianTotem.attributes().build());
        });
    }

    private ModEntities() {}
}
