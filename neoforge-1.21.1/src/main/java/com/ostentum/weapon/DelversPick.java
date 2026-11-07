package com.ostentum.weapon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.common.SimpleTier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Delver's Pick: a diamond pickaxe whose mined block is the middle of a 1x3 line, up and down on a wall, along your
 * facing on a floor or ceiling. The extra blocks only break when the pick could mine them, each for a point of
 * durability; sneak to mine one block. Abandoned mineshaft minecarts can hold a Rusted Delver's Head (more often below
 * Y 0); smithed onto a diamond pickaxe it makes the Dormant Delver's Pick, a plain pick, which comes out awakened when
 * thrown through a Nether portal (Fundamentum portal crafting, recipe/delvers_pick_awakening.json).
 */
public final class DelversPick {
    public static final DeferredItem<Item> HEAD = Weapons.item("rusted_delvers_head", Item::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> DORMANT = Weapons.item("dormant_delvers_pick", Item::new, p -> p.rarity(Rarity.UNCOMMON)
        .pickaxe(Tiers.DIAMOND, 1.0F, -2.8F));
    public static final DeferredItem<Item> PICK = Weapons.item("delvers_pick", Item::new, p -> p.rarity(Rarity.RARE)
        .pickaxe(Tiers.DIAMOND, 1.0F, -2.8F));

    private static boolean mining;

    static void init() {
        NeoForge.EVENT_BUS.addListener(DelversPick::mine);
    }

    /** The two extra blocks for a block mined at {@code pos} from {@code face}, looking {@code facing}. */
    public static List<BlockPos> line(BlockPos pos, Direction face, Direction facing) {
        List<BlockPos> out = new ArrayList<>();
        if (face.getAxis().isHorizontal()) {
            out.add(pos.above());
            out.add(pos.below());
        } else {
            out.add(pos.relative(facing));
            out.add(pos.relative(facing.getOpposite()));
        }
        return out;
    }

    private static void mine(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (mining || !(event.getPlayer() instanceof ServerPlayer player) || player.isShiftKeyDown() || !Weapons.holds(player, PICK)
            || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Direction face = face(player, level, event.getPos());
        ItemStack pick = player.getMainHandItem();
        mining = true;
        try {
            for (BlockPos pos : line(event.getPos(), face, player.getDirection())) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || !pick.isCorrectToolForDrops(state) || pick.isEmpty()) {
                    continue;
                }
                player.gameMode.destroyBlock(pos);
            }
        } finally {
            mining = false;
        }
    }

    /** The face of {@code pos} the player is looking at (a wall face if the ray misses). */
    static Direction face(ServerPlayer player, Level level, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange() + 1.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) ? hit.getDirection() : player.getDirection().getOpposite();
    }

    private DelversPick() {}
}
