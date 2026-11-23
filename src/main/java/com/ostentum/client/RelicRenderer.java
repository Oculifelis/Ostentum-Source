package com.ostentum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ostentum.Ostentum;
import com.ostentum.relic.RelicBlock;
import com.ostentum.relic.RelicBlockEntity;
import com.ostentum.relic.Relics;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a relic: the base, then the boss's gold likeness bobbing gently over it, each followed by its glow layer (the
 * base's diamond, the boss's own colour shining through). Models and placement: {@link RelicModels}.
 */
public class RelicRenderer implements BlockEntityRenderer<RelicBlockEntity, RelicRenderer.State> {
    static final ModelLayerLocation BASE_1 = new ModelLayerLocation(Ostentum.id("relic_base_1x1"), "main");
    static final ModelLayerLocation BASE_3 = new ModelLayerLocation(Ostentum.id("relic_base_3x3"), "main");
    static final ModelLayerLocation WITHER = new ModelLayerLocation(Ostentum.id("wither_relic"), "main");
    static final ModelLayerLocation DRAGON = new ModelLayerLocation(Ostentum.id("dragon_relic"), "main");
    static final ModelLayerLocation ELDER = new ModelLayerLocation(Ostentum.id("elder_guardian_relic"), "main");
    static final ModelLayerLocation WARDEN = new ModelLayerLocation(Ostentum.id("warden_relic"), "main");

    private static final Identifier BASE_1_TEX = tex("base_1x1"), BASE_1_GLOW = tex("base_1x1_glow");
    private static final Identifier BASE_3_TEX = tex("base_3x3"), BASE_3_GLOW = tex("base_3x3_glow");
    private static final Identifier WITHER_TEX = tex("wither_gold"), WITHER_GLOW = tex("wither_glow");
    private static final Identifier DRAGON_TEX = tex("dragon_gold"), DRAGON_GLOW = tex("dragon_glow");
    private static final Identifier ELDER_TEX = tex("elder_gold"), ELDER_GLOW = tex("elder_glow");
    private static final Identifier WARDEN_TEX = tex("warden_gold"), WARDEN_GLOW = tex("warden_glow");

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        boolean large;
        boolean elder;
        boolean warden;
        float time;
    }

    private final ModelPart base1, base3, wither, dragon, elder, warden;

    public RelicRenderer(BlockEntityRendererProvider.Context ctx) {
        this.base1 = ctx.bakeLayer(BASE_1);
        this.base3 = ctx.bakeLayer(BASE_3);
        this.wither = ctx.bakeLayer(WITHER);
        this.dragon = ctx.bakeLayer(DRAGON);
        this.elder = ctx.bakeLayer(ELDER);
        this.warden = ctx.bakeLayer(WARDEN);
    }

    private static Identifier tex(String name) {
        return Ostentum.id("textures/entity/relic/" + name + ".png");
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RelicBlockEntity be, State state, float partial, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partial, camera, breakProgress);
        state.facing = be.getBlockState().getValue(RelicBlock.FACING);
        state.large = be.getBlockState().getBlock() instanceof RelicBlock relic && relic.large();
        state.elder = be.getBlockState().is(Relics.ELDER_GUARDIAN_RELIC.get());
        state.warden = be.getBlockState().is(Relics.WARDEN_RELIC.get());
        state.time = be.getLevel() == null ? 0.0F : (be.getLevel().getGameTime() % 24000L) + partial;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.rotate(Axis.YP.rotationDegrees(180.0F - state.facing.toYRot()));
        pose.scale(-1.0F, -1.0F, 1.0F);                 // model space, as for mobs: y down, front toward -z
        int light = state.lightCoords;
        if (state.large) {
            part(nodes, pose, base3, BASE_3_TEX, BASE_3_GLOW, light);
            likeness(nodes, pose, dragon, DRAGON_TEX, DRAGON_GLOW, light, state.time, 1.2F,
                RelicModels.DRAGON_X, RelicModels.DRAGON_Y, RelicModels.DRAGON_Z, RelicModels.DRAGON_SCALE);
        } else if (state.elder) {
            part(nodes, pose, base1, BASE_1_TEX, BASE_1_GLOW, light);
            likeness(nodes, pose, elder, ELDER_TEX, ELDER_GLOW, light, state.time, 0.6F,
                RelicModels.ELDER_X, RelicModels.ELDER_Y, RelicModels.ELDER_Z, RelicModels.ELDER_SCALE);
        } else if (state.warden) {
            part(nodes, pose, base1, BASE_1_TEX, BASE_1_GLOW, light);
            likeness(nodes, pose, warden, WARDEN_TEX, WARDEN_GLOW, light, state.time, 0.6F,
                RelicModels.WARDEN_X, RelicModels.WARDEN_Y, RelicModels.WARDEN_Z, RelicModels.WARDEN_SCALE);
        } else {
            part(nodes, pose, base1, BASE_1_TEX, BASE_1_GLOW, light);
            likeness(nodes, pose, wither, WITHER_TEX, WITHER_GLOW, light, state.time, 0.6F,
                RelicModels.WITHER_X, RelicModels.WITHER_Y, RelicModels.WITHER_Z, RelicModels.WITHER_SCALE);
        }
        pose.popPose();
    }

    private static void likeness(SubmitNodeCollector nodes, PoseStack pose, ModelPart model, Identifier tex, Identifier glow, int light,
                                 float time, float bob, float x, float y, float z, float scale) {
        pose.pushPose();
        pose.translate(x / 16.0F, (y + Mth.sin(time * 0.06F) * bob) / 16.0F, z / 16.0F);
        pose.scale(scale, scale, scale);
        part(nodes, pose, model, tex, glow, light);
        pose.popPose();
    }

    private static void part(SubmitNodeCollector nodes, PoseStack pose, ModelPart model, Identifier tex, Identifier glow, int light) {
        nodes.submitModelPart(model, pose, RenderTypes.entityCutout(tex), light, OverlayTexture.NO_OVERLAY, null);
        nodes.submitModelPart(model, pose, RenderTypes.eyes(glow), light, OverlayTexture.NO_OVERLAY, null);
    }

    /** The likeness rises well above the block, and the 3x3 spreads a block to each side. */
    @Override
    public AABB getRenderBoundingBox(RelicBlockEntity be) {
        boolean large = be.getBlockState().getBlock() instanceof RelicBlock relic && relic.large();
        AABB box = new AABB(be.getBlockPos());
        return large ? box.inflate(1.5, 0.0, 1.5).expandTowards(0.0, 4.5, 0.0) : box.inflate(0.25, 0.0, 0.25).expandTowards(0.0, 1.5, 0.0);
    }
}
