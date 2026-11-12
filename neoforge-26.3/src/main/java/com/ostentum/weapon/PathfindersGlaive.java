package com.ostentum.weapon;

import com.mojang.math.Transformation;
import com.ostentum.OstentumSounds;
import com.ostentum.mixin.DisplayAccess;
import com.ostentum.mixin.ItemDisplayAccess;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pathfinder's Glaive (iron + compass): an iron spear that marks a waypoint. Use on the top of a block plants a copy of
 * it there; the planted glaive glows, a column of light rises from it to the top of the world, and while you hold the glaive the
 * distance shows above the hotbar. Use again, from any range, pulls it back. The waypoint lives on the glaive's stack.
 */
public final class PathfindersGlaive {
    public static final DeferredItem<Item> GLAIVE = Weapons.item("pathfinders_glaive", Glaive::new, p -> p
        .spear(ToolMaterial.IRON, 0.95F, 0.95F, 0.6F, 2.5F, 11.0F, 6.75F, 5.1F, 11.25F, 4.6F)
        // the spear's own numbers again (what vanilla's spear(...) above builds), with the glaive's sounds
        .component(DataComponents.KINETIC_WEAPON, new KineticWeapon(10, 12,
            KineticWeapon.Condition.ofAttackerSpeed(50, 11.0F), KineticWeapon.Condition.ofAttackerSpeed(135, 5.1F),
            KineticWeapon.Condition.ofRelativeSpeed(225, 4.6F), 0.38F, 0.95F,
            Optional.of(OstentumSounds.GLAIVE_CHARGE), Optional.of(OstentumSounds.GLAIVE_HIT)))
        .component(DataComponents.PIERCING_WEAPON, new PiercingWeapon(true, false,
            Optional.of(OstentumSounds.GLAIVE_ATTACK), Optional.of(OstentumSounds.GLAIVE_HIT))));

    static final String TAG = "ostentum_glaive";
    /** Planted glaives recalled while their chunk was unloaded: removed when they load again. */
    private static final Set<UUID> ORPHANS = ConcurrentHashMap.newKeySet();

    static void init() {
        NeoForge.EVENT_BUS.addListener(PathfindersGlaive::held);
        NeoForge.EVENT_BUS.addListener(PathfindersGlaive::orphan);
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

        /** The thrust trail: compass-gold motes along each jab out to the glaive's reach, a white glint at the tip. */
        @Override
        public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
            if (entity.level() instanceof ServerLevel level) {
                trail(level, entity);
            }
            return false;
        }

        /** Never the enchantment glint, in hand or planted. */
        @Override
        public boolean isFoil(ItemStack stack) {
            return false;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (planted(stack)) {
                recall(level, player, stack);
                return InteractionResult.SUCCESS;
            }
            return super.use(level, player, hand);
        }
    }

    /** Eight motes from 0.6 to 4.5 blocks out along the look, gold fading to amber and shrinking, then one white glint. */
    static void trail(ServerLevel level, LivingEntity entity) {
        Vec3 eye = entity.getEyePosition(), look = entity.getLookAngle();
        for (int i = 0; i < 8; i++) {
            double t = i / 7.0, d = 0.6 + 3.9 * t;
            Vec3 at = eye.add(look.scale(d)).add(0, -0.25, 0);
            level.sendParticles(dust(0xFFD666, 0xE0A838, t, 0.9F - 0.5F * (float) t), at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        Vec3 tip = eye.add(look.scale(4.6)).add(0, -0.25, 0);
        level.sendParticles(dust(0xFFFAEC, 0xFFFAEC, 0, 1.0F), tip.x, tip.y, tip.z, 1, 0, 0, 0, 0);
    }

    private static DustParticleOptions dust(int from, int to, double t, float scale) {
        int r = (int) Mth.lerp(t, (from >> 16) & 255, (to >> 16) & 255), g = (int) Mth.lerp(t, (from >> 8) & 255, (to >> 8) & 255);
        int b = (int) Mth.lerp(t, from & 255, to & 255);
        return new DustParticleOptions((r << 16) | (g << 8) | b, scale);
    }

    public static boolean planted(ItemStack stack) {
        return Weapons.data(stack).contains("waypoint_dim");
    }

    /** Where this stack's glaive is planted, if it is planted in {@code level}'s dimension; else null. */
    public static BlockPos waypoint(ItemStack stack, Level level) {
        CompoundTag tag = Weapons.data(stack);
        if (!tag.getStringOr("waypoint_dim", "").equals(level.dimension().identifier().toString())) {
            return null;
        }
        return new BlockPos(tag.getIntOr("waypoint_x", 0), tag.getIntOr("waypoint_y", 0), tag.getIntOr("waypoint_z", 0));
    }

    /** Plants a glowing copy of the glaive at {@code at} and writes the waypoint onto {@code stack}. */
    public static void plant(ServerLevel level, Player player, ItemStack stack, BlockPos at) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        ((ItemDisplayAccess) display).ostentum$setItemStack(stack.copyWithCount(1));
        // the icon runs butt (bottom left) to tip (top right): turn it tip down and sink it a little
        ((DisplayAccess) display).ostentum$setTransformation(new Transformation(new Vector3f(0.0F, 0.55F, 0.0F),
            new Quaternionf().rotateZ((float) Math.toRadians(-135.0)), new Vector3f(1.4F, 1.4F, 1.4F), null));
        display.addTag(TAG);
        display.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        level.addFreshEntity(display);
        UUID id = display.getUUID();
        Weapons.edit(stack, tag -> {
            tag.putString("waypoint_dim", level.dimension().identifier().toString());
            tag.putInt("waypoint_x", at.getX());
            tag.putInt("waypoint_y", at.getY());
            tag.putInt("waypoint_z", at.getZ());
            tag.putString("waypoint_id", id.toString());
        });
        level.playSound(null, at, OstentumSounds.GLAIVE_PLANT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
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
        UUID id = UUID.fromString(tag.getStringOr("waypoint_id", new UUID(0, 0).toString()));
        ServerLevel there = here.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(tag.getStringOr("waypoint_dim", "minecraft:overworld"))));
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
        here.playSound(null, player.blockPosition(), OstentumSounds.GLAIVE_RECALL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /**
     * While held: the distance to the waypoint above the hotbar. The light column is drawn by the client
     * ({@code client.GlaiveBeam}) from the waypoint on the stack, up to the top of the world, at any range.
     */
    private static void held(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }
        ItemStack stack = player.getMainHandItem().is(GLAIVE.get()) ? player.getMainHandItem() : player.getOffhandItem();
        if (!stack.is(GLAIVE.get()) || !planted(stack)) {
            return;
        }
        CompoundTag tag = Weapons.data(stack);
        if (!tag.getStringOr("waypoint_dim", "").equals(player.level().dimension().identifier().toString())) {
            player.sendOverlayMessage(Component.translatable("item.ostentum.pathfinders_glaive.elsewhere"));
            return;
        }
        Vec3 at = new Vec3(tag.getIntOr("waypoint_x", 0) + 0.5, tag.getIntOr("waypoint_y", 0), tag.getIntOr("waypoint_z", 0) + 0.5);
        player.sendOverlayMessage(Component.translatable("item.ostentum.pathfinders_glaive.distance", (int) Math.round(at.distanceTo(player.position()))));
    }

    private static void orphan(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Display.ItemDisplay display && display.entityTags().contains(TAG) && ORPHANS.remove(display.getUUID())) {
            event.setCanceled(true);
        }
    }

    private PathfindersGlaive() {}
}
