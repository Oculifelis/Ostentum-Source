package com.ostentum.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The thrown trident's loyalty and foil data, which its own constructors set only for vanilla's entity type ({@code weapon.WeaponTrident}). */
@Mixin(ThrownTrident.class)
public interface ThrownTridentAccess {
    @Accessor("ID_LOYALTY")
    static EntityDataAccessor<Byte> ostentum$loyalty() {
        throw new AssertionError();
    }

    @Accessor("ID_FOIL")
    static EntityDataAccessor<Boolean> ostentum$foil() {
        throw new AssertionError();
    }
}
