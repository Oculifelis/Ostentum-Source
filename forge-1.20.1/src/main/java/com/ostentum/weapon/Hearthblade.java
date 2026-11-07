package com.ostentum.weapon;

import net.minecraft.core.BlockPos;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeTier;
import net.minecraft.world.item.crafting.RecipeType;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Hearthblade. Brushing suspicious gravel in trail ruins can give a Cold Hearthblade; set on a lit campfire it rekindles
 * over 30 s (a campfire cooking recipe) and pops off. Off a soul campfire it comes out as the blue-flame Soul Hearthblade.
 * Its kills cook their drops (the soul blade's in soul fire, which also crisps one extra). Each kill stores half a food
 * point in the blade, up to {@link #MAX_EMBERS}; sneak + use eats them.
 */
public final class Hearthblade {
    static final Tier MATERIAL = new ForgeTier(2, 800, 7.0F, 2.5F, 14, BlockTags.NEEDS_IRON_TOOL, () -> net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.IRON_INGOT));

    public static final RegistryObject<Item> COLD = Weapons.item("cold_hearthblade", Item::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> HEARTHBLADE = Weapons.item("hearthblade", Blade::new, p -> p.rarity(Rarity.RARE)
        .fireResistant().sword(MATERIAL, 3.0F, -2.4F));
    public static final RegistryObject<Item> SOUL = Weapons.item("soul_hearthblade", Blade::new, p -> p.rarity(Rarity.RARE)
        .fireResistant().sword(MATERIAL, 3.0F, -2.4F));

    /** Half food points a blade holds. */
    public static final int MAX_EMBERS = 10;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(Hearthblade::cookDrops);
        MinecraftForge.EVENT_BUS.addListener(Hearthblade::offSoulCampfire);
    }

    public static boolean isBlade(ItemStack stack) {
        return stack.is(HEARTHBLADE.get()) || stack.is(SOUL.get());
    }

    public static int embers(ItemStack stack) {
        return Weapons.data(stack).getInt("embers");
    }

    static final class Blade extends Item {
        Blade(Properties properties) {
            super(properties);
        }

        /** Sneak + use eats the stored embers: one food point per two. */
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            int embers = embers(stack);
            if (!player.isShiftKeyDown() || embers < 2 || !player.getFoodData().needsFood()) {
                return InteractionResultHolder.pass(player.getItemInHand(hand));
            }
            if (level instanceof ServerLevel server) {
                player.getFoodData().eat(embers / 2, 0.6F);
                Weapons.edit(stack, tag -> tag.putInt("embers", embers % 2));
                server.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8F, 1.0F);
                server.sendParticles(stack.is(SOUL.get()) ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    player.getX(), player.getY(1.0), player.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
                player.displayClientMessage(Component.translatable("item.ostentum.hearthblade.ate", embers / 2), true);
            }
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
    }

    /** A hearthblade kill: every drop with a smelting recipe comes out cooked, and the blade stores an ember. */
    private static void cookDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || event.getSource().getDirectEntity() != player
            || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack blade = player.getMainHandItem();
        if (!isBlade(blade)) {
            return;
        }
        boolean soul = blade.is(SOUL.get());
        boolean extra = false;
        for (ItemEntity drop : event.getDrops()) {
            ItemStack cooked = cooked(level, drop.getItem());
            if (!cooked.isEmpty()) {
                if (soul && !extra) {
                    cooked.grow(1);
                    extra = true;
                }
                drop.setItem(cooked);
            }
        }
        Weapons.edit(blade, tag -> tag.putInt("embers", Math.min(MAX_EMBERS, tag.getInt("embers") + 1)));
        var dead = event.getEntity();
        level.sendParticles(soul ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, dead.getX(), dead.getY(0.5), dead.getZ(),
            12, 0.3, 0.3, 0.3, 0.02);
    }

    /** The cooked form of {@code raw} (smelting recipes), count kept; empty if it does not cook. */
    public static ItemStack cooked(ServerLevel level, ItemStack raw) {
        net.minecraft.world.SimpleContainer input = new net.minecraft.world.SimpleContainer(raw.copyWithCount(1));
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level)
            .map(recipe -> recipe.assemble(input, level.registryAccess()))
            .filter(out -> !out.isEmpty())
            .map(out -> out.copyWithCount(out.getCount() * raw.getCount()))
            .orElse(ItemStack.EMPTY);
    }

    /** A hearthblade popped off a lit soul campfire comes out as the soul blade. */
    private static void offSoulCampfire(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !(event.getLevel() instanceof ServerLevel level)
            || !item.getItem().is(HEARTHBLADE.get())) {
            return;
        }
        BlockPos pos = item.blockPosition();
        for (BlockPos at : new BlockPos[] {pos, pos.below()}) {
            var state = level.getBlockState(at);
            if (state.is(Blocks.SOUL_CAMPFIRE) && state.getValue(CampfireBlock.LIT)) {
                ItemStack soul = com.ostentum.Stacks.transmute(item.getItem(), SOUL.get());
                if (soul.getTag() != null) {
                    soul.getTag().remove("embers");
                }
                item.setItem(soul);
                level.playSound(null, at, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1.0F, 1.0F);
                return;
            }
        }
    }

    private Hearthblade() {}
}
