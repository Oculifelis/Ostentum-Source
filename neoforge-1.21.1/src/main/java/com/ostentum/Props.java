package com.ostentum;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21.1 stand-in for 26.x's item properties builders: {@code enchantable}, {@code repairable} and {@code sword} are
 * properties there, but on 1.21.1 they live on the item. An item made from a {@code Props} is remembered with them
 * ({@code mixin/ItemMixin}), and its enchantment value and repair item are read from here.
 */
public final class Props extends Item.Properties {
    private static final Map<Item, Props> ITEMS = new WeakHashMap<>();

    private int enchantable;
    private @Nullable Predicate<ItemStack> repair;

    /** A plain item registered with properties built by {@code op} (26.x's {@code registerSimpleItem(name, op)}). */
    public static DeferredItem<Item> simple(DeferredRegister.Items items, String name, UnaryOperator<Props> op) {
        return items.registerItem(name, Item::new, op.apply(new Props()));
    }

    /** 26.x's {@code registerItem(name, factory, op)}. */
    public static <I extends Item> DeferredItem<I> item(DeferredRegister.Items items, String name, Function<Item.Properties, ? extends I> factory,
            UnaryOperator<Props> op) {
        return items.registerItem(name, factory, op.apply(new Props()));
    }

    public static void remember(Item item, Item.Properties properties) {
        if (properties instanceof Props props && (props.enchantable > 0 || props.repair != null)) {
            ITEMS.put(item, props);
        }
    }

    /** The enchantment value set with {@link #enchantable}, or -1 when none was. */
    public static int enchantable(Item item) {
        Props props = ITEMS.get(item);
        return props == null || props.enchantable <= 0 ? -1 : props.enchantable;
    }

    public static boolean repairs(Item item, ItemStack with) {
        Props props = ITEMS.get(item);
        return props != null && props.repair != null && props.repair.test(with);
    }

    public Props enchantable(int value) {
        enchantable = value;
        return this;
    }

    public Props repairable(Item item) {
        repair = stack -> stack.is(item);
        return this;
    }

    public Props repairable(TagKey<Item> tag) {
        repair = stack -> stack.is(tag);
        return this;
    }

    /** A sword of {@code tier}: its durability, enchantability, repair item, attack and the sword's mining. */
    public Props sword(Tier tier, float damage, float speed) {
        durability(tier.getUses());
        enchantable(tier.getEnchantmentValue());
        repair = tier.getRepairIngredient();
        attributes(SwordItem.createAttributes(tier, damage, speed));
        component(DataComponents.TOOL, SwordItem.createToolProperties());
        return this;
    }

    public Props pickaxe(Tier tier, float damage, float speed) {
        return digger(tier, BlockTags.MINEABLE_WITH_PICKAXE, damage, speed);
    }

    public Props axe(Tier tier, float damage, float speed) {
        return digger(tier, BlockTags.MINEABLE_WITH_AXE, damage, speed);
    }

    private Props digger(Tier tier, TagKey<Block> mines, float damage, float speed) {
        durability(tier.getUses());
        enchantable(tier.getEnchantmentValue());
        repair = tier.getRepairIngredient();
        attributes(DiggerItem.createAttributes(tier, damage, speed));
        component(DataComponents.TOOL, tier.createToolProperties(mines));
        return this;
    }

    /**
     * 26.x's spear (a lunge-charged kinetic weapon) does not exist on 1.21.1: here it is a plain melee weapon of
     * {@code tier} hitting as hard as its full-speed lunge ({@code damage}·tier bonus + 3), slow (-2.8) and one block
     * longer. The other spear numbers (charge times, lunge speeds) have nothing to drive.
     */
    public Props spear(Tier tier, float... ignored) {
        durability(tier.getUses());
        enchantable(tier.getEnchantmentValue());
        repair = tier.getRepairIngredient();
        attributes(ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 3.0F + tier.getAttackDamageBonus(),
                AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.8F, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(SPEAR_REACH, 1.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .build());
        return this;
    }

    private static final ResourceLocation SPEAR_REACH = ResourceLocation.fromNamespaceAndPath("ostentum", "spear_reach");

    @Override public Props stacksTo(int size) { super.stacksTo(size); return this; }
    @Override public Props durability(int damage) { super.durability(damage); return this; }
    @Override public Props rarity(Rarity rarity) { super.rarity(rarity); return this; }
    @Override public Props fireResistant() { super.fireResistant(); return this; }
    @Override public Props attributes(ItemAttributeModifiers modifiers) { super.attributes(modifiers); return this; }
    @Override public Props jukeboxPlayable(ResourceKey<JukeboxSong> song) { super.jukeboxPlayable(song); return this; }
    @Override public <T> Props component(DataComponentType<T> type, T value) { super.component(type, value); return this; }
}
