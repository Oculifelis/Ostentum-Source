package com.ostentum.client;

import com.ostentum.Ostentum;
import com.ostentum.entity.Dragonling;
import com.ostentum.entity.ModEntities;
import com.ostentum.entity.SkyDolphin;
import com.ostentum.relic.Relics;
import net.minecraft.client.model.animal.dolphin.BabyDolphinModel;
import net.minecraft.client.model.animal.dolphin.DolphinModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.BatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.state.DolphinRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Renderers and model layers for Ostentum's creatures and the boss relics. */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT)
public final class OstentumClient {
    static final ModelLayerLocation DRAGONLING = new ModelLayerLocation(Ostentum.id("dragonling"), "main");

    private OstentumClient() {}

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DRAGONLING, DragonlingModel::createBodyLayer);
        event.registerLayerDefinition(RelicRenderer.BASE_1, RelicModels::base1);
        event.registerLayerDefinition(RelicRenderer.BASE_3, RelicModels::base3);
        event.registerLayerDefinition(RelicRenderer.WITHER, RelicModels::wither);
        event.registerLayerDefinition(RelicRenderer.DRAGON, RelicModels::dragon);
        event.registerLayerDefinition(RelicRenderer.ELDER, RelicModels::elder);
        event.registerLayerDefinition(RelicRenderer.WARDEN, RelicModels::warden);
    }

    /** The Blaze Whip's floating rods, moved every frame (BlazeRodsRenderer). */
    @SubscribeEvent
    public static void specialRenderers(net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent event) {
        event.register(Ostentum.id("blaze_rods"), BlazeRodsRenderer.Unbaked.MAP_CODEC);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DRAGONLING.get(), DragonlingRenderer::new);
        WhipGlow.register();
        WhipShards.register();
        EndRiftStyle.register();
        event.registerEntityRenderer(ModEntities.SKY_DOLPHIN.get(), SkyDolphinRenderer::new);
        event.registerEntityRenderer(ModEntities.BITE_BAT.get(), BatRenderer::new);
        event.registerEntityRenderer(ModEntities.GUARDIAN_TOTEM.get(), TotemRenderer::new);
        event.registerEntityRenderer(ModEntities.PUFF.get(), NoopRenderer::new);
        event.registerBlockEntityRenderer(Relics.RELIC.get(), RelicRenderer::new);
    }

    static final class DragonlingRenderer extends MobRenderer<Dragonling, LivingEntityRenderState, DragonlingModel> {
        private static final Identifier TEXTURE = Ostentum.id("textures/entity/dragonling.png");

        DragonlingRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DragonlingModel(ctx.bakeLayer(DRAGONLING)), 0.3F);
        }

        @Override
        public LivingEntityRenderState createRenderState() {
            return new LivingEntityRenderState();
        }

        @Override
        public Identifier getTextureLocation(LivingEntityRenderState state) {
            return TEXTURE;
        }
    }

    /** The vanilla baby dolphin model, pink, swimming through the air. */
    static final class SkyDolphinRenderer extends MobRenderer<SkyDolphin, DolphinRenderState, DolphinModel> {
        private static final Identifier TEXTURE = Ostentum.id("textures/entity/sky_dolphin.png");

        SkyDolphinRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new BabyDolphinModel(ctx.bakeLayer(ModelLayers.DOLPHIN_BABY)), 0.3F);
        }

        @Override
        public DolphinRenderState createRenderState() {
            return new DolphinRenderState();
        }

        @Override
        public void extractRenderState(SkyDolphin dolphin, DolphinRenderState state, float partialTicks) {
            super.extractRenderState(dolphin, state, partialTicks);
            state.isMoving = true;
        }

        @Override
        public Identifier getTextureLocation(DolphinRenderState state) {
            return TEXTURE;
        }
    }
}
