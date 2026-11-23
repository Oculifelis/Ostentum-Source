package com.ostentum.weapon;

import com.mojang.math.Transformation;
import com.ostentum.mixin.BlockDisplayAccess;
import com.ostentum.mixin.DisplayAccess;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import org.joml.Vector3f;

/**
 * Quake Hammer (heavy core + breeze rod), a mace sidegrade: mace-like hits with no fall-damage scaling. Sneak + attack
 * the ground sends a fissure crawling {@link #LENGTH} blocks forward, one block a tick; the blocks along it heave up
 * (shown, not broken), launching and hurting everything on the line.
 */
public final class QuakeHammer {
    public static final DeferredItem<Item> HAMMER = Weapons.item("quake_hammer", Item::new, p -> p.rarity(Rarity.EPIC).durability(500)
        .component(DataComponents.TOOL, MaceItem.createToolProperties()).repairable(Items.BREEZE_ROD).attributes(MaceItem.createAttributes())
        .enchantable(15).component(DataComponents.WEAPON, new Weapon(1)));

    public static final int LENGTH = 8;
    static final float DAMAGE = 6.0F;
    static final int COOLDOWN = 40;

    static void init() {
        NeoForge.EVENT_BUS.addListener(QuakeHammer::slam);
    }

    private static void slam(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START || !(event.getLevel() instanceof ServerLevel level)
            || !player.isShiftKeyDown() || !Weapons.holds(player, HAMMER) || player.getCooldowns().isOnCooldown(player.getMainHandItem())) {
            return;
        }
        event.setCanceled(true);
        player.getCooldowns().addCooldown(player.getMainHandItem(), COOLDOWN);
        player.getMainHandItem().hurtAndBreak(2, player, InteractionHand.MAIN_HAND.asEquipmentSlot());
        fissure(level, player, event.getPos());
    }

    /** Starts a fissure from {@code from} along the player's facing; returns the mobs it will strike, as it reaches them. */
    public static Set<LivingEntity> fissure(ServerLevel level, Player player, BlockPos from) {
        Vec3 dir = player.getLookAngle().multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot()) : dir.normalize();
        Set<LivingEntity> struck = new HashSet<>();
        level.playSound(null, from, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 0.7F);
        for (int i = 1; i <= LENGTH; i++) {
            Vec3 point = Vec3.atCenterOf(from).add(dir.scale(i));
            int step = i;
            Weapons.later(level, i, () -> heave(level, player, BlockPos.containing(point.x, from.getY(), point.z), struck, step));
        }
        return struck;
    }

    static void heave(ServerLevel level, Player player, BlockPos column, Set<LivingEntity> struck, int step) {
        BlockPos ground = null;
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos pos = column.above(dy);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP) && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                ground = pos;
                break;
            }
        }
        if (ground == null) {
            return;
        }
        BlockState state = level.getBlockState(ground);
        if (state.getRenderShape() == RenderShape.MODEL) {
            lift(level, ground, state);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), ground.getX() + 0.5, ground.getY() + 1.1, ground.getZ() + 0.5,
            12, 0.3, 0.1, 0.3, 0.15);
        if (step % 2 == 0) {
            level.playSound(null, ground, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.8F, 0.6F);
        }
        AABB line = new AABB(ground.above()).inflate(0.7, 1.0, 0.7);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, line, e -> e != player && e.isAlive())) {
            if (struck.add(living)) {
                living.hurtServer(level, level.damageSources().playerAttack(player), DAMAGE);
                living.setDeltaMovement(living.getDeltaMovement().add(0, 0.75, 0));
                living.hurtMarked = true;
            }
        }
    }

    /** A copy of the block that rises half a block and settles back, then goes; the block itself never moves. */
    private static void lift(ServerLevel level, BlockPos pos, BlockState state) {
        Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        ((BlockDisplayAccess) display).ostentum$setBlockState(state);
        DisplayAccess access = (DisplayAccess) display;
        access.ostentum$setTransformation(new Transformation(new Vector3f(-0.005F, 0.0F, -0.005F), null, new Vector3f(1.01F, 1.01F, 1.01F), null));
        display.setPos(pos.getX(), pos.getY(), pos.getZ());
        level.addFreshEntity(display);
        Weapons.later(level, 2, () -> {
            access.ostentum$setInterpolationDelay(0);
            access.ostentum$setInterpolationDuration(3);
            access.ostentum$setTransformation(new Transformation(new Vector3f(-0.005F, 0.55F, -0.005F), null, new Vector3f(1.01F, 1.01F, 1.01F), null));
        });
        Weapons.later(level, 6, () -> {
            access.ostentum$setInterpolationDelay(0);
            access.ostentum$setInterpolationDuration(5);
            access.ostentum$setTransformation(new Transformation(new Vector3f(-0.005F, 0.0F, -0.005F), null, new Vector3f(1.01F, 1.01F, 1.01F), null));
        });
        Weapons.later(level, 12, display::discard);
    }

    private QuakeHammer() {}
}
