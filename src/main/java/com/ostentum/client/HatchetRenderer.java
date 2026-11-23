package com.ostentum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ostentum.weapon.FrostbiteHatchet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown Frostbite Hatchet: its own item model,
 * drawn in the vertical plane of its flight instead of turned to the camera, and tumbling end over end, head first,
 * like a thrown axe; it keeps spinning forward when it turns home. The frost trail is the server's snowflakes.
 */
public final class HatchetRenderer extends EntityRenderer<FrostbiteHatchet.Thrown, HatchetRenderer.State> {
    /** Degrees of tumble per tick. */
    static final float SPIN = 42.0F;
    private final ItemModelResolver items;

    public HatchetRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemModelResolver();
    }

    public static final class State extends ThrownItemRenderState {
        float yRot;
        float xRot;
        float spin;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FrostbiteHatchet.Thrown entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        items.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.FIXED, entity);
        Vec3 move = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        if (move.lengthSqr() < 1.0E-6) {
            move = entity.getDeltaMovement();
        }
        if (move.lengthSqr() > 1.0E-6) {
            state.yRot = (float) (Mth.atan2(move.x, move.z) * Mth.RAD_TO_DEG);
            state.xRot = (float) (Mth.atan2(move.y, move.horizontalDistance()) * Mth.RAD_TO_DEG);
        } else {
            state.yRot = entity.getYRot(partialTicks);
            state.xRot = entity.getXRot(partialTicks);
        }
        state.spin = (entity.tickCount + partialTicks) * SPIN;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // flight along +X, the blade's plane the vertical plane of the flight, tumbling forward about Z
        poseStack.rotate(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        poseStack.rotate(Axis.ZP.rotationDegrees(state.xRot));
        poseStack.rotate(Axis.ZP.rotationDegrees(-state.spin));
        poseStack.scale(0.85F, 0.85F, 0.85F);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
