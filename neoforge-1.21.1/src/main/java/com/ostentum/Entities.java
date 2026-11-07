package com.ostentum;

import java.util.function.UnaryOperator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 1.21.1 stand-in for 26.x's {@code DeferredRegister.Entities}: {@code registerEntityType(name, factory, category, builder)}. */
public final class Entities {
    private final String modid;
    private final DeferredRegister<EntityType<?>> register;

    public Entities(String modid) {
        this.modid = modid;
        this.register = DeferredRegister.create(Registries.ENTITY_TYPE, modid);
    }

    public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name, EntityType.EntityFactory<E> factory,
            MobCategory category, UnaryOperator<EntityType.Builder<E>> builder) {
        return register.register(name, () -> builder.apply(EntityType.Builder.of(factory, category)).build(modid + ":" + name));
    }

    public void register(IEventBus bus) {
        register.register(bus);
    }
}
