package com.ostentum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ostentum.Ostentum;
import com.ostentum.entity.GuardianTotem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Guardian Totem drawn as the real Totem of Undying, slowly turning and bobbing; grey once it has saved its player
 * today. 1.21.1: the grey one is the standalone model {@link #SPENT} (registered in {@link OstentumClient}).
 */
public class TotemRenderer extends EntityRenderer<GuardianTotem> {
    static final ModelResourceLocation SPENT = ModelResourceLocation.standalone(Ostentum.id("item/guardian_totem_spent"));

    // made on first draw: item stacks can't be built while resources first load, before item components are bound
    private ItemStack totem;
    private final ItemRenderer items;

    public TotemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(GuardianTotem entity, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        if (totem == null) {
            totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        }
        float age = entity.tickCount + partialTicks;
        pose.pushPose();
        pose.translate(0.0F, 0.2F + Mth.sin(age / 12.0F) * 0.05F, 0.0F);
        pose.mulPose(Axis.YP.rotation(age / 30.0F));
        pose.scale(0.9F, 0.9F, 0.9F);
        BakedModel model = entity.ready() ? items.getModel(totem, entity.level(), null, entity.getId())
            : Minecraft.getInstance().getModelManager().getModel(SPENT);
        items.render(totem, ItemDisplayContext.GROUND, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GuardianTotem entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    protected int getBlockLightLevel(GuardianTotem totem, BlockPos pos) {
        return totem.ready() ? Math.max(12, super.getBlockLightLevel(totem, pos)) : super.getBlockLightLevel(totem, pos);
    }
}
