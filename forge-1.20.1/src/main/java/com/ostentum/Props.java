package com.ostentum;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * 1.20.1 stand-in for 26.x's item properties builders: {@code enchantable}, {@code repairable}, {@code sword},
 * {@code pickaxe}, {@code axe}, attributes and the tool component are properties there, but on 1.20.1 they live on the
 * item class. An item made from a {@code Props} is remembered with them ({@code mixin/ItemMixin}), and its enchantment
 * value, repair item, main-hand attributes, mining speed and correct-tool check are read from here.
 */
public final class Props extends Item.Properties {
    private static final Map<Item, Props> ITEMS = new WeakHashMap<>();
    /** Vanilla's own ids for a weapon's base attack damage and speed (Item.BASE_ATTACK_DAMAGE_UUID / _SPEED_UUID). */
    static final UUID BASE_ATTACK_DAMAGE = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    static final UUID BASE_ATTACK_SPEED = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");
    private static final UUID SPEAR_REACH = UUID.fromString("2b4f8a1e-5f0c-4b8e-9d55-0e0f7a3d6c11");

    private int enchantable;
    private @Nullable Predicate<ItemStack> repair;
    private @Nullable Supplier<Multimap<Attribute, AttributeModifier>> attributes;
    private @Nullable Multimap<Attribute, AttributeModifier> built;
    private @Nullable Tier tier;
    private @Nullable TagKey<Block> mines;
    private boolean sword;

    /** A plain item registered with properties built by {@code op} (26.x's {@code registerSimpleItem(name, op)}). */
    public static RegistryObject<Item> simple(DeferredRegister<Item> items, String name, UnaryOperator<Props> op) {
        Props props = op.apply(new Props());
        return items.register(name, () -> new Item(props));
    }

    /** 26.x's {@code registerItem(name, factory, op)}. */
    public static <I extends Item> RegistryObject<I> item(DeferredRegister<Item> items, String name, Function<Item.Properties, ? extends I> factory,
            UnaryOperator<Props> op) {
        Props props = op.apply(new Props());
        return items.register(name, () -> factory.apply(props));
    }

    public static void remember(Item item, Item.Properties properties) {
        if (properties instanceof Props props && (props.enchantable > 0 || props.repair != null || props.attributes != null || props.tier != null)) {
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

    /** The main-hand attributes set by {@link #sword}, {@link #pickaxe}, {@link #axe}, {@link #spear} or {@link #attack}; null when none were. */
    public static @Nullable Multimap<Attribute, AttributeModifier> attributes(Item item) {
        Props props = ITEMS.get(item);
        if (props == null || props.attributes == null) {
            return null;
        }
        if (props.built == null) {
            props.built = props.attributes.get();
        }
        return props.built;
    }

    /** The mining speed of a tool made with {@link #sword}, {@link #pickaxe} or {@link #axe} on {@code state}; -1 for other items. */
    public static float destroySpeed(Item item, BlockState state) {
        Props props = ITEMS.get(item);
        if (props == null || props.tier == null) {
            return -1.0F;
        }
        if (props.sword) {
            return state.is(Blocks.COBWEB) ? 15.0F : state.is(BlockTags.SWORD_EFFICIENT) ? 1.5F : 1.0F;
        }
        return props.mines != null && state.is(props.mines) ? props.tier.getSpeed() : 1.0F;
    }

    /** Whether a tool made with {@link #sword}, {@link #pickaxe} or {@link #axe} gets drops from {@code state}; null for other items. */
    public static @Nullable Boolean correctTool(Item item, BlockState state) {
        Props props = ITEMS.get(item);
        if (props == null || props.tier == null) {
            return null;
        }
        if (props.sword) {
            return state.is(Blocks.COBWEB);
        }
        return props.mines != null && state.is(props.mines) && TierSortingRegistry.isCorrectTierForDrops(props.tier, state);
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

    /** Main-hand attack damage (added to the player's 1) and attack speed (added to 4), as vanilla weapons give. */
    public Props attack(float damage, float speed) {
        attributes = () -> ImmutableMultimap.<Attribute, AttributeModifier>builder()
            .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE, "Weapon modifier", damage, AttributeModifier.Operation.ADDITION))
            .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED, "Weapon modifier", speed, AttributeModifier.Operation.ADDITION))
            .build();
        return this;
    }

    /** A sword of {@code tier}: its durability, enchantability, repair item, attack and the sword's mining. */
    public Props sword(Tier tier, float damage, float speed) {
        durability(tier.getUses());
        enchantable(tier.getEnchantmentValue());
        repair = tier.getRepairIngredient();
        attack(damage + tier.getAttackDamageBonus(), speed);
        this.tier = tier;
        this.sword = true;
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
        attack(damage + tier.getAttackDamageBonus(), speed);
        this.tier = tier;
        this.mines = mines;
        return this;
    }

    /**
     * 26.x's spear (a lunge-charged kinetic weapon) does not exist on 1.20.1: here it is a plain melee weapon of
     * {@code tier} hitting as hard as its full-speed lunge ({@code damage}·tier bonus + 3), slow (-2.8) and one block
     * longer (Forge's entity reach). The other spear numbers (charge times, lunge speeds) have nothing to drive.
     */
    public Props spear(Tier tier, float... ignored) {
        durability(tier.getUses());
        enchantable(tier.getEnchantmentValue());
        repair = tier.getRepairIngredient();
        float damage = 3.0F + tier.getAttackDamageBonus();
        attributes = () -> ImmutableMultimap.<Attribute, AttributeModifier>builder()
            .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE, "Weapon modifier", damage, AttributeModifier.Operation.ADDITION))
            .put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED, "Weapon modifier", -2.8F, AttributeModifier.Operation.ADDITION))
            .put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(SPEAR_REACH, "Spear reach", 1.0, AttributeModifier.Operation.ADDITION))
            .build();
        return this;
    }

    @Override public Props stacksTo(int size) { super.stacksTo(size); return this; }
    @Override public Props durability(int damage) { super.durability(damage); return this; }
    @Override public Props rarity(Rarity rarity) { super.rarity(rarity); return this; }
    @Override public Props fireResistant() { super.fireResistant(); return this; }
}
