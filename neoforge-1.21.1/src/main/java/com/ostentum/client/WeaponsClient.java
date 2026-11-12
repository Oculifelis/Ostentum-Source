package com.ostentum.client;

import com.fundamentum.client.Cloth;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ostentum.Ostentum;
import com.ostentum.weapon.AbyssalTrident;
import com.ostentum.weapon.BastionCrossbow;
import com.ostentum.weapon.BloomBow;
import com.ostentum.weapon.CoralTrident;
import com.ostentum.weapon.DuelistsSaber;
import com.ostentum.weapon.EngineersCrossbow;
import com.ostentum.weapon.FisherKingsTrident;
import com.ostentum.weapon.FrostbiteHatchet;
import com.ostentum.weapon.PhantomVeil;
import com.ostentum.weapon.VoidstringBow;
import com.ostentum.weapon.Weapons;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.TridentModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The weapons' client side ({@code weapon}): the three tridents drawn with their own textures (thrown, and in hand
 * through {@link TridentItems}, as vanilla 1.21.1 draws its trident), the thrown Frostbite Hatchet, the bows' and
 * crossbows' pulling models, and the Phantom Veil: the cloak on its wearer (tools/weapons/veil.py's model, its cape
 * swinging with vanilla's cape numbers). The far-off shimmer of a sneaking wearer is {@code mixin/LivingEntityRendererMixin}.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class WeaponsClient {
    static final ModelLayerLocation VEIL = new ModelLayerLocation(Ostentum.id("phantom_veil"), "main");
    static final ResourceLocation VEIL_TEXTURE = Ostentum.id("textures/entity/curios/phantom_veil.png");

    private WeaponsClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        trident(event, FisherKingsTrident.THROWN.get(), "fisher_kings_trident");
        trident(event, CoralTrident.THROWN.get(), "coral_trident");
        event.registerEntityRenderer(AbyssalTrident.THROWN.get(), AbyssalRenderer::new);
        event.registerEntityRenderer(FrostbiteHatchet.THROWN.get(), HatchetRenderer::new);
    }

    private static void trident(EntityRenderersEvent.RegisterRenderers event, EntityType<? extends ThrownTrident> type, String name) {
        ResourceLocation texture = Ostentum.id("textures/entity/trident/" + name + ".png");
        event.registerEntityRenderer(type, ctx -> new TridentRenderer(ctx, texture));
    }

    @SubscribeEvent
    public static void layerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(VEIL, VeilModel::layer);
        event.registerLayerDefinition(AbyssalTridentModel.LAYER, AbyssalTridentModel::layer);
    }

    /** The tridents in hand: builtin/entity models drawn by {@link TridentItems}. */
    @SubscribeEvent
    public static void clientExtensions(RegisterClientExtensionsEvent event) {
        TridentItems renderer = new TridentItems();
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        }, FisherKingsTrident.TRIDENT.get(), CoralTrident.TRIDENT.get(), AbyssalTrident.TRIDENT.get());
    }

    /** Vanilla registers its bow, crossbow and trident model properties for its own items only. */
    @SubscribeEvent
    public static void itemProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (Item bow : new Item[] {BloomBow.BOW.get(), VoidstringBow.BOW.get()}) {
                ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) ->
                    entity == null || entity.getUseItem() != stack ? 0.0F : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F);
                ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            }
            for (Item crossbow : new Item[] {BastionCrossbow.CROSSBOW.get(), EngineersCrossbow.CROSSBOW.get()}) {
                ItemProperties.register(crossbow, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) ->
                    entity == null || CrossbowItem.isCharged(stack) ? 0.0F : Weapons.crossbowPull(stack, entity));
                ItemProperties.register(crossbow, ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
                ItemProperties.register(crossbow, ResourceLocation.withDefaultNamespace("charged"), (stack, level, entity, seed) ->
                    CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
                ItemProperties.register(crossbow, ResourceLocation.withDefaultNamespace("firework"), (stack, level, entity, seed) -> {
                    ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
                    return charged != null && charged.contains(Items.FIREWORK_ROCKET) ? 1.0F : 0.0F;
                });
            }
            for (Item trident : new Item[] {FisherKingsTrident.TRIDENT.get(), CoralTrident.TRIDENT.get(), AbyssalTrident.TRIDENT.get()}) {
                ItemProperties.register(trident, ResourceLocation.withDefaultNamespace("throwing"), (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            }
            // the saber's guard model: blade held across the chest while blocking
            ItemProperties.register(DuelistsSaber.SABER.get(), ResourceLocation.fromNamespaceAndPath(Ostentum.MODID, "guarding"), (stack, level, entity, seed) ->
                entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
        });
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new VeilLayer(renderer, new VeilModel(event.getEntityModels().bakeLayer(VEIL))));
            }
        }
    }

    /** A thrown trident with its own texture, as vanilla's ThrownTridentRenderer. */
    static final class TridentRenderer extends EntityRenderer<ThrownTrident> {
        private final TridentModel model;
        private final ResourceLocation texture;

        TridentRenderer(EntityRendererProvider.Context context, ResourceLocation texture) {
            super(context);
            this.model = new TridentModel(context.bakeLayer(ModelLayers.TRIDENT));
            this.texture = texture;
        }

        @Override
        public void render(ThrownTrident entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot()) + 90.0F));
            VertexConsumer buffer = ItemRenderer.getFoilBufferDirect(buffers, model.renderType(texture), false, entity.isFoil());
            model.renderToBuffer(poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            super.render(entity, yaw, partialTicks, poseStack, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(ThrownTrident entity) {
            return texture;
        }
    }

    /** The thrown Abyssal Trident: its own model, cutout, with the emissive glow texture on top. */
    static final class AbyssalRenderer extends EntityRenderer<ThrownTrident> {
        private final AbyssalTridentModel model;

        AbyssalRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.model = new AbyssalTridentModel(context.bakeLayer(AbyssalTridentModel.LAYER));
        }

        @Override
        public void render(ThrownTrident entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot()) + 90.0F));
            VertexConsumer buffer = ItemRenderer.getFoilBufferDirect(buffers, model.renderType(AbyssalTridentModel.TEXTURE), false, entity.isFoil());
            model.renderToBuffer(poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
            model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(AbyssalTridentModel.GLOW)), light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            super.render(entity, yaw, partialTicks, poseStack, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(ThrownTrident entity) {
            return AbyssalTridentModel.TEXTURE;
        }
    }

    /** The tridents in hand: vanilla's trident model with each one's texture, flipped as vanilla's item renderer does. */
    static final class TridentItems extends BlockEntityWithoutLevelRenderer {
        private final Map<Item, ResourceLocation> textures = new IdentityHashMap<>();
        private TridentModel model;
        private AbyssalTridentModel abyssal;

        TridentItems() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override
        public void onResourceManagerReload(ResourceManager manager) {
            model = new TridentModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.TRIDENT));
            abyssal = new AbyssalTridentModel(Minecraft.getInstance().getEntityModels().bakeLayer(AbyssalTridentModel.LAYER));
        }

        private ResourceLocation texture(Item item) {
            return textures.computeIfAbsent(item, i -> Ostentum.id("textures/entity/trident/" + BuiltInRegistries.ITEM.getKey(i).getPath() + ".png"));
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            if (model == null) {
                onResourceManagerReload(Minecraft.getInstance().getResourceManager());
            }
            poseStack.pushPose();
            poseStack.scale(1.0F, -1.0F, -1.0F);
            if (stack.getItem() == AbyssalTrident.TRIDENT.get()) {
                VertexConsumer buffer = ItemRenderer.getFoilBufferDirect(buffers, abyssal.renderType(AbyssalTridentModel.TEXTURE), false, stack.hasFoil());
                abyssal.renderToBuffer(poseStack, buffer, light, overlay);
                abyssal.renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(AbyssalTridentModel.GLOW)), light, overlay);
            } else {
                VertexConsumer buffer = ItemRenderer.getFoilBufferDirect(buffers, model.renderType(texture(stack.getItem())), false, stack.hasFoil());
                model.renderToBuffer(poseStack, buffer, light, overlay);
            }
            poseStack.popPose();
        }
    }

    /** The Phantom Veil on a player: mantle, hood and a two-part swinging cape on the body, drapes on the arms. */
    static final class VeilModel extends Model {
        private final ModelPart root;
        private final ModelPart body;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        /** The cape's cloth segments, top to hem, each a child of the one before. */
        static final int SEGMENTS = 4;
        private final ModelPart[] cloth = new ModelPart[SEGMENTS];

        VeilModel(ModelPart root) {
            super(RenderType::entityTranslucent);
            this.root = root;
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

        /** Follows the player model's body and arms; the cape hangs as Fundamentum's cloth chain for this wearer. */
        void follow(PlayerModel<AbstractClientPlayer> player, AbstractClientPlayer entity, float partialTicks) {
            body.loadPose(player.body.storePose());
            rightArm.loadPose(player.rightArm.storePose());
            leftArm.loadPose(player.leftArm.storePose());
            Cloth.Pose pose = Cloth.pose(entity, "ostentum:phantom_veil", SEGMENTS, partialTicks);
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

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
            root.render(poseStack, buffer, light, overlay, color);
        }
    }

    static final class VeilLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final VeilModel model;

        VeilLayer(PlayerRenderer parent, VeilModel model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!PhantomVeil.wears(player) || player.isInvisible()) {
                return;
            }
            model.follow(getParentModel(), player, partialTicks);
            model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityTranslucent(VEIL_TEXTURE)), light, OverlayTexture.NO_OVERLAY, -1);
        }
    }
}
