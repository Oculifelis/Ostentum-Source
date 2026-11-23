package com.ostentum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ostentum.weapon.FrostbiteHatchet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown Frostbite Hatchet: its own item model,
 * drawn in the vertical plane of its flight instead of turned to the camera, and tumbling end over end, head first,
 * like a thrown axe; it keeps spinning forward when it turns home. The frost trail is the server's snowflakes.
 */
public final class HatchetRenderer extends EntityRenderer<FrostbiteHatchet.Thrown> {
    /** Degrees of tumble per tick. */
    static final float SPIN = 42.0F;
    private final ItemRenderer items;

    public HatchetRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(FrostbiteHatchet.Thrown entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        Vec3 move = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        if (move.lengthSqr() < 1.0E-6) {
            move = entity.getDeltaMovement();
        }
        boolean moving = move.lengthSqr() > 1.0E-6;
        float yRot = moving ? (float) (Mth.atan2(move.x, move.z) * Mth.RAD_TO_DEG) : entity.getViewYRot(partialTicks);
        float xRot = moving ? (float) (Mth.atan2(move.y, move.horizontalDistance()) * Mth.RAD_TO_DEG) : entity.getViewXRot(partialTicks);
        poseStack.pushPose();
        // flight along +X, the blade's plane the vertical plane of the flight, tumbling forward about Z
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(xRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-(entity.tickCount + partialTicks) * SPIN));
        poseStack.scale(0.85F, 0.85F, 0.85F);
        items.renderStatic(entity.getItem(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack, buffers, entity.level(), entity.getId());
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(FrostbiteHatchet.Thrown entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
