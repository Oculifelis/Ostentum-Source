package com.ostentum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ostentum.Ostentum;
import com.ostentum.entity.Dragonling;
import com.ostentum.entity.ModEntities;
import com.ostentum.entity.SkyDolphin;
import com.ostentum.relic.Relics;
import net.minecraft.client.model.DolphinModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.BatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Renderers and model layers for Ostentum's creatures and the boss relics. 1.21.1: the Blaze Whip's floating rods
 * (a 26.x special item model) are cut; its held model still draws.
 */
@EventBusSubscriber(modid = Ostentum.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
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

    /** The spent Guardian Totem's grey model, drawn by {@link TotemRenderer}. */
    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        event.register(TotemRenderer.SPENT);
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

    static final class DragonlingRenderer extends MobRenderer<Dragonling, DragonlingModel> {
        private static final ResourceLocation TEXTURE = Ostentum.id("textures/entity/dragonling.png");

        DragonlingRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DragonlingModel(ctx.bakeLayer(DRAGONLING)), 0.3F);
        }

        @Override
        public ResourceLocation getTextureLocation(Dragonling entity) {
            return TEXTURE;
        }
    }

    /** The vanilla dolphin model at baby size, pink, swimming through the air. */
    static final class SkyDolphinRenderer extends MobRenderer<SkyDolphin, DolphinModel<SkyDolphin>> {
        private static final ResourceLocation TEXTURE = Ostentum.id("textures/entity/sky_dolphin.png");

        SkyDolphinRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DolphinModel<>(ctx.bakeLayer(ModelLayers.DOLPHIN)), 0.3F);
        }

        @Override
        protected void scale(SkyDolphin dolphin, PoseStack pose, float partialTicks) {
            pose.scale(0.5F, 0.5F, 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(SkyDolphin entity) {
            return TEXTURE;
        }
    }
}
