package com.ostentum.weapon;

import com.mojang.math.Transformation;
import com.ostentum.mixin.DisplayAccess;
import com.ostentum.mixin.ItemDisplayAccess;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.common.ForgeTier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pathfinder's Glaive (iron + compass): an iron spear that marks a waypoint. Use on the top of a block plants a copy of
 * it there; the planted glaive glows through walls, a column of light rises from it, and while you hold the glaive the
 * distance shows above the hotbar. Use again, from any range, pulls it back. The waypoint lives on the glaive's stack.
 */
public final class PathfindersGlaive {
    public static final RegistryObject<Item> GLAIVE = Weapons.item("pathfinders_glaive", Glaive::new, p -> p
        .spear(Tiers.IRON, 0.95F, 0.95F, 0.6F, 2.5F, 11.0F, 6.75F, 5.1F, 11.25F, 4.6F));

    static final String TAG = "ostentum_glaive";
    /** Planted glaives recalled while their chunk was unloaded: removed when they load again. */
    private static final Set<UUID> ORPHANS = ConcurrentHashMap.newKeySet();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(PathfindersGlaive::held);
        MinecraftForge.EVENT_BUS.addListener(PathfindersGlaive::orphan);
    }

    static final class Glaive extends Item {
        Glaive(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            ItemStack stack = context.getItemInHand();
            if (planted(stack)) {
                return recall(context.getLevel(), context.getPlayer(), stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            if (context.getClickedFace() != Direction.UP) {
                return InteractionResult.PASS;
            }
            if (context.getLevel() instanceof ServerLevel level && context.getPlayer() != null) {
                plant(level, context.getPlayer(), stack, context.getClickedPos().above());
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (planted(stack)) {
                recall(level, player, stack);
                return InteractionResultHolder.success(player.getItemInHand(hand));
            }
            return super.use(level, player, hand);
        }
    }

    public static boolean planted(ItemStack stack) {
        return Weapons.data(stack).contains("waypoint_dim");
    }

    /** Plants a glowing copy of the glaive at {@code at} and writes the waypoint onto {@code stack}. */
    public static void plant(ServerLevel level, Player player, ItemStack stack, BlockPos at) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        ((ItemDisplayAccess) display).ostentum$setItemStack(stack.copyWithCount(1));
        // the icon runs butt (bottom left) to tip (top right): turn it tip down and sink it a little
        ((DisplayAccess) display).ostentum$setTransformation(new Transformation(new Vector3f(0.0F, 0.55F, 0.0F),
            new Quaternionf().rotateZ((float) Math.toRadians(-135.0)), new Vector3f(1.4F, 1.4F, 1.4F), null));
        ((DisplayAccess) display).ostentum$setGlowColorOverride(0xFFE0B040);
        display.setGlowingTag(true);
        display.addTag(TAG);
        display.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        level.addFreshEntity(display);
        UUID id = display.getUUID();
        Weapons.edit(stack, tag -> {
            tag.putString("waypoint_dim", level.dimension().location().toString());
            tag.putInt("waypoint_x", at.getX());
            tag.putInt("waypoint_y", at.getY());
            tag.putInt("waypoint_z", at.getZ());
            tag.putString("waypoint_id", id.toString());
        });
        level.playSound(null, at, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /** Pulls the planted glaive back, from any range or dimension; false if nothing was planted. */
    public static boolean recall(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel here)) {
            return planted(stack);
        }
        CompoundTag tag = Weapons.data(stack);
        if (!tag.contains("waypoint_dim")) {
            return false;
        }
        UUID id = UUID.fromString((tag.contains("waypoint_id") ? tag.getString("waypoint_id") : new UUID(0, 0).toString()));
        ServerLevel there = here.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation((tag.contains("waypoint_dim") ? tag.getString("waypoint_dim") : "minecraft:overworld"))));
        Entity planted = there == null ? null : there.getEntity(id);
        if (planted != null) {
            planted.discard();
        } else {
            ORPHANS.add(id);
        }
        Weapons.edit(stack, t -> {
            for (String key : new String[] {"waypoint_dim", "waypoint_x", "waypoint_y", "waypoint_z", "waypoint_id"}) {
                t.remove(key);
            }
        });
        here.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** While held: the distance to the waypoint above the hotbar, and the light column at the waypoint. */
    private static void held(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }
        ItemStack stack = player.getMainHandItem().is(GLAIVE.get()) ? player.getMainHandItem() : player.getOffhandItem();
        if (!stack.is(GLAIVE.get()) || !planted(stack)) {
            return;
        }
        CompoundTag tag = Weapons.data(stack);
        if (!tag.getString("waypoint_dim").equals(player.level().dimension().location().toString())) {
            player.displayClientMessage(Component.translatable("item.ostentum.pathfinders_glaive.elsewhere"), true);
            return;
        }
        Vec3 at = new Vec3(tag.getInt("waypoint_x") + 0.5, tag.getInt("waypoint_y"), tag.getInt("waypoint_z") + 0.5);
        player.displayClientMessage(Component.translatable("item.ostentum.pathfinders_glaive.distance", (int) Math.round(at.distanceTo(player.position()))), true);
        ServerLevel level = (ServerLevel) player.level();
        for (int i = 0; i < 12; i++) {
            level.sendParticles(player, ParticleTypes.END_ROD, true, at.x, at.y + 1.0 + i * 1.5, at.z, 1, 0.02, 0.2, 0.02, 0.0);
        }
    }

    private static void orphan(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Display.ItemDisplay display && display.getTags().contains(TAG) && ORPHANS.remove(display.getUUID())) {
            event.setCanceled(true);
        }
    }

    private PathfindersGlaive() {}
}
