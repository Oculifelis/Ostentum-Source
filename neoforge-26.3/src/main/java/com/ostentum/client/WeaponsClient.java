package com.ostentum.client;

import com.fundamentum.client.Cloth;
import com.fundamentum.curios.Slots;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ostentum.Ostentum;
import com.ostentum.weapon.AbyssalTrident;
import com.ostentum.weapon.CoralTrident;
import com.ostentum.weapon.FisherKingsTrident;
import com.ostentum.weapon.FrostbiteHatchet;
import com.ostentum.weapon.PhantomVeil;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.object.projectile.TridentModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * The weapons' client side ({@code weapon}): the three tridents drawn with their own textures (thrown and in hand, the
 * {@code ostentum:trident} special item model), the thrown Frostbite Hatchet, and the Phantom Veil: the cloak on its
 * wearer (tools/weapons/veil.py's model, its cape swinging with vanilla's cape numbers) and the far-off shimmer of a
 * sneaking wearer.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class WeaponsClient {
    static final ModelLayerLocation VEIL = new ModelLayerLocation(Ostentum.id("phantom_veil"), "main");
    static final Identifier VEIL_TEXTURE = Ostentum.id("textures/entity/curios/phantom_veil.png");
    /** Set on a player's render state when they wear the veil. */
    static final ContextKey<Boolean> WEARS_VEIL = new ContextKey<>(Ostentum.id("wears_veil"));
    /** The veil's cloth chain this frame (Fundamentum's Cloth; the layer gets no entity). */
    static final ContextKey<Cloth.Pose> VEIL_CLOTH = new ContextKey<>(Ostentum.id("veil_cloth"));

    private WeaponsClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        trident(event, FisherKingsTrident.THROWN.get(), "fisher_kings_trident");
        trident(event, CoralTrident.THROWN.get(), "coral_trident");
        event.registerEntityRenderer(AbyssalTrident.THROWN.get(), ctx -> new TridentRenderer(ctx,
            new AbyssalTridentModel(ctx.bakeLayer(AbyssalTridentModel.LAYER)), AbyssalTridentModel.TEXTURE, AbyssalTridentModel.GLOW));
        event.registerEntityRenderer(FrostbiteHatchet.THROWN.get(), HatchetRenderer::new);
    }

    private static void trident(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends ThrownTrident> type, String name) {
        Identifier texture = Ostentum.id("textures/entity/trident/" + name + ".png");
        event.registerEntityRenderer(type, ctx -> new TridentRenderer(ctx, new TridentModel(ctx.bakeLayer(ModelLayers.TRIDENT)), texture, null));
    }

    @SubscribeEvent
    public static void layerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VEIL, VeilModel::layer);
        event.registerLayerDefinition(AbyssalTridentModel.LAYER, AbyssalTridentModel::layer);
    }

    @SubscribeEvent
    public static void specialRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(Ostentum.id("trident"), TridentItem.Unbaked.MAP_CODEC);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            AvatarRenderer<AbstractClientPlayer> renderer = event.getPlayerRenderer(skin);
            if (renderer != null) {
                renderer.addLayer(new VeilLayer(renderer, new VeilModel(event.getEntityModels().bakeLayer(VEIL))));
            }
        }
    }

    @SubscribeEvent
    public static void renderStates(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T entity, AvatarRenderState state) {
                if (!(entity instanceof Player player) || !PhantomVeil.wears(player)) {
                    return;
                }
                state.setRenderData(WEARS_VEIL, Boolean.TRUE);
                state.setRenderData(VEIL_CLOTH, Cloth.pose(player, "ostentum:phantom_veil", VeilModel.SEGMENTS,
                    Mth.clamp(state.ageInTicks - player.tickCount, 0.0F, 1.0F)));
                // a sneaking wearer seen from beyond 8 blocks by someone else is only a faint shimmer
                if (player.isCrouching() && player != Minecraft.getInstance().player
                    && state.distanceToCameraSq > PhantomVeil.SHIMMER_RANGE * PhantomVeil.SHIMMER_RANGE) {
                    state.isInvisible = true;
                    state.isInvisibleToPlayer = false;
                    state.nameTag = null;
                }
            }
        });
    }

    /** A thrown trident with its own model and texture, as vanilla's ThrownTridentRenderer, and an emissive glow texture if it has one. */
    static final class TridentRenderer extends EntityRenderer<ThrownTrident, ThrownTridentRenderState> {
        private final Model<Unit> model;
        private final Identifier texture;
        private final @Nullable Identifier glow;

        TridentRenderer(EntityRendererProvider.Context context, Model<Unit> model, Identifier texture, @Nullable Identifier glow) {
            super(context);
            this.model = model;
            this.texture = texture;
            this.glow = glow;
        }

        @Override
        public void submit(ThrownTridentRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            poseStack.pushPose();
            poseStack.rotate(Axis.YP.rotationDegrees(state.yRot - 90.0F));
            poseStack.rotate(Axis.ZP.rotationDegrees(state.xRot + 90.0F));
            collector.order(0).submitModel(model, Unit.INSTANCE, poseStack,
                state.isFoil ? RenderTypes.entitySolidGlint(texture) : model.renderType(texture), state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.outlineColor);
            if (glow != null) {
                collector.order(2).submitModel(model, Unit.INSTANCE, poseStack, RenderTypes.eyes(glow), state.lightCoords, OverlayTexture.NO_OVERLAY,
                    state.outlineColor);
            }
            poseStack.popPose();
            super.submit(state, poseStack, collector, camera);
        }

        @Override
        public ThrownTridentRenderState createRenderState() {
            return new ThrownTridentRenderState();
        }

        @Override
        public void extractRenderState(ThrownTrident entity, ThrownTridentRenderState state, float partialTicks) {
            super.extractRenderState(entity, state, partialTicks);
            state.yRot = entity.getYRot(partialTicks);
            state.xRot = entity.getXRot(partialTicks);
            state.isFoil = entity.isFoil();
        }
    }

    /**
     * The {@code ostentum:trident} special item model: vanilla's trident model (or the one {@code "layer"} names) with the
     * texture the item model names, and an emissive {@code "glow"} texture on top if it names one.
     */
    static final class TridentItem implements NoDataSpecialModelRenderer {
        private final Model<Unit> model;
        private final Identifier texture;
        private final @Nullable Identifier glow;

        TridentItem(Model<Unit> model, Identifier texture, @Nullable Identifier glow) {
            this.model = model;
            this.texture = texture;
            this.glow = glow;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
            collector.submitModelPart(model.root(), poseStack, foil ? RenderTypes.entitySolidGlint(texture) : model.renderType(texture), light,
                overlay, null, -1, outline);
            if (glow != null) {
                collector.submitModelPart(model.root(), poseStack, RenderTypes.eyes(glow), light, overlay, null, -1, outline);
            }
        }

        @Override
        public void getExtents(Consumer<Vector3fc> output) {
            model.root().getExtentsForGui(new PoseStack(), output);
        }

        /**
         * {@code "texture"} and the optional {@code "glow"}: {@code "ostentum:entity/trident/<name>"}, paths under textures/
         * without .png, as vanilla's model textures; the optional {@code "layer"} is a model layer id (its "main" layer).
         */
        record Unbaked(Identifier texture, Optional<Identifier> glow, Optional<Identifier> layer) implements NoDataSpecialModelRenderer.Unbaked {
            static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture),
                Identifier.CODEC.optionalFieldOf("glow").forGetter(Unbaked::glow),
                Identifier.CODEC.optionalFieldOf("layer").forGetter(Unbaked::layer)).apply(i, Unbaked::new));

            @Override
            public MapCodec<Unbaked> type() {
                return MAP_CODEC;
            }

            @Override
            public TridentItem bake(SpecialModelRenderer.BakingContext context) {
                Model<Unit> model = layer.isPresent()
                    ? new AbyssalTridentModel(context.entityModelSet().bakeLayer(new ModelLayerLocation(layer.get(), "main")))
                    : new TridentModel(context.entityModelSet().bakeLayer(ModelLayers.TRIDENT));
                return new TridentItem(model, texture.withPath(p -> "textures/" + p + ".png"),
                    glow.map(g -> g.withPath(p -> "textures/" + p + ".png")).orElse(null));
            }
        }
    }

    /** The Phantom Veil on a player: mantle, hood and a two-part swinging cape on the body, drapes on the arms. */
    static final class VeilModel extends Model<AvatarRenderState> {
        private final ModelPart body;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        /** The cape's cloth segments, top to hem, each a child of the one before. */
        static final int SEGMENTS = 4;
        private final ModelPart[] cloth = new ModelPart[SEGMENTS];

        VeilModel(ModelPart root) {
            super(root, RenderTypes::entityTranslucent);
            this.body = root.getChild("body");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
            this.cloth[0] = body.getChild("cape");
            this.cloth[1] = cloth[0].getChild("cape_lower");
            this.cloth[2] = cloth[1].getChild("cape_hem");
            this.cloth[3] = cloth[2].getChild("cape_hem_lower");
        }

        static LayerDefinition layer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
            body.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(24, 0).addBox(-4.5F, -0.5F, -2.5F, 9, 3, 5), PartPose.ZERO);
            body.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 21).addBox(-4.0F, -1.5F, 2.1F, 8, 3, 2), PartPose.ZERO);
            // the cape as four cloth segments, each hung from the one above; the boxes split the old 10 + 8 pixel cape and
            // hem in halves on the same texture rows, so the texture is unchanged
            PartDefinition cape = body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, 0.05F, 10, 5, 1),
                PartPose.offset(0.0F, 0.0F, 2.0F));
            PartDefinition lower = cape.addOrReplaceChild("cape_lower", CubeListBuilder.create().texOffs(0, 5).addBox(-5.0F, 0.0F, 0.05F, 10, 5, 1),
                PartPose.offset(0.0F, 5.0F, 0.0F));
            PartDefinition hem = lower.addOrReplaceChild("cape_hem", CubeListBuilder.create().texOffs(0, 11).addBox(-6.0F, 0.0F, 0.0F, 12, 4, 1),
                PartPose.offset(0.0F, 5.0F, 0.2F));
            hem.addOrReplaceChild("cape_hem_lower", CubeListBuilder.create().texOffs(0, 15).addBox(-6.0F, 0.0F, 0.0F, 12, 4, 1),
                PartPose.offset(0.0F, 4.0F, 0.0F));
            root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.ZERO)
                .addOrReplaceChild("right_shoulder", CubeListBuilder.create().texOffs(26, 9).addBox(-3.5F, -2.5F, -2.5F, 5, 4, 5), PartPose.ZERO);
            root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.ZERO)
                .addOrReplaceChild("left_shoulder", CubeListBuilder.create().texOffs(26, 18).addBox(-1.5F, -2.5F, -2.5F, 5, 4, 5), PartPose.ZERO);
            return LayerDefinition.create(mesh, 64, 32);
        }

        private PlayerModel parent;

        /** Remembers the player model to follow; the posing happens in setupAnim, when the submitted model is drawn. */
        void follow(PlayerModel player, AvatarRenderState state) {
            this.parent = player;
        }

        /**
         * Runs when the submitted model is drawn (after Model's reset): follows the player model's body and arms posed
         * from the same state, and hangs the cape as the cloth chain the render state carries.
         */
        @Override
        public void setupAnim(AvatarRenderState state) {
            super.setupAnim(state);
            if (parent == null) {
                return;
            }
            parent.setupAnim(state);
            body.loadPose(parent.body.storePose());
            rightArm.loadPose(parent.rightArm.storePose());
            leftArm.loadPose(parent.leftArm.storePose());
            Cloth.Pose pose = state.getRenderData(VEIL_CLOTH);
            if (pose == null) {
                return;
            }
            // Fundamentum's cloth chain (1.9.4): sway and trail from vanilla's cape inertia, lift in a fall, idle drift
            for (int i = 0; i < SEGMENTS; i++) {
                cloth[i].resetPose();
                cloth[i].xRot = pose.pitch()[i];
                cloth[i].zRot = pose.roll()[i];
            }
            if (pose.chestArmour()) {
                cloth[0].y += Cloth.ARMOUR_Y * 16.0F;
                cloth[0].z += Cloth.ARMOUR_Z * 16.0F;
            }
        }
    }

    static final class VeilLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
        private final VeilModel model;

        VeilLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, VeilModel model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
            if (state.getRenderData(WEARS_VEIL) == null || state.isInvisible) {
                return;
            }
            model.follow(getParentModel(), state);
            collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityTranslucent(VEIL_TEXTURE), light, OverlayTexture.NO_OVERLAY,
                state.outlineColor);
        }
    }
}
