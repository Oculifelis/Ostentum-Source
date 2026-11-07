package com.ostentum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ostentum.Ostentum;
import com.ostentum.entity.GuardianTotem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Guardian Totem drawn as the real Totem of Undying, slowly turning and bobbing; grey once it has saved its player today. */
public class TotemRenderer extends EntityRenderer<GuardianTotem, TotemRenderer.State> {
    public static class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public boolean ready;
    }

    // made on first draw: item stacks can't be built while resources first load, before item components are bound
    private ItemStack ready, spent;
    private final ItemModelResolver items;

    public TotemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemModelResolver();
        this.shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GuardianTotem totem, State state, float partialTicks) {
        super.extractRenderState(totem, state, partialTicks);
        state.ready = totem.ready();
        if (ready == null) {
            ready = new ItemStack(Items.TOTEM_OF_UNDYING);
            spent = ready.copy();
            spent.set(DataComponents.ITEM_MODEL, Ostentum.id("guardian_totem_spent"));
        }
        items.updateForNonLiving(state.item, state.ready ? ready : spent, ItemDisplayContext.GROUND, totem);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.0F, 0.2F + Mth.sin(state.ageInTicks / 12.0F) * 0.05F, 0.0F);
        pose.mulPose(Axis.YP.rotation(state.ageInTicks / 30.0F));
        pose.scale(0.9F, 0.9F, 0.9F);
        state.item.submit(pose, nodes, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
        super.submit(state, pose, nodes, camera);
    }

    @Override
    protected int getBlockLightLevel(GuardianTotem totem, BlockPos pos) {
        return totem.ready() ? Math.max(12, super.getBlockLightLevel(totem, pos)) : super.getBlockLightLevel(totem, pos);
    }
}
