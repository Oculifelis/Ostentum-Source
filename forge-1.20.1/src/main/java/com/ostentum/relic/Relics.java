package com.ostentum.relic;

import com.ostentum.Ostentum;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Terraria-style boss relics for the vanilla bosses: the boss's gold likeness floating over Ars Moriendi's gold
 * pedestal with a diamond set in its front (Oculifelis's pick on the relic page). The Wither's, the Elder Guardian's and the Warden's
 * (1.9.3) are one block; the Ender Dragon, vanilla's final boss, gets a 3x3 relic whose eight outer blocks are invisible
 * {@link RelicFrameBlock}s. Every kill gives one: the Wither's, the Elder Guardian's and the Warden's fall with their drops, the
 * Dragon's goes straight to the player who killed it (it dies over the exit portal and the void, where a dropped item
 * would be lost). Drawn by client.RelicRenderer.
 */
public final class Relics {
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Ostentum.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Ostentum.MODID);

    public static final RegistryObject<RelicBlock> WITHER_RELIC = BLOCKS.register("wither_relic", () -> new RelicBlock(stone(BlockBehaviour.Properties.of()), false));
    public static final RegistryObject<RelicBlock> ELDER_GUARDIAN_RELIC = BLOCKS.register("elder_guardian_relic",
        () -> new RelicBlock(stone(BlockBehaviour.Properties.of()), false));
    public static final RegistryObject<RelicBlock> WARDEN_RELIC = BLOCKS.register("warden_relic", () -> new RelicBlock(stone(BlockBehaviour.Properties.of()), false));
    public static final RegistryObject<RelicBlock> DRAGON_RELIC = BLOCKS.register("dragon_relic", () -> new RelicBlock(stone(BlockBehaviour.Properties.of()), true));
    public static final RegistryObject<RelicFrameBlock> DRAGON_RELIC_FRAME = BLOCKS.register("dragon_relic_frame",
        () -> new RelicFrameBlock(stone(BlockBehaviour.Properties.of()).noLootTable()));

    public static final RegistryObject<BlockItem> WITHER_RELIC_ITEM = Ostentum.ITEMS.register("wither_relic", () -> new BlockItem(WITHER_RELIC.get(), new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final RegistryObject<BlockItem> ELDER_GUARDIAN_RELIC_ITEM = Ostentum.ITEMS.register("elder_guardian_relic", () -> new BlockItem(ELDER_GUARDIAN_RELIC.get(), new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final RegistryObject<BlockItem> WARDEN_RELIC_ITEM = Ostentum.ITEMS.register("warden_relic", () -> new BlockItem(WARDEN_RELIC.get(), new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final RegistryObject<BlockItem> DRAGON_RELIC_ITEM = Ostentum.ITEMS.register("dragon_relic", () -> new BlockItem(DRAGON_RELIC.get(), new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    public static final RegistryObject<BlockEntityType<RelicBlockEntity>> RELIC = BLOCK_ENTITIES.register("relic",
        () -> BlockEntityType.Builder.of(RelicBlockEntity::new, WITHER_RELIC.get(), ELDER_GUARDIAN_RELIC.get(), WARDEN_RELIC.get(), DRAGON_RELIC.get()).build(null));

    private Relics() {
    }

    /** A gold pedestal whose diamond glows a little; blast-proof and immovable, so a 3x3 is never split apart. */
    private static BlockBehaviour.Properties stone(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.GOLD).strength(3.0F, 1200.0F).sound(SoundType.METAL).noOcclusion()
            .lightLevel(s -> 6).pushReaction(PushReaction.BLOCK);
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(Relics::witherDrops);
        MinecraftForge.EVENT_BUS.addListener(Relics::dragonDeath);
    }

    /** The Wither's, the Elder Guardian's and the Warden's relics fall with their drops. */
    private static void witherDrops(LivingDropsEvent event) {
        var boss = event.getEntity();
        ItemStack relic = boss.getType() == EntityType.WITHER ? new ItemStack(WITHER_RELIC_ITEM.get())
            : boss.getType() == EntityType.ELDER_GUARDIAN ? new ItemStack(ELDER_GUARDIAN_RELIC_ITEM.get())
            : boss.getType() == EntityType.WARDEN ? new ItemStack(WARDEN_RELIC_ITEM.get()) : ItemStack.EMPTY;
        if (!relic.isEmpty()) {
            event.getDrops().add(new ItemEntity(boss.level(), boss.getX(), boss.getY(), boss.getZ(), relic));
        }
    }

    /** Fires once, on the killing blow (the death flight comes after). */
    private static void dragonDeath(LivingDeathEvent event) {
        var dragon = event.getEntity();
        if (dragon.getType() != EntityType.ENDER_DRAGON || dragon.level().isClientSide()) {
            return;
        }
        ItemStack relic = new ItemStack(DRAGON_RELIC_ITEM.get());
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            if (!player.getInventory().add(relic)) {
                player.drop(relic, false);
            }
        } else {
            dragon.spawnAtLocation(relic);
        }
    }
}
