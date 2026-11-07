package com.ostentum.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The thrown trident's item (1.20.1 keeps it private), loyalty and foil data, which its own constructors set only for vanilla's entity type ({@code weapon.WeaponTrident}). */
@Mixin(ThrownTrident.class)
public interface ThrownTridentAccess {
    @Accessor("ID_LOYALTY")
    static EntityDataAccessor<Byte> ostentum$loyalty() {
        throw new AssertionError();
    }

    @Accessor("tridentItem")
    void ostentum$setTridentItem(ItemStack stack);

    @Accessor("tridentItem")
    ItemStack ostentum$tridentItem();

    @Accessor("ID_FOIL")
    static EntityDataAccessor<Boolean> ostentum$foil() {
        throw new AssertionError();
    }
}
