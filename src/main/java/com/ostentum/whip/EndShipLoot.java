package com.ostentum.whip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ostentum.mixin.TemplatePieceAccess;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/**
 * Puts an Ender Whip in an End city chest with {@code chance}, but only when the chest stands on the city's ship (the
 * "ship" template piece): the city's own chests share the same loot table and never get one. The data file limits it
 * to {@code minecraft:chests/end_city_treasure}.
 */
public final class EndShipLoot extends LootModifier {
    public static final Codec<EndShipLoot> CODEC = RecordCodecBuilder.create(i -> codecStart(i)
        .and(Codec.floatRange(0, 1).fieldOf("chance").forGetter(m -> m.chance))
        .apply(i, EndShipLoot::new));

    private final float chance;

    EndShipLoot(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin != null && context.getRandom().nextFloat() < chance && onShip(context.getLevel(), BlockPos.containing(origin))) {
            loot.add(new ItemStack(OstentumWhips.ENDER_WHIP.get()));
        }
        return loot;
    }

    /** True when {@code pos} is inside the ship piece of an End city. */
    public static boolean onShip(ServerLevel level, BlockPos pos) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, BuiltinStructures.END_CITY);
        if (!start.isValid()) {
            return false;
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof TemplatePieceAccess template && "ship".equals(template.ostentum$templateName())
                && piece.getBoundingBox().isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
