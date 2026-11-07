package com.ostentum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ostentum.Ostentum;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The Abyssal Trident's own model: vanilla's trident cubes (same layout and 32x32 texture) plus kelp hanging from the head.
 * Drawn cutout so the thin parts can have holes, with an emissive pass from {@link #GLOW} for the veins and the glow.
 */
public final class AbyssalTridentModel extends Model {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Ostentum.id("abyssal_trident"), "main");
    public static final ResourceLocation TEXTURE = Ostentum.id("textures/entity/trident/abyssal_trident.png");
    public static final ResourceLocation GLOW = Ostentum.id("textures/entity/trident/abyssal_trident_glow.png");

    private final ModelPart root;

    AbyssalTridentModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    ModelPart root() {
        return root;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        root.render(poseStack, buffer, light, overlay, color);
    }

    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition pole = root.addOrReplaceChild("pole", CubeListBuilder.create().texOffs(0, 6).addBox(-0.5F, 2.0F, -0.5F, 1.0F, 25.0F, 1.0F), PartPose.ZERO);
        pole.addOrReplaceChild("base", CubeListBuilder.create().texOffs(4, 0).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 2.0F, 1.0F), PartPose.ZERO);
        pole.addOrReplaceChild("left_spike", CubeListBuilder.create().texOffs(4, 3).addBox(-2.5F, -3.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.ZERO);
        pole.addOrReplaceChild("middle_spike", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.ZERO);
        pole.addOrReplaceChild("right_spike", CubeListBuilder.create().texOffs(4, 3).addBox(1.5F, -3.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.ZERO);
        pole.addOrReplaceChild("kelp_x", CubeListBuilder.create().texOffs(8, 3).addBox(-1.5F, 2.0F, 0.0F, 3.0F, 8.0F, 0.0F), PartPose.ZERO);
        pole.addOrReplaceChild("kelp_z", CubeListBuilder.create().texOffs(24, 0).addBox(0.0F, 2.0F, -1.5F, 0.0F, 8.0F, 3.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }
}
