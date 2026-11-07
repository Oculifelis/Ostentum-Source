package com.ostentum.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/**
 * A weapon in a loot table with {@code chance} (data in loot_modifiers/, which name the table). With {@code replace} it
 * takes the place of everything the table rolled: brushing suspicious blocks keeps only one item, so an added one
 * would be lost.
 */
public final class WeaponLoot extends LootModifier {
    public static final Codec<WeaponLoot> CODEC = RecordCodecBuilder.create(i -> codecStart(i)
        .and(BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(m -> m.item))
        .and(Codec.floatRange(0, 1).fieldOf("chance").forGetter(m -> m.chance))
        .and(Codec.BOOL.optionalFieldOf("replace", false).forGetter(m -> m.replace))
        .apply(i, WeaponLoot::new));

    private final Item item;
    private final float chance;
    private final boolean replace;

    WeaponLoot(LootItemCondition[] conditions, Item item, float chance, boolean replace) {
        super(conditions);
        this.item = item;
        this.chance = chance;
        this.replace = replace;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (context.getRandom().nextFloat() < chance) {
            if (replace) {
                loot.clear();
            }
            loot.add(new ItemStack(item));
        }
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
